/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

/**
 * 内置五子棋 AI：棋型启发式 + 迭代加深 α-β 搜索，纯 Java、无随机数
 * （同一个局面同一难度永远走同一手，便于测试与复现）。
 *
 * <p>它实现 {@link GomokuEngine}，将来接外部强引擎时换实现即可，界面与复盘侧不用改。
 *
 * <p>搜索只在自己的 scratch 棋局（{@code int[]} 拷贝）里落子，**不改变传入的 GomokuBoard**。
 * 黑棋禁手开着时，候选点一律先过 {@link GomokuBoard#isForbiddenOn} 才会被考虑。
 */
public class LocalGomokuAI implements GomokuEngine {

    public static final int FAST = 0;
    public static final int NORMAL = 1;
    public static final int STRONG = 2;

    /** 难度档：{搜索深度, 候选宽度, 节点预算}。 */
    private static final int[][] LEVELS = {
        {2, 8, 20000},
        {4, 10, 120000},
        {4, 14, 400000},
    };

    /** 「已成五」的量级：比任何棋型都大，配合 depth 用来偏好更快的胜 / 更慢的败。 */
    public static final double WIN = 1e9;

    private static final int[][] DIRS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    private int level = NORMAL;
    private int width = LEVELS[NORMAL][1];
    private long nodes;
    private long budget;
    private boolean budgetHit;

    @Override
    public String name() {
        return "内置引擎";
    }

    @Override
    public void setLevel(int level) {
        this.level = (level >= 0 && level < LEVELS.length) ? level : NORMAL;
    }

    @Override
    public int level() {
        return level;
    }

    // ---------- 对外评估 ----------

    @Override
    public double evaluate(GomokuBoard board, int color) {
        if (color != GomokuBoard.BLACK && color != GomokuBoard.WHITE) return 0;
        if (board.gameOver) {
            if (board.winner == GomokuBoard.EMPTY) return 0;
            return board.winner == color ? WIN : -WIN;
        }
        return staticEval(board.grid, board.size, color);
    }

    // ---------- 走子 ----------

    @Override
    public int genmove(GomokuBoard board, int color) {
        if (color != GomokuBoard.BLACK && color != GomokuBoard.WHITE) return -1;
        if (board.gameOver || board.isFull()) return -1;
        int size = board.size;
        int[] g = board.grid.clone();
        boolean forbid = board.forbiddenForBlack && color == GomokuBoard.BLACK;
        int opp = GomokuBoard.opponent(color);

        // 空盘：天元。（不看手顺：局面可能是直接摆在 grid 上的。）
        if (!hasStone(g)) return board.idx(size / 2, size / 2);

        // 1) 能成五就成五；2) 否则堵对方下一手成五。
        int win = findFive(g, size, color, forbid);
        if (win >= 0) return win;
        int block = findFive(g, size, opp, false);
        if (block >= 0) return block;

        // 3) 迭代加深搜索：每层都保留「已经找到的最好一手」，超预算也能给答案。
        int maxDepth = LEVELS[level][0];
        width = LEVELS[level][1];
        budget = LEVELS[level][2];
        nodes = 0;
        budgetHit = false;
        int best = -1;
        double bestScore = -Double.MAX_VALUE;
        for (int depth = 1; depth <= maxDepth; depth++) {
            double[] res = searchRoot(g, size, color, opp, depth, forbid);
            if (res[0] >= 0) {
                best = (int) res[0];
                bestScore = res[1];
            }
            if (budgetHit || bestScore >= WIN / 2) break;
        }
        if (best >= 0 && isPairable(g, size, color, best, forbid)) return best;
        // 兜底：第一个合法点（理论上到不了；禁手开到极端时才可能）。
        int[] all = empties(g, size);
        for (int p : all) {
            if (isPairable(g, size, color, p, forbid)) return p;
        }
        return -1;
    }

    /**
     * 静态评估排前 n 的候选着法（不搜索），给「经验库」这类外挂做候选过滤用。
     * 必成五 / 必堵五这类硬着法独占返回；其余按「走完之后的盘面」从好到坏排序。
     */
    public int[] topMoves(GomokuBoard board, int color, int n) {
        if (color != GomokuBoard.BLACK && color != GomokuBoard.WHITE) return new int[0];
        if (board.gameOver || board.isFull()) return new int[0];
        int size = board.size;
        int[] g = board.grid.clone();
        boolean forbid = board.forbiddenForBlack && color == GomokuBoard.BLACK;
        int opp = GomokuBoard.opponent(color);
        if (!hasStone(g)) return new int[]{board.idx(size / 2, size / 2)};
        int win = findFive(g, size, color, forbid);
        if (win >= 0) return new int[]{win};
        int block = findFive(g, size, opp, false);
        if (block >= 0) return new int[]{block};
        int[] cand = candidates(g, size, color, forbid);
        if (cand.length == 0) return new int[0];
        double[] sc = new double[cand.length];
        for (int i = 0; i < cand.length; i++) {
            int[] child = newWith(g, size, cand[i], color);
            sc[i] = -staticEval(child, size, opp);
        }
        int want = Math.max(0, Math.min(n, cand.length));
        int[] out = new int[want];
        boolean[] used = new boolean[cand.length];
        for (int k = 0; k < want; k++) {
            int bi = -1;
            double bs = -Double.MAX_VALUE;
            for (int i = 0; i < cand.length; i++) {
                if (!used[i] && sc[i] > bs) {
                    bs = sc[i];
                    bi = i;
                }
            }
            used[bi] = true;
            out[k] = cand[bi];
        }
        return out;
    }

