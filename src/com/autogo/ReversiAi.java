/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.util.List;

/**
 * 黑白棋 AI。
 *
 * <p>评估 = 位置权重（角最贵、角旁最坑）+ 行动力差 + 终局子数差，
 * 用 alpha-beta 搜索。三档：快（浅+随机）、普通（中）、强（深，真的会抢角、会下坑）。</p>
 */
public class ReversiAi {

    public static final int FAST = 0;
    public static final int NORMAL = 1;
    public static final int STRONG = 2;

    /** 位置权重表：角 100，角旁 -50（送角），边 10，次边 5。 */
    private static final int[][] W = {
            {100, -50, 10, 5, 5, 10, -50, 100},
            {-50, -60, -3, -3, -3, -3, -60, -50},
            {10, -3, 6, 2, 2, 6, -3, 10},
            {5, -3, 2, 1, 1, 2, -3, 5},
            {5, -3, 2, 1, 1, 2, -3, 5},
            {10, -3, 6, 2, 2, 6, -3, 10},
            {-50, -60, -3, -3, -3, -3, -60, -50},
            {100, -50, 10, 5, 5, 10, -50, 100},
    };

    private int level = NORMAL;
    private final java.util.Random rnd = new java.util.Random();

    public void setLevel(int l) {
        level = (l < FAST || l > STRONG) ? NORMAL : l;
    }

    public int level() {
        return level;
    }

    /** 返回 {row, col}；无棋可走返回 null。 */
    public int[] genmove(ReversiBoard board) {
        if (board == null || board.isGameOver()) {
            return null;
        }
        int side = board.sideToMove();
        List<int[]> moves = board.legalMoves(side);
        if (moves.isEmpty()) {
            return null;
        }
        int depth;
        if (level == FAST) {
            depth = 1;
        } else if (level == NORMAL) {
            depth = 3;
        } else {
            depth = 6;
        }
        // 终局（空格少）直接算到底：这一段是完全精确的，不靠评估
        int empties = 64 - board.count(ReversiBoard.BLACK) - board.count(ReversiBoard.WHITE);
        if (empties <= 10) {
            depth = empties;
        }

        int[] best = moves.get(0);
        double bestScore = -1e18;
        List<int[]> ties = new java.util.ArrayList<int[]>();
        for (int[] m : moves) {
            ReversiBoard child = board.copy();
            child.play(side, m[0], m[1]);
            double s = alphabeta(child, side, depth - 1, -1e18, 1e18);
            if (s > bestScore + 1e-9) {
                bestScore = s;
                best = m;
                ties.clear();
                ties.add(m);
            } else if (s > bestScore - 1e-9) {
                ties.add(m);
            }
        }
        // 快档在同分行里随机，避免每局都一样
        if (level == FAST && ties.size() > 1) {
            return ties.get(rnd.nextInt(ties.size()));
        }
        return best;
    }

    private double alphabeta(ReversiBoard b, int side, int depth, double alpha, double beta) {
        if (b.isGameOver() || depth <= 0) {
            return evaluate(b, side);
        }
        int cur = b.sideToMove();
        List<int[]> moves = b.legalMoves(cur);
        boolean maxing = (cur == side);
        if (moves.isEmpty()) {
            // 无棋可走：弃权，还是同一方继续（board 已在 play 里处理过推进）
            return evaluate(b, side);
        }
        order(b, moves);
        double best = maxing ? -1e18 : 1e18;
        for (int[] m : moves) {
            ReversiBoard child = b.copy();
            child.play(cur, m[0], m[1]);
            double v = alphabeta(child, side, depth - 1, alpha, beta);
            if (maxing) {
                if (v > best) best = v;
                if (v > alpha) alpha = v;
            } else {
                if (v < best) best = v;
                if (v < beta) beta = v;
            }
            if (beta <= alpha) {
                break;
            }
        }
        return best;
    }

    /** 着法排序：先试高分点（角/边），剪枝更狠。 */
    private void order(ReversiBoard b, List<int[]> moves) {
        double[] sc = new double[moves.size()];
        for (int i = 0; i < moves.size(); i++) {
            sc[i] = W[moves.get(i)[0]][moves.get(i)[1]];
        }
        for (int i = 1; i < moves.size(); i++) {
            int[] mm = moves.get(i);
            double ss = sc[i];
            int j = i - 1;
            while (j >= 0 && sc[j] < ss) {
                moves.set(j + 1, moves.get(j));
                sc[j + 1] = sc[j];
                j--;
            }
            moves.set(j + 1, mm);
            sc[j + 1] = ss;
        }
    }

    /** 局面评估（站在 side 的视角）。 */
    private double evaluate(ReversiBoard b, int side) {
        if (b.isGameOver()) {
            int mine = b.count(side);
            int other = b.count(ReversiBoard.opponent(side));
            if (mine > other) return 1e6 + (mine - other);
            if (mine < other) return -1e6 - (other - mine);
            return 0;
        }
        int opp = ReversiBoard.opponent(side);
        double pos = 0;
        for (int r = 0; r < ReversiBoard.SIZE; r++) {
            for (int c = 0; c < ReversiBoard.SIZE; c++) {
                int v = b.at(r, c);
                if (v == side) pos += W[r][c];
                else if (v == opp) pos -= W[r][c];
            }
        }
        // 行动力：能走的选择越多越主动
        int myMob = b.legalMoves(side).size();
        int opMob = b.legalMoves(opp).size();
        double mob = myMob - opMob;

        // 子数在后期才重要，前期别被「多子」骗了
        int empties = 64 - b.count(ReversiBoard.BLACK) - b.count(ReversiBoard.WHITE);
        double discs = b.count(side) - b.count(opp);

        return pos * 1.0 + mob * 8.0 + discs * (empties <= 16 ? 4.0 : 0.3);
    }
}
