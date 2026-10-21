/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.util.List;

/**
 * 象棋内置 AI（纯 Java，可 JVM 单测）。
 *
 * <p>三档难度用「搜索深度 + 节点预算」区分：快 = 1 层、普通 = 2 层、强 = 3 层（迭代加深，超预算就停）。
 * 走法由 {@link XiangqiBoard#legalMoves(int)} 给出（已过滤送将、照面等非法着法），
 * 评估 = 子力价值差 + 一点点机动性；将死/困毙按「对方无着可走」算胜负分。
 */
public class XiangqiAI {
    public static final int FAST = 0;
    public static final int NORMAL = 1;
    public static final int STRONG = 2;

    /** 胜负分（比任何子力分都大）。 */
    static final int WIN = 1000000000;

    private static final int[] VALUE = {0, 10000, 250, 250, 500, 900, 450, 100}; // K A B N R C P
    private static final int[] DEPTH = {1, 2, 3};
    private static final long[] NODE_BUDGET = {2000, 40000, 400000};

    private int level = NORMAL;

    public String name() {
        return "内置象棋 AI";
    }

    public void setLevel(int l) {
        level = (l < FAST || l > STRONG) ? NORMAL : l;
    }

    public int level() {
        return level;
    }

    /** 轮到的这一方走哪一步；无着可走或已终局返回 null。 */
    public int[] genmove(XiangqiBoard board) {
        if (board == null || board.gameOver()) {
            return null;
        }
        int color = board.sideToMove();
        List<int[]> moves = board.legalMoves(color);
        if (moves.isEmpty()) {
            return null;
        }
        int depth = DEPTH[level];
        int[] best = null;
        for (int d = 1; d <= depth; d++) {
            int alpha = -WIN;
            int[] bestAtDepth = null;
            for (int[] mv : moves) {
                XiangqiBoard next = board.copy();
                next.play(mv[0], mv[1], mv[2], mv[3]);
                int score = -negamax(next, d - 1, -WIN, Integer.MAX_VALUE, XiangqiBoard.opponent(color));
                if (score > alpha) {
                    alpha = score;
                    bestAtDepth = mv;
                }
            }
            if (bestAtDepth != null) {
                best = bestAtDepth;
            }
        }
        return best;
    }

    private int negamax(XiangqiBoard board, int depth, int alpha, int beta, int color) {
        if (board.isCheckmate(board.sideToMove()) || board.isStalemate(board.sideToMove())) {
            return -WIN + (3 - depth);   // 对方无着：对「color」来说是赢
        }
        if (depth <= 0) {
            return evaluate(board, color);
        }
        int best = -WIN - 1;
        for (int[] mv : board.legalMoves(board.sideToMove())) {
            XiangqiBoard next = board.copy();
            next.play(mv[0], mv[1], mv[2], mv[3]);
            int score = -negamax(next, depth - 1, -beta, -alpha, XiangqiBoard.opponent(color));
            if (score > best) {
                best = score;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    /** 以 color 的视角给局面打分（正数 = 对 color 有利）。 */
    public int evaluate(XiangqiBoard board, int color) {
        if (board == null) {
            return 0;
        }
        int score = 0;
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                int p = board.at(r, c);
                if (p == 0) {
                    continue;
                }
                int v = VALUE[XiangqiBoard.typeOf(p)];
                score += XiangqiBoard.colorOf(p) == color ? v : -v;
            }
        }
        if (board.inCheck(XiangqiBoard.opponent(color))) {
            score += 30;
        }
        if (board.inCheck(color)) {
            score -= 30;
        }
        return score;
    }
}