    private static boolean hasStone(int[] g) {
        for (int v : g) {
            if (v != GomokuBoard.EMPTY) return true;
        }
        return false;
    }

    /** 找「color 落在 (r,c) 就成五」的点；forbid 时先过禁手。返回点号，没有返回 -1。 */
    private static int findFive(int[] g, int size, int color, boolean forbid) {
        for (int p = 0; p < g.length; p++) {
            if (g[p] != GomokuBoard.EMPTY) continue;
            int r = p / size, c = p % size;
            if (forbid && GomokuBoard.isForbiddenOn(newWith(g, size, p, color), size, r, c)) continue;
            int[] probe = g.clone();
            probe[p] = color;
            if (GomokuBoard.runLengthOn(probe, size, color, r, c) >= 5) return p;
        }
        return -1;
    }

    private static int[] newWith(int[] g, int size, int point, int color) {
        int[] copy = g.clone();
        copy[point] = color;
        return copy;
    }

    /** 根节点：按启发式排序试每一手，返回 {点号, 分}；一个候选都没有时点号为 -1。 */
    private double[] searchRoot(int[] g, int size, int mover, int opp, int depth, boolean forbid) {
        int[] cand = candidates(g, size, mover, forbid);
        int bestPoint = -1;
        double best = -Double.MAX_VALUE;
        double alpha = -Double.MAX_VALUE;
        for (int p : cand) {
            int r = p / size, c = p % size;
            g[p] = mover;
            nodes++;
            double sc;
            if (GomokuBoard.runLengthOn(g, size, mover, r, c) >= 5) {
                sc = WIN + depth;
            } else {
                sc = -negamax(g, size, opp, depth - 1, -Double.MAX_VALUE, -alpha, forbid);
            }
            g[p] = GomokuBoard.EMPTY;
            if (sc > best) {
                best = sc;
                bestPoint = p;
            }
            if (best > alpha) alpha = best;
            if (budgetHit) break;
        }
        return new double[]{bestPoint, best};
    }

    private double negamax(int[] g, int size, int mover, int depth,
                           double alpha, double beta, boolean forbid) {
        if (nodes >= budget) {
            budgetHit = true;
            return staticEval(g, size, mover);
        }
        if (depth <= 0) return staticEval(g, size, mover);
        int[] cand = candidates(g, size, mover, forbid);
        if (cand.length == 0) return 0;
        int opp = GomokuBoard.opponent(mover);
        double best = -Double.MAX_VALUE;
        for (int p : cand) {
            int r = p / size, c = p % size;
            g[p] = mover;
            nodes++;
            double sc;
            if (GomokuBoard.runLengthOn(g, size, mover, r, c) >= 5) {
                sc = WIN + depth;
            } else {
                sc = -negamax(g, size, opp, depth - 1, -beta, -alpha, forbid);
            }
            g[p] = GomokuBoard.EMPTY;
            if (sc > best) best = sc;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break;
            if (budgetHit) break;
        }
        return best;
    }

    // ---------- 候选点 ----------

