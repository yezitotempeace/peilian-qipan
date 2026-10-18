package com.autogo;

import java.util.ArrayList;
import java.util.List;

/**
 * 五子棋复盘：把一局棋逐手过一遍，算出每一手的「该走的人还有多少胜算」、
 * 这一手亏了多少、以及电脑推荐的下一手。纯 Java、不依赖 Android，所以能上 JVM 直测。
 *
 * 与围棋复盘（GameReview）刻意不共用代码：那边是目数/贴目口径，这边是连五口径，
 * 共用只会让两边都难改。形状（wrToMove / loss / bestMove 三个等长数组）对齐，
 * 复盘页的画法可以照着来，不用重新设计数据结构。
 *
 * 胜率怎么来的：内置 AI 的评估分（见 LocalGomokuAI）是「以某个颜色为视角」的分值，
 * 越大越好；这里用
 *
 *     wr = 0.5 + 0.5 * tanh(score / K)
 *
 * 压到 0~1。K 是经验值（越小越敏感）；评估分 >= WIN/2 直接记 1.0、<= -WIN/2 记 0.0，
 * 免得「已经赢了」还被压成 0.99 之类。
 */
public final class GomokuReview {

    /** 评估分 → 胜率的温度参数。调大 = 胜率曲线更平缓。 */
    public static final double K = 30000.0;

    /** 低于这个「亏损」就不算失误（细棋波动）。 */
    public static final double BLUNDER_MIN = 0.05;

    /**
     * 下标约定（与围棋的 GameReview 对齐）：
     *   wrToMove[i] / bestMove[i] 说的是「下第 i 手之前」的局面（长度 = 手数 + 1）；
     *   loss[i] 是第 i+1 手本身的亏损（长度 = 手数，没有第 0 手）。
     */
    private GomokuReview() {
    }

    /** 一局棋的复盘结果。数组长度都是 moves.size() + 1。 */
    public static final class Review {
        public final int size;
        public final boolean forbiddenForBlack;
        public final int firstColor;
        public final List<int[]> moves;

        public final int winner;
        public final String endReason;

        /** 轮到走第 i 手时，「该走的人」的胜率。 */
        public final double[] wrToMove;
        /** 第 i+1 手实际这么走，比走最好的一手亏掉的胜率（>= 0）。 */
        public final double[] loss;
        /** 「下第 i 手之前」电脑推荐的点（字符串）；无处可下为空串。 */
        public final String[] bestMove;
        /** 同上，点的编号（-1 = 无处可下）。 */
        public final int[] bestPoint;

        private Review(int size, boolean forbiddenForBlack, int firstColor, List<int[]> moves,
                       int winner, String endReason, double[] wrToMove, double[] loss,
                       String[] bestMove, int[] bestPoint) {
            this.size = size;
            this.forbiddenForBlack = forbiddenForBlack;
            this.firstColor = firstColor;
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

        /** 从某个颜色的视角看每一手的胜率（画曲线用）。 */
        public double[] winratesFor(int color) {
            double[] out = new double[wrToMove.length];
            for (int i = 0; i < wrToMove.length; i++) {
                double w = wrToMove[i];
                if (Double.isNaN(w)) {
                    out[i] = Double.NaN;
                } else {
                    boolean toMoveIsColor = (i % 2 == 0) == (color == firstColor);
                    out[i] = toMoveIsColor ? w : 1.0 - w;
                }
            }
            return out;
        }

        /** 失误排行（亏得最多的在前）。返回的是 0 基的手序下标，最多 n 条。 */
        public List<Integer> topLosses(int n) {
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < loss.length; i++) {
                if (!Double.isNaN(loss[i])) idx.add(i);
            }
            idx.sort((a, b) -> Double.compare(loss[b], loss[a]));
            return idx.subList(0, Math.min(n, idx.size()));
        }
    }

    /** 评估分 → 胜率（0~1）。score 以「该走的一方」为视角。 */
    public static double toWinrate(double score) {
        if (Double.isNaN(score)) return Double.NaN;
        if (score >= LocalGomokuAI.WIN / 2) return 1.0;
        if (score <= -LocalGomokuAI.WIN / 2) return 0.0;
        return 0.5 + 0.5 * Math.tanh(score / K);
    }

