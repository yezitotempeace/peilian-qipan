/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 外部中国象棋引擎 ElephantEye（象眼）的 UCCI 适配层。
 *
 * <p>ElephantEye 是纯 alpha-beta 搜索的象棋引擎（LGPL，无神经网络权重，Android 版约 500 KB），
 * 支持 UCCI 3.0 协议。它比项目内置的 {@link XiangqiAI} 强很多，还自带：
 * <ul>
 *   <li>{@code randomness} 选项：让同一局面不必每次走同一步（随机应变）；</li>
 *   <li>{@code knowledge} 选项 + BOOK.DAT 开局库：内置的开局知识（经验）。</li>
 * </ul>
 *
 * <p>坐标换算：我们的 row 0 在**上**（黑方底线），UCCI 的 rank 0 在**下**（红方底线），
 * 所以 {@code rank = ROWS - 1 - row}，{@code file = 'a' + col}。
 * 走法串形如 {@code h2e2}（起点+终点，各 1 字母 + 1 数字）。
 */
public final class XiangqiEngine implements XiangqiEngineApi {

    /** 三档每手时限（毫秒）。快档也够引擎搜到 5-6 层，远强于内置 AI 的 1 层。 */
    private static final long[] LEVEL_MS = {400, 1200, 8000};
    /** 读回应的额外宽限（进程启动 / 开局库加载都可能慢）。 */
    private static final long READ_SLACK_MS = 15000L;

    private final String bin;
    private Process proc;
    private BufferedWriter in;
    private InputStream rawOut;
    private Thread errDrain;
    private final StringBuilder errTail = new StringBuilder();
    private final java.io.ByteArrayOutputStream byteBuf = new java.io.ByteArrayOutputStream();
    private int level = 1;

    public XiangqiEngine(String bin) {
        if (bin == null || bin.trim().isEmpty()) {
            throw new IllegalArgumentException("ElephantEye 可执行文件路径不能为空");
        }
        this.bin = bin;
    }

    @Override
    public String name() {
        return "ElephantEye（外部强引擎）";
    }

    @Override
    public void setLevel(int l) {
        level = (l < 0 || l > 2) ? 1 : l;
    }

    @Override
    public int level() {
        return level;
    }

    /** 让引擎出招；无着可走返回 null。 */
    @Override
    public int[] genmove(XiangqiBoard board) {
        if (board == null || board.gameOver()) {
            return null;
        }
        ensureStarted();
        try {
            // 随机性档位：快=小、普通=中、强=极小（越强越不靠运气）
            String[] randomness = {"small", "medium", "tiny"};
            send("setoption randomness " + randomness[level]);
            send("setoption knowledge large");
            send("position " + fenOf(board));
            send("go time " + LEVEL_MS[level]);
            String mv = readBestMove();
            if (mv == null) {
                return null;
            }
            return parseMove(board, mv);
        } catch (IOException e) {
            throw new IllegalStateException("ElephantEye 通信失败：" + e.getMessage(), e);
        }
    }