    /** 已有子周围 2 格内的空点，按「我造威胁 + 对方造威胁」排序后截到候选宽度。 */
    private int[] candidates(int[] g, int size, int color, boolean forbid) {
        boolean[] near = new boolean[g.length];
        boolean any = false;
        for (int p = 0; p < g.length; p++) {
            if (g[p] == GomokuBoard.EMPTY) continue;
            any = true;
            int r = p / size, c = p % size;
            for (int dr = -2; dr <= 2; dr++) {
                for (int dc = -2; dc <= 2; dc++) {
                    int rr = r + dr, cc = c + dc;
                    if (GomokuBoard.inBounds(size, rr, cc)) near[rr * size + cc] = true;
                }
            }
        }
        if (!any) return new int[]{0};
        int opp = GomokuBoard.opponent(color);
        int[] pts = new int[g.length];
        double[] score = new double[g.length];
        int n = 0;
        for (int p = 0; p < g.length; p++) {
            if (g[p] != GomokuBoard.EMPTY || !near[p]) continue;
            int r = p / size, c = p % size;
            if (forbid && GomokuBoard.isForbiddenOn(newWith(g, size, p, color), size, r, c)) continue;
            double mine = threat(g, size, color, r, c);
            double theirs = threat(g, size, opp, r, c);
            pts[n] = p;
            score[n] = Math.max(mine, theirs * 1.05) + (mine + theirs) * 0.1;
            n++;
        }
        if (n == 0) return empties(g, size);
        // 选择排序：截前 width 个（棋盘最大 225，规模小，直接选排够了）。
        int keep = Math.min(n, Math.max(1, width));
        for (int i = 0; i < keep; i++) {
            int bestJ = i;
            for (int j = i + 1; j < n; j++) {
                if (score[j] > score[bestJ]) bestJ = j;
            }
            double ts = score[i]; score[i] = score[bestJ]; score[bestJ] = ts;
            int tp = pts[i]; pts[i] = pts[bestJ]; pts[bestJ] = tp;
        }
        int[] out = new int[keep];
        System.arraycopy(pts, 0, out, 0, keep);
        return out;
    }

    private static int[] empties(int[] g, int size) {
        int[] tmp = new int[g.length];
        int n = 0;
        for (int p = 0; p < g.length; p++) {
            if (g[p] == GomokuBoard.EMPTY) tmp[n++] = p;
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }

    /** 假设 color 落在 (r,c)：单方向的棋型价值，取最大方向（只用来排序，不含禁手判定）。 */
    static double threat(int[] g, int size, int color, int r, int c) {
        double best = 0;
        for (int[] d : DIRS) {
            int[] span = GomokuBoard.runSpan(g, size, r, c, color, d[0], d[1]);
            int run = span[2];
            if (run >= 5) return WIN;
            int open = openEnds(g, size, span, d, run);
            double v;
            if (run == 4) v = (open == 2) ? 1e6 : (open == 1) ? 1e5 : 0;
            else if (run == 3) v = (open == 2) ? 1e4 : (open == 1) ? 1e3 : 0;
            else if (run == 2) v = (open == 2) ? 100 : (open == 1) ? 10 : 0;
            else v = (open == 2) ? 1 : 0;
            if (v > best) best = v;
        }
        return best;
    }

    /** 这一段两端还有几个空点（0 / 1 / 2）。 */
    private static int openEnds(int[] g, int size, int[] span, int[] d, int run) {
        int open = 0;
        int r1 = span[0] - d[0], c1 = span[1] - d[1];
        if (GomokuBoard.inBounds(size, r1, c1) && g[r1 * size + c1] == GomokuBoard.EMPTY) open++;
        int r2 = span[0] + run * d[0], c2 = span[1] + run * d[1];
        if (GomokuBoard.inBounds(size, r2, c2) && g[r2 * size + c2] == GomokuBoard.EMPTY) open++;
        return open;
    }

    // ---------- 静态评估 ----------

    /** 以 color 为视角的盘面静态分（两边互为相反数）。 */
    static double staticEval(int[] g, int size, int color) {
        return stonesValue(g, size, color) - stonesValue(g, size, GomokuBoard.opponent(color));
    }

    /** 某色所有连段的价值之和；每个连段只算一次（只看它是「起点」的那个方向）。 */
    static double stonesValue(int[] g, int size, int color) {
        double total = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (g[r * size + c] != color) continue;
                for (int[] d : DIRS) {
                    int pr = r - d[0], pc = c - d[1];
                    if (GomokuBoard.inBounds(size, pr, pc) && g[pr * size + pc] == color) continue;
                    int[] span = GomokuBoard.runSpan(g, size, r, c, color, d[0], d[1]);
                    int run = span[2];
                    int open = openEnds(g, size, span, d, run);
                    total += runValue(run, open);
                }
            }
        }
        return total;
    }

    private static double runValue(int run, int open) {
        if (run >= 5) return WIN;
        if (run == 4) return (open == 2) ? 1e6 : (open == 1) ? 1e5 : 0;
        if (run == 3) return (open == 2) ? 1e4 : (open == 1) ? 1e3 : 0;
        if (run == 2) return (open == 2) ? 100 : (open == 1) ? 10 : 0;
        return (open == 2) ? 1 : 0;
    }

    /** 这一手合不合法（空点 + 禁手）。 */
    private static boolean isPairable(int[] g, int size, int color, int point, boolean forbid) {
        if (point < 0 || point >= g.length || g[point] != GomokuBoard.EMPTY) return false;
        if (!forbid) return true;
        int r = point / size, c = point % size;
        return !GomokuBoard.isForbiddenOn(newWith(g, size, point, color), size, r, c);
    }
}