    /**
     * 整盘逐手分析。
     *
     * 重放旧手顺一律按「自由规则」（不拿现在的禁手开关重判历史手；与 GomokuBoard.replay 同一口径），
     * 交给引擎评估 / 推荐时才切回棋局真实规则。
     *
     * @param moves             手顺（每项 {color, point}），与 GomokuBoard.moves 同格式
     * @param size              棋盘路数
     * @param forbiddenForBlack 是否开黑棋禁手
     * @param engine            评估用的引擎（内置 AI，或将来换上的强引擎）
     * @param level             评估用的难度档（复盘建议用 FAST，手机上一局几百手才跑得动）
     */
    public static Review analyze(List<int[]> moves, int size, boolean forbiddenForBlack,
                                 GomokuEngine engine, int level) {
        List<int[]> mv = new ArrayList<>();
        if (moves != null) {
            for (int[] m : moves) {
                if (m != null && m.length >= 2) mv.add(new int[]{m[0], m[1]});
            }
        }
        if (engine == null) throw new IllegalArgumentException("engine == null");

        // 重放旧手顺按自由规则：历史是当时规则下走出来的，不重判（开关开着时禁手点在落子时就被拒了）。
        GomokuBoard board = new GomokuBoard(size, false);
        int firstColor = mv.isEmpty() ? GomokuBoard.BLACK : mv.get(0)[0];

        int n = mv.size();
        double[] wr = new double[n + 1];
        double[] loss = new double[n];
        String[] bestMove = new String[n + 1];
        int[] bestPoint = new int[n + 1];

        int evalLevel = engine.level();
        engine.setLevel(level);
        try {
            for (int i = 0; i < n; i++) {
                int[] m = mv.get(i);
                int color = m[0];
                int point = m[1];

                // 「下第 i 手之前」：轮到 color 走（引擎按棋局真实规则评估 / 推荐）
                board.forbiddenForBlack = forbiddenForBlack;
                wr[i] = toWinrate(engine.evaluate(board, color));
                int rec = engine.genmove(board, color);
                bestPoint[i] = rec;
                bestMove[i] = (rec < 0) ? "" : GomokuBoard.pointToName(size, rec);

                // 落子；手顺本身必须合法（调用方给的是自己的棋谱）——旧手不重判禁手
                board.forbiddenForBlack = false;
                if (!board.isLegal(color, point)) {
                    throw new IllegalArgumentException("手顺里有非法落子：第 " + (i + 1) + " 手 "
                            + GomokuBoard.pointToName(size, point) + "（" + board.illegalReason(color, point) + "）");
                }
                board.play(color, point);

                double wrNext; // 下一个局面「该走的人」（= 对手）的胜率
                if (board.gameOver) {
                    if (board.winner == GomokuBoard.EMPTY) {
                        wrNext = 0.5;                       // 和棋
                    } else {
                        wrNext = (board.winner == color) ? 0.0 : 1.0; // 赢家是刚走的这一方 ⇒ 对手胜率 0
                    }
                    bestMove[i + 1] = "";
                    bestPoint[i + 1] = -1;
                } else {
                    board.forbiddenForBlack = forbiddenForBlack;
                    wrNext = toWinrate(engine.evaluate(board, GomokuBoard.opponent(color)));
                    int recNext = engine.genmove(board, GomokuBoard.opponent(color));
                    bestPoint[i + 1] = recNext;
                    bestMove[i + 1] = (recNext < 0) ? "" : GomokuBoard.pointToName(size, recNext);
                }
                wr[i + 1] = wrNext;
                // 走子的这一方，走完之后还有多少胜率 = 1 - 对手胜率；亏掉的 = 走之前的胜率 - 走之后
                loss[i] = Math.max(0.0, wr[i] - (1.0 - wrNext));
            }
        } finally {
            engine.setLevel(evalLevel);
        }

        return new Review(size, forbiddenForBlack, firstColor, mv, board.winner, board.endReason,
                wr, loss, bestMove, bestPoint);
    }

    /** 一句话结论。 */
    public static String verdict(Review r) {
        if (r == null || r.moveCount() == 0) return "没有可复盘的手数。";
        int winner = r.winner;
        String who = winner == GomokuBoard.BLACK ? "黑" : winner == GomokuBoard.WHITE ? "白" : "";
        StringBuilder sb = new StringBuilder();
        if (winner == GomokuBoard.EMPTY) {
            sb.append("终局：和棋");
        } else {
            sb.append("终局：").append(who).append("胜");
        }
        if (r.endReason != null && !r.endReason.isEmpty()) sb.append("（").append(r.endReason).append("）");

        // 最大的一次失误（loss[i] 说的是第 i+1 手）
        int worst = -1;
        for (int i = 0; i < r.loss.length; i++) {
            if (Double.isNaN(r.loss[i])) continue;
            if (worst < 0 || r.loss[i] > r.loss[worst]) worst = i;
        }
        if (worst < 0 || r.loss[worst] < BLUNDER_MIN) {
            sb.append("｜全程没有明显失误");
        } else {
            int[] m = r.moves.get(worst);
            String c = m[0] == GomokuBoard.BLACK ? "黑" : "白";
            sb.append("｜").append(c).append("第 ").append(worst + 1).append(" 手 ")
              .append(GomokuBoard.pointToName(r.size, m[1]))
              .append(" 亏 ").append(Math.round(r.loss[worst] * 100)).append("%");
            if (r.bestMove[worst] != null && !r.bestMove[worst].isEmpty()) {
                sb.append("（应下 ").append(r.bestMove[worst]).append("）");
            }
        }

        // 赢家从哪一手起没再落后
        if (winner != GomokuBoard.EMPTY) {
            double[] forWinner = r.winratesFor(winner);
            int from = -1;
            for (int i = forWinner.length - 1; i >= 0; i--) {
                double w = forWinner[i];
                if (Double.isNaN(w)) continue;
                if (w < 0.5) break;
                from = i;
            }
            if (from == 0) {
                sb.append("｜").append(who).append("从头领先到尾");
            } else if (from > 0 && from < r.moveCount()) {
                sb.append("｜").append(who).append("从第 ").append(from).append(" 手起再未落后");
            } else {
                sb.append("｜胜负到最后才见分晓");
            }
        }
        return sb.toString();
    }
}
