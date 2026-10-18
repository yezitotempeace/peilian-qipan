package com.autogo;

import java.util.ArrayList;
import java.util.List;

/**
 * 象棋复盘：把一盘棋逐手走一遍，记下「轮到谁走时的胜率、这一手亏了多少、引擎推荐的着法」。
 * 纯 Java（不依赖 Android），可 JVM 单测。口径与五子棋 GomokuReview 一致：
 * wrToMove / bestMove / bestPoint 长 n+1，loss 长 n。
 */
public final class XiangqiReview {

    /** 子力分换算胜率的温度：车 = 900 分，1 车优势约 0.8 胜率。 */
    public static final double K = 600.0;

    /** 亏损超过这个值算「大亏」。 */
    public static final double BLUNDER_MIN = 0.05;

    /** 复盘结果（只读）。 */
    public static final class Review {
        public final List<int[]> moves;
        public final int winner;          // RED / BLACK / 0 = 没分胜负
        public final String endReason;    // 终局说明或「对局进行中」
        public final double[] wrToMove;   // 长 n+1：走第 i 手之前、行棋方的胜率
        public final double[] loss;       // 长 n：第 i+1 手亏掉的胜率
        public final int[][] bestMove;    // 长 n+1：该手之前引擎推荐（{fr,fc,tr,tc}），无则 null
        public final int[] bestPoint;     // 长 n+1：推荐着法的目标点 row*9+col，无则 -1

        Review(List<int[]> moves, int winner, String endReason,
               double[] wrToMove, double[] loss, int[][] bestMove, int[] bestPoint) {
            this.moves = moves;
            this.winner = winner;
            this.endReason = endReason;
            this.wrToMove = wrToMove;
            this.loss = loss;
            this.bestMove = bestMove;
            this.bestPoint = bestPoint;
        }

        public int moveCount() {
            return moves.size();
        }
    }

    private XiangqiReview() {
    }

    /** 子力分 → 胜率（0..1），+WIN/2 以上按满分算。 */
    public static double toWinrate(int score) {
        if (score >= XiangqiAI.WIN / 2) {
            return 1.0;
        }
        if (score <= -XiangqiAI.WIN / 2) {
            return 0.0;
        }
        return 0.5 + 0.5 * Math.tanh(score / K);
    }

    /**
     * 复盘一盘棋。
     *
     * @param moves  手顺，每个元素 {fromRow, fromCol, toRow, toCol}
     * @param engine 用来评估与出推荐着法的引擎（会临时改难度，结束时还原）
     * @param level  复盘用难度
     */
    public static Review analyze(List<int[]> moves, XiangqiAI engine, int level) {
        return analyze(moves, new XiangqiBoard(), engine, level);
    }

    /** 同 {@link #analyze(List, XiangqiAI, int)}，但可从残局开局（给测试／残局复盘用）。 */
    public static Review analyze(List<int[]> moves, XiangqiBoard start, XiangqiAI engine, int level) {
        if (engine == null) {
            throw new IllegalArgumentException("复盘引擎为空");
        }
        List<int[]> ms = new ArrayList<int[]>();
        if (moves != null) {
            for (int[] mv : moves) {
                ms.add(mv == null ? null : mv.clone());
            }
        }
        int n = ms.size();
        double[] wr = new double[n + 1];
        double[] loss = new double[n];
        int[][] bm = new int[n + 1][];
        int[] bp = new int[n + 1];
        java.util.Arrays.fill(bp, -1);

        XiangqiBoard board = (start == null) ? new XiangqiBoard() : start.copy();
        int oldLevel = engine.level();
        engine.setLevel(level);
        try {
            wr[0] = toWinrate(engine.evaluate(board, board.sideToMove()));
            for (int i = 0; i < n; i++) {
                int[] best = engine.genmove(board);
                if (best != null) {
                    bm[i] = best.clone();
                    bp[i] = best[2] * XiangqiBoard.COLS + best[3];
                }
                int[] mv = ms.get(i);
                if (mv == null || mv.length != 4) {
                    throw new IllegalArgumentException("手顺里有残缺着法：第 " + (i + 1) + " 手");
                }
                int mover = XiangqiBoard.colorOf(board.at(mv[0], mv[1]));
                if (!board.play(mv[0], mv[1], mv[2], mv[3])) {
                    throw new IllegalArgumentException("手顺里有非法着法：第 " + (i + 1) + " 手 "
                            + XiangqiBoard.moveName(mv) + "（" + XiangqiBoard.typeName(board.at(mv[0], mv[1])) + "）");
                }
                if (board.gameOver()) {
                    wr[i + 1] = (board.winner() == mover) ? 0.0 : 1.0;
                } else {
                    wr[i + 1] = toWinrate(engine.evaluate(board, board.sideToMove()));
                }
                loss[i] = Math.max(0.0, wr[i] - (1.0 - wr[i + 1]));
            }
            int winner = board.gameOver() ? board.winner() : 0;
            String end = board.gameOver() ? board.resultString() : "对局进行中";
            return new Review(ms, winner, end, wr, loss, bm, bp);
        } finally {
            engine.setLevel(oldLevel);
        }
    }

    /** 一句话结论：终局 + 最大亏损那一手 + 走势。 */
    public static String verdict(Review r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.winner == 0 ? "局面：" : "终局：").append(r.endReason);
        int n = r.moveCount();
        int worst = -1;
        double worstLoss = 0.0;
        for (int i = 0; i < n; i++) {
            if (r.loss[i] > worstLoss) {
                worstLoss = r.loss[i];
                worst = i;
            }
        }
        if (n == 0) {
            sb.append("｜空棋谱");
            return sb.toString();
        }
        if (worst >= 0 && worstLoss >= BLUNDER_MIN) {
            sb.append("｜第 ").append(worst + 1).append(" 手 ")
                    .append(XiangqiBoard.moveText(r.moves.get(worst)))
                    .append(" 亏 ").append(Math.round(worstLoss * 100)).append("%");
            if (r.bestMove[worst] != null) {
                sb.append("（应下 ").append(XiangqiBoard.moveText(r.bestMove[worst])).append("）");
            }
        } else {
            sb.append("｜全程没有明显失误");
        }
        sb.append("｜").append(trend(r));
        return sb.toString();
    }

    private static String trend(Review r) {
        int n = r.moveCount();
        if (n == 0) {
            return "还没有可分析的着手";
        }
        // wrToMove[i] 是「轮到谁走」的视角：红先，所以偶数位是红方视角、奇数位要翻过来，
        // 否则两边轮流的口径混在一起比，任何分出胜负的棋都会被判成「局面几经反复」（第 96 轮审计 中-2）。
        boolean redAlways = true;
        boolean blackAlways = true;
        boolean close = true;
        for (int i = 0; i <= n; i++) {
            double w = (i % 2 == 0) ? r.wrToMove[i] : 1.0 - r.wrToMove[i];
            if (w < 0.55) {
                redAlways = false;
            }
            if (w > 0.45) {
                blackAlways = false;
            }
            if (Math.abs(w - 0.5) > 0.1) {
                close = false;
            }
        }
        if (redAlways) {
            return "红方从头领先到尾";
        }
        if (blackAlways) {
            return "黑方从头领先到尾";
        }
        if (close) {
            return "胜负到最后才见分晓";
        }
        return "局面几经反复";
    }
}
