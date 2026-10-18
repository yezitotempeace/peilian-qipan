package com.autogo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 外部强引擎 Rapfi（Gomocup 协议）的适配层 —— 走 {@link GomokuEngine} 接口挂上来，
 * 界面与复盘侧一行不用改。
 *
 * <p>用途：五子棋对局「强」档的电脑对手。复盘（胜率 / 推荐点）仍然走内置 {@link LocalGomokuAI}：
 * Gomocup 协议没有「评估局面」的命令，硬造出来的数不可信 ⇒ 本类的 {@link #evaluate} 直接委托内置 AI。
 *
 * <p>与引擎的对话（2026-10-15 对 pbrain-rapfi 实测）：
 * <pre>
 *   START &lt;size&gt;              引擎回 OK
 *   INFO TIMEOUT_TURN &lt;ms&gt;    设置每手时限
 *   BOARD                     换行跟每颗子 &lt;x&gt;,&lt;y&gt;,&lt;1|2&gt;，最后一行 DONE
 *                             引擎回一手 &lt;x&gt;,&lt;y&gt;（0 基坐标；y 从最下面一行往上数）
 *   END                       引擎退出
 * </pre>
 * 引擎在思考前后会往标准输出打 {@code MESSAGE ...} 信息行，解析时必须跳过。
 *
 * <p>颜色约定（Rapfi 源码 {@code command/gomocup.cpp} 的 getPosition）：引擎用 **BOARD 的第一条记录**
 * 的 side 标记判断自己是黑还是白 —— 标 1 表示「引擎自己的子」、标 2 表示对方的子。
 * 所以本方要走时，把 {@code color} 那一方的子标 1、另一方标 2，引擎就认为自己执该色；
 * 因为五子棋黑先，第一手一定是黑，白方要走（历史首手是黑）时首手自然标 2 ⇒ 引擎执白。
 * <b>行序不能乱</b>：颠倒 BOARD 行的顺序会让引擎认错颜色（第 46 轮审计用真引擎实测过），
 * 所以行必须按落子时间从早到晚发（{@code RapfiGomokuTest} 里有一条专门钉住行序的用例）。
 *
 * <p>超时是硬超时：读取只消费「已经就绪」的**字节**，半行（包括只写了一半的多字节字符）也绝不在上面阻塞；
 * 到点就结束引擎进程并报错，下一次 {@link #genmove} 会自动重起一个进程。
 */
public final class RapfiGomokuEngine implements GomokuEngine {

    /** 档位（0 快 / 1 普通 / 2 强）与各自的每手时限（毫秒）。 */
    private static final int[] LEVEL_MS = {400, 1500, 4000};
    /** 读回应的额外宽限：引擎自报时限之外，进程调度 / 开局库加载都可能多花时间。 */
    private static final long READ_SLACK_MS = 15000L;

    private final String bin;
    /** 复盘用的评估（见类注释）：委托内置 AI，避免把「没有评估能力」伪装成有。 */
    private final LocalGomokuAI evalFallback = new LocalGomokuAI();

    private Process proc;
    private BufferedWriter in;
    /** 引擎标准输出的**字节**流（不套 Reader：见 {@link #pollLine} —— 半个多字节字符会把 Reader 卡住）。 */
    private InputStream rawOut;
    private Thread errDrain;
    private final StringBuilder errTail = new StringBuilder();
    /** 半行的暂存（见 {@link #pollLine}）：攒的是字节，所以「只写了一半的多字节字符」也不会卡住读取。 */
    private final ByteArrayOutputStream byteBuf = new ByteArrayOutputStream();
    private int level = 1;

    public RapfiGomokuEngine(String bin) {
        if (bin == null || bin.trim().isEmpty()) {
            throw new IllegalArgumentException("Rapfi 可执行文件的路径不能为空");
        }
        this.bin = bin;
    }

    @Override
    public String name() {
        return "Rapfi（外部强引擎）";
    }

    @Override
    public void setLevel(int l) {
        level = (l < 0 || l > 2) ? 1 : l;
    }

    @Override
    public int level() {
        return level;
    }

    /** 候选排名用内置 AI 算（Rapfi 协议没有「给前几名」的命令）。 */
    @Override
    public int[] topMoves(GomokuBoard board, int color, int n) {
        return evalFallback.topMoves(board, color, n);
    }

    @Override
    public double evaluate(GomokuBoard board, int color) {
        return evalFallback.evaluate(board, color);
    }

    @Override
    public int genmove(GomokuBoard board, int color) {
        if (board.gameOver || board.isFull()) {
            return -1;
        }
        // 路数自检（第 51 轮审计 中-1 / 第 52 轮审计 提示 1）：Rapfi 是 15×15 的引擎，
        // 非 15 路时它只看得见 225 个点，棋盘上其余的子它根本看不见 —— 与其喂残缺输入，
        // 不如直接拒绝，由调用方（GomokuActivity.engineForMove 的门禁 + startAiTurn 的 catch）退回内置 AI。
        // 注：已经终局 / 下满的盘在上面就返回 -1 了，不会走到这里（所以「满盘 ⇒ -1」的旧用例不受影响）。
        if (board.size != 15) {
            throw new IllegalStateException("Rapfi 只支持 15 路（收到 " + board.size + " 路）");
        }
        ensureStarted();
        try {
            send("START " + board.size);
            readUntilOk();
            send("INFO TIMEOUT_TURN " + LEVEL_MS[level]);
            StringBuilder sb = new StringBuilder("BOARD");
            for (int i = 0; i < board.moves.size(); i++) {
                int[] mv = board.moves.get(i);
                int[] xy = toEngine(board.size, mv[1]);
                sb.append('\n').append(xy[0]).append(',').append(xy[1]).append(',').append(mv[0] == color ? 1 : 2);
            }
            sb.append("\nDONE");
            send(sb.toString());
            int[] xy = readMove(board.size);
            if (xy == null) {
                return -1;
            }
            return board.idx(board.size - 1 - xy[1], xy[0]);
        } catch (IOException e) {
            throw new IllegalStateException("Rapfi 通信失败：" + e.getMessage(), e);
        }
    }

    /**
     * 发 END 并结束进程；可重复调用。
     * 切断与 {@link #killProcess} 走同一套收尾（SIGTERM → 等 500 ms → SIGKILL）：
     * 外部引擎完全可能吞掉 SIGTERM，只 destroy() 会留下吃 CPU 的孤儿进程（第 48 轮审计实测）。
     */
    public void close() {
        Process p = proc;
        if (p == null) {
            return;
        }
        try {
            // 管道里还压着没读走的输出时（引擎狂发日志且没人读）就别写 END —— 4 字节也可能卡在写满的管道上没人收，
            // 那样 close() 会跟着卡住（第 49 轮审计提的边界）；这种时候直接走收尾。
            if (p.isAlive() && rawOut != null && rawOut.available() == 0) {
                p.getOutputStream().write("END\n".getBytes(StandardCharsets.UTF_8));
                p.getOutputStream().flush();
            }
        } catch (IOException ignored) {
            // 引擎已经死了就算了
        }
        terminateProcess();
        errDrain = null;
    }

    /** 项目坐标（0 = 左上角）→ 引擎坐标（0 = 左下角）：x 同列，y 上下翻转。 */
    public static int[] toEngine(int size, int point) {
        return new int[]{point % size, size - 1 - point / size};
    }

    /** 引擎坐标 → 项目点号。 */
    public static int toPoint(int size, int x, int y) {
        return (size - 1 - y) * size + x;
    }

    // ---------------------------------------------------------------- 内部

    private void ensureStarted() {
        if (proc != null && proc.isAlive() && in != null && rawOut != null) {
            return;
        }
        try {
            final Process p = new ProcessBuilder(bin).start();
            proc = p;
            in = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));
            rawOut = p.getInputStream();
            byteBuf.reset();
            errTail.setLength(0);
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    BufferedReader er = null;
                    try {
                        er = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8));
                        String line;
                        while ((line = er.readLine()) != null) {
                            if (errTail.length() < 2000) {
                                errTail.append(line).append('\n');
                            }
                        }
                    } catch (IOException ignored) {
                        // 进程结束时读到 EOF 很正常
                    } finally {
                        if (er != null) {
                            try { er.close(); } catch (IOException ignored) { }
                        }
                    }
                }
            }, "rapfi-stderr");
            t.setDaemon(true);
            t.start();
            errDrain = t;
        } catch (IOException e) {
            throw new IllegalStateException("启动 Rapfi 失败（" + bin + "）：" + e.getMessage(), e);
        }
    }

    private void send(String s) throws IOException {
        if (in == null) {
            throw new IllegalStateException("Rapfi 未启动");
        }
        in.write(s);
        in.write('\n');
        in.flush();
    }

    private void readUntilOk() throws IOException {
        byteBuf.reset();
        long deadline = System.currentTimeMillis() + READ_SLACK_MS;
        while (System.currentTimeMillis() < deadline) {
            String line = pollLine(deadline);
            if ("OK".equals(line)) {
                return;
            }
        }
        killProcess();
        throw new IllegalStateException("Rapfi 没有回应 START（最后输出：" + errTail.toString().trim() + "；已结束进程）");
    }

    /** 读到一行坐标；引擎说 PASS 就返回 null。 */
    private int[] readMove(int size) throws IOException {
        byteBuf.reset();
        long deadline = System.currentTimeMillis() + LEVEL_MS[level] + READ_SLACK_MS;
        while (System.currentTimeMillis() < deadline) {
            String line = pollLine(deadline);
            if (line == null) {
                continue;
            }
            if ("PASS".equalsIgnoreCase(line)) {
                return null;
            }
            int comma = line.indexOf(',');
            if (comma <= 0) {
                continue; // MESSAGE ... 之类的信息行
            }
            int x;
            int y;
            try {
                x = Integer.parseInt(line.substring(0, comma).trim());
                y = Integer.parseInt(line.substring(comma + 1).trim());
            } catch (NumberFormatException nfe) {
                continue; // 不是坐标
            }
            if (x < 0 || y < 0 || x >= size || y >= size) {
                killProcess();
                throw new IllegalStateException("Rapfi 回了越界的坐标：" + line + "（棋盘 " + size + " 路；已结束进程）");
            }
            return new int[]{x, y};
        }
        killProcess();
        throw new IllegalStateException("Rapfi 超时（" + (LEVEL_MS[level] + READ_SLACK_MS) + " ms 内没有回手；已结束进程）");
    }

    /**
     * 超时 / 输出坏掉时结束引擎进程：先把「还活着」的引用清掉（否则 {@link #ensureStarted} 会误认为进程还能用，
     * 下一次写进去直接 Stream Closed），再等它真的退出（最多 500 ms），不行就强杀。
     * 下一次 {@link #genmove} 会重起一个新的。
     */
    private void killProcess() {
        terminateProcess();
    }

    /**
     * 结束当前进程：先清引用（下一次 {@link #genmove} 会重起一个新的），再 SIGTERM，等最多 500 ms，
     * 还活着就补一刀 SIGKILL —— 忽略 TERM 的引擎（假引擎、卡死的子进程）只能靠这一刀收掉。
     * `close()` 与 `killProcess()` 都走这里，避免两条路越走越不一样。
     */
    private void terminateProcess() {
        Process p = proc;
        proc = null;
        in = null;
        rawOut = null;
        if (p == null) {
            return;
        }
        p.destroy();
        try {
            if (!p.waitFor(500, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
    }

    /**
     * 读一行（读到换行才算一行）。只在 {@code rawOut.available()} 为真时消费**一个字节**，
     * <b>绝不在半行上阻塞 —— 包括只写了一半的多字节字符</b>（那种情况套 {@code InputStreamReader}
     * 会在解码器里等剩下的字节，第 47 轮审计抓到过这个缝）：半行会攒进 {@link #byteBuf}，
     * 下一次调用接着攒 ⇒ deadline 到点一定返回 null（硬超时）。
     */
    private String pollLine(long deadline) throws IOException {
        while (System.currentTimeMillis() < deadline) {
            if (rawOut.available() > 0) {
                int b = rawOut.read();
                if (b < 0) {
                    throw new IllegalStateException("Rapfi 进程已退出（最后输出：" + errTail.toString().trim() + "）");
                }
                if (b == '\n') {
                    String s = new String(byteBuf.toByteArray(), StandardCharsets.UTF_8).trim();
                    byteBuf.reset();
                    return s;
                }
                if (byteBuf.size() > 65536) {
                    killProcess();
                    throw new IllegalStateException("Rapfi 输出的单行超过 64 KB，已结束进程");
                }
                byteBuf.write(b);
                continue;
            }
            if (proc == null || !proc.isAlive()) {
                throw new IllegalStateException("Rapfi 进程已退出（最后输出：" + errTail.toString().trim() + "）");
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 Rapfi 时被中断", ie);
            }
        }
        return null;
    }
}
