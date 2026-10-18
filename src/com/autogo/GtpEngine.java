package com.autogo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * GTP (Go Text Protocol) 客户端：把 KataGo 当子进程跑，走 stdin/stdout 通信。
 *
 * 为什么用子进程而不是 JNI：GTP 是稳定的文本协议，子进程崩溃不会带走 App 进程，
 * 而且和桌面版的 autogo 驱动层共用同一套语义。代价是要保证二进制在磁盘上可执行 ——
 * 靠 AndroidManifest 的 extractNativeLibs="true" 把 jniLibs 里的 libkatago.so
 * 释放到 nativeLibraryDir（那个目录的文件有执行权限）。
 */
public class GtpEngine implements Closeable {

    public static class GtpError extends IOException {
        public GtpError(String msg) {
            super(msg);
        }
    }

    public static class GtpTimeout extends IOException {
        public GtpTimeout(String msg) {
            super(msg);
        }
    }

    private final Process process;
    private final BufferedWriter stdin;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private final Thread reader;
    private volatile String stderrTail = "";
    private volatile boolean closed = false;
    /** 超时之后响应流就错位了，继续发命令只会读到上一条的残留。 */
    private volatile boolean poisoned = false;

    /**
     * 超时处理：引擎还在搜索，它的响应稍后才会到达，会污染下一次读取。
     * 此时无法可靠地「跳过」那段输出，所以标记引擎不可用，让调用方重启。
     */
    private GtpTimeout timeout(String msg) {
        poisoned = true;
        return new GtpTimeout(msg);
    }

    public GtpEngine(String exe, List<String> args, File logFile) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(exe);
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        if (logFile != null) {
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));
        }
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);
        process = pb.start();

        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    lines.offer(line);
                }
            } catch (IOException ignored) {
                // 进程结束时流会关闭，属正常
            } finally {
                lines.offer("\u0000EOF");
            }
        }, "gtp-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** 进程是否还活着。 */
    public boolean isAlive() {
        return !closed && process.isAlive();
    }

    /**
     * 发送一条 GTP 命令并读取完整响应。
     *
     * GTP 的响应以 '=' 或 '?' 开头，以空行结束；多行响应只有首行带前缀。
     * 引擎在首条响应前可能吐出 banner/日志行，这些一律跳过。
     */
    public String send(String command, long timeoutMs) throws IOException {
        if (closed) throw new GtpError("引擎已关闭");
        if (poisoned) {
            throw new GtpError("引擎状态异常：上一次请求超时后响应流已经错位，请重启引擎");
        }
        try {
            stdin.write(command);
            stdin.write("\n");
            stdin.flush();
        } catch (IOException e) {
            throw new GtpError("向引擎写入失败（进程可能已退出）: " + e.getMessage());
        }

        StringBuilder sb = new StringBuilder();
        boolean started = false;
        boolean isError = false;
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;

        while (true) {
            long remainMs = (deadline - System.nanoTime()) / 1_000_000L;
            if (remainMs <= 0) throw timeout("引擎响应超时（" + (timeoutMs / 1000) + "s）：" + command);
            String line;
            try {
                line = lines.poll(remainMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GtpError("等待引擎响应时被中断");
            }
            if (line == null) throw timeout("引擎响应超时：" + command);
            if (line.equals("\u0000EOF")) throw new GtpError("引擎进程已退出。" + stderrHint());

            if (!started) {
                if (line.isEmpty()) continue;
                char c = line.charAt(0);
                if (c == '=' || c == '?') {
                    started = true;
                    isError = (c == '?');
                    String rest = line.substring(1).trim();
                    if (!rest.isEmpty()) sb.append(rest);
                }
                // 非 '=' / '?' 开头的行是引擎杂音，丢弃
                continue;
            }
            if (line.isEmpty()) break; // 空行 = 响应结束
            if (sb.length() > 0) sb.append('\n');
            sb.append(line.trim());
        }

        if (isError) throw new GtpError(sb.toString());
        return sb.toString();
    }

    private String stderrHint() {
        return stderrTail.isEmpty() ? "" : "（stderr: " + stderrTail + "）";
    }

    /** 便捷方法：棋盘尺寸。 */
    public void boardsize(int size) throws IOException {
        send("boardsize " + size, 30_000);
    }

    public void clearBoard() throws IOException {
        send("clear_board", 30_000);
    }

    public void komi(double komi) throws IOException {
        send("komi " + komi, 30_000);
    }

    public void play(int color, String vertex) throws IOException {
        send("play " + Board.colorToChar(color) + " " + vertex, 60_000);
    }

    public void undo() throws IOException {
        send("undo", 30_000);
    }

    /**
     * 载入一份 SGF 作为当前局面。
     *
     * 为什么需要它：GTP 没有「通用地在某点摆一个白子」的命令，`set_free_handicap`
     * 只能摆黑子。而我们的棋谱可以带任意 AB/AW 混合摆子，只重放 moves 会让引擎看到
     * **另一个局面**（实测 `AB[cc][gg];W[ee]`：旧路径引擎只看到 1 子）。`loadsgf` 是
     * 唯一能精确还原的路子 —— 实测 KataGo v1.18.2 支持，载入后 `showboard` 与我们的
     * 盘面逐点一致。
     */
    public void loadsgf(String path) throws IOException {
        String q = path.indexOf(' ') >= 0 ? "\"" + path + "\"" : path;
        send("loadsgf " + q, 60_000);
    }

    /** 让引擎落子，返回 GTP 坐标（可能是 "pass" / "resign"）。 */
    public String genmove(int color, long timeoutMs) throws IOException {
        return send("genmove " + Board.colorToChar(color), timeoutMs).trim();
    }

    public String finalScore(long timeoutMs) throws IOException {
        return send("final_score", timeoutMs).trim();
    }

    @Override
    public void close() {
        // 说明：closed 的检查与赋值不是原子的，两个线程同时进来会各发一次 quit、各 waitFor 一次。
        // 后果无害（第二次写已关闭的流抛 IOException，被下面 catch 吞掉；waitFor/destroy 幂等），
        // 但 v60 起确实会有并发关（worker 的 finally 与 Activity 的 closeAsync），所以在这里记一笔。
        if (closed) return;
        closed = true;
        try {
            stdin.write("quit\n");
            stdin.flush();
        } catch (IOException ignored) {
            // 进程可能已经没了
        }
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroy();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroy();
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
        }
    }
}