    /** 结束进程（可重复调用）。 */
    public void close() {
        Process p = proc;
        if (p == null) {
            return;
        }
        try {
            send("quit");
        } catch (IOException ignored) {
            // 进程可能已经死了
        }
        try {
            p.waitFor();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        if (p.isAlive()) {
            p.destroy();
        }
        proc = null;
        in = null;
        rawOut = null;
    }

    // ---------------------------------------------------------------- 协议

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
                        // 进程结束读到 EOF 正常
                    } finally {
                        if (er != null) {
                            try { er.close(); } catch (IOException ignored) { }
                        }
                    }
                }
            }, "eleeye-stderr");
            t.setDaemon(true);
            t.start();
            errDrain = t;
            // 握手
            send("ucci");
            readUntilUcciOk();
        } catch (IOException e) {
            throw new IllegalStateException("启动 ElephantEye 失败（" + bin + "）：" + e.getMessage(), e);
        }
    }

    private void send(String s) throws IOException {
        if (in == null) {
            throw new IllegalStateException("ElephantEye 未启动");
        }
        in.write(s);
        in.write('\n');
        in.flush();
    }

    private void readUntilUcciOk() throws IOException {
        byteBuf.reset();
        long deadline = System.currentTimeMillis() + READ_SLACK_MS;
        while (System.currentTimeMillis() < deadline) {
            String line = pollLine(deadline);
            if ("ucciok".equals(line)) {
                return;
            }
        }
        killProcess();
        throw new IllegalStateException("ElephantEye 握手失败（最后输出：" + errTail.toString().trim() + "）");
    }

    /** 读到 bestmove 并返回走法串；引擎说 nobestmove 返回 null。 */
    private String readBestMove() throws IOException {
        byteBuf.reset();
        long deadline = System.currentTimeMillis() + LEVEL_MS[level] + READ_SLACK_MS;
        while (System.currentTimeMillis() < deadline) {
            String line = pollLine(deadline);
            if (line == null) {
                continue;
            }
            if (line.startsWith("bestmove")) {
                String[] parts = line.split("\\s+");
                return parts.length >= 2 ? parts[1] : null;
            }
            if ("nobestmove".equals(line)) {
                return null;
            }
        }
        killProcess();
        throw new IllegalStateException("ElephantEye 超时没有返回走法（最后输出：" + errTail.toString().trim() + "）");
    }

    /** 读一行（按字节攒，避免多字节字符卡住 Reader）。 */
    private String pollLine(long deadline) throws IOException {
        while (System.currentTimeMillis() < deadline) {
            int b = rawOut.read();
            if (b < 0) {
                return null;
            }
            if (b == '\n') {
                String s = new String(byteBuf.toByteArray(), StandardCharsets.UTF_8).trim();
                byteBuf.reset();
                return s;
            }
            if (b != '\r') {
                byteBuf.write(b);
                if (byteBuf.size() > 65536) {   // 防止异常输出撑爆内存
                    byteBuf.reset();
                }
            }
        }
        return null;
    }

    private void killProcess() {
        Process p = proc;
        if (p != null) {
            p.destroy();
            proc = null;
            in = null;
            rawOut = null;
        }
    }

    // ---------------------------------------------------------------- 坐标

    /** 我们的棋盘 → UCCI FEN（起始局面串）。 */
    public static String fenOf(XiangqiBoard board) {
        StringBuilder sb = new StringBuilder("fen ");
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            int empty = 0;
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                int p = board.at(r, c);
                if (p == 0) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    sb.append(empty);
                    empty = 0;
                }
                sb.append(fenChar(p));
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (r != XiangqiBoard.ROWS - 1) {
                sb.append('/');
            }
        }
        // UCCI FEN: 走子方 'w'(红) / 'b'(黑)；"-" 无重子限制；"-" 半回合；1 回合数
        sb.append(board.sideToMove() == XiangqiBoard.RED ? " w" : " b");
        sb.append(" - - 0 1");
        return sb.toString();
    }

    /** 我们的棋子 → FEN 字母（红大写、黑小写）。 */
    static char fenChar(int piece) {
        int t = XiangqiBoard.typeOf(piece);
        boolean red = XiangqiBoard.colorOf(piece) == XiangqiBoard.RED;
        char ch;
        switch (t) {
            case XiangqiBoard.K: ch = 'k'; break;
            case XiangqiBoard.A: ch = 'a'; break;
            case XiangqiBoard.B: ch = 'b'; break;
            case XiangqiBoard.N: ch = 'n'; break;
            case XiangqiBoard.R: ch = 'r'; break;
            case XiangqiBoard.C: ch = 'c'; break;
            case XiangqiBoard.P: ch = 'p'; break;
            default: ch = '?'; break;
        }
        return red ? Character.toUpperCase(ch) : ch;
    }

    /** UCCI 走法串（如 h2e2）→ 我们的 {fromRow, fromCol, toRow, toCol}。 */
    public static int[] parseMove(XiangqiBoard board, String mv) {
        if (mv == null || mv.length() < 4) {
            throw new IllegalStateException("ElephantEye 回了无法解析的走法：" + mv);
        }
        int fc = mv.charAt(0) - 'a';
        int fr = mv.charAt(1) - '0';
        int tc = mv.charAt(2) - 'a';
        int tr = mv.charAt(3) - '0';
        if (fc < 0 || fc >= XiangqiBoard.COLS || tc < 0 || tc >= XiangqiBoard.COLS
                || fr < 0 || fr >= XiangqiBoard.ROWS || tr < 0 || tr >= XiangqiBoard.ROWS) {
            throw new IllegalStateException("ElephantEye 回了越界走法：" + mv);
        }
        // rank 0 在红方底线（我们的 row 9）
        int fromRow = XiangqiBoard.ROWS - 1 - fr;
        int toRow = XiangqiBoard.ROWS - 1 - tr;
        return new int[]{fromRow, fc, toRow, tc};
    }
}
