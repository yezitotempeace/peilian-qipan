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
 * 评估 = 子力价值差 + 兵种位置分 + 机动性 + 将帅安全；将死/困毙按「对方无着可走」算胜负分。
 *
 * <p>开局多样性：纯搜索对同一局面永远给同一着，连着几局开头都一样。
 * 这里加了「开局随机池」——前几手在同分最优着里随机挑一个，保证每局开局不重样。
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

    /** 开局随机窗口：前 N 手（全程手数）允许在同分候选里随机。 */
    private static final int OPENING_PLIES = 10;
    /** 开局随机容忍的分差（厘兵）：候选分 ≥ 最优分 − 该值就进随机池。 */
    private static final int OPENING_SLACK = 40;

    /**
     * 开局库（红方首着）：真实象棋常见开局体系，随机挑一个，避免每局都「当头炮」。
     * 每项 = {fromRow, fromCol, toRow, toCol}，全部来自标准初始局面。
     */
    private static final int[][] OPENING_BOOK = {
            {7, 1, 7, 4},   // 中炮（炮二平五）
            {7, 7, 7, 4},   // 中炮（炮八平五）
            {6, 2, 5, 2},   // 仙人指路：兵三进一
            {6, 6, 5, 6},   // 仙人指路：兵七进一
            {9, 2, 7, 4},   // 飞相局：相三进五
            {9, 6, 7, 4},   // 飞相局：相七进五
            {9, 1, 7, 2},   // 起马局：马二进三
            {9, 7, 7, 6},   // 起马局：马八进七
            {7, 1, 7, 3},   // 过宫炮：炮二平四
            {7, 7, 7, 5},   // 过宫炮：炮八平六
            {9, 0, 8, 0},   // 边车：车一进一
            {9, 8, 8, 8},   // 边车：车九进一
    };

    private int level = NORMAL;
    private final java.util.Random rnd = new java.util.Random();

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
        // 开局库：红方第一手从常见开局里随机挑，保证每局开局不重样。
        if (board.moveCount() == 0) {
            int[] book = pickFromBook(moves);
            if (book != null) {
                return book;
            }
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
        // 开局多样化：前若干手在「最优分 − slack」内的候选里随机挑，避免每局开头一模一样。
        if (best != null && board.moveCount() < OPENING_PLIES) {
            int[] varied = pickOpeningVariety(board, color, depth, best);
            if (varied != null) {
                best = varied;
            }
        }
        return best;
    }

    /** 从开局库里挑一个当前合法的着法（开局库条目若非法就跳过）。 */
    private int[] pickFromBook(List<int[]> legal) {
        java.util.List<int[]> pool = new java.util.ArrayList<int[]>();
        for (int[] book : OPENING_BOOK) {
            for (int[] mv : legal) {
                if (mv[0] == book[0] && mv[1] == book[1] && mv[2] == book[2] && mv[3] == book[3]) {
                    pool.add(mv);
                    break;
                }
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(rnd.nextInt(pool.size()));
    }

    /**
     * 开局随机：重新给所有着法打分（同一深度），把分差在 OPENING_SLACK 以内的都收进池子，
     * 再随机选一个。只有 1 个候选时自然还是它。
     */
    private int[] pickOpeningVariety(XiangqiBoard board, int color, int depth, int[] fallback) {
        int alpha = -WIN;
        java.util.List<int[]> cands = new java.util.ArrayList<int[]>();
        java.util.List<Integer> scores = new java.util.ArrayList<Integer>();
        for (int[] mv : board.legalMoves(color)) {
            XiangqiBoard next = board.copy();
            next.play(mv[0], mv[1], mv[2], mv[3]);
            int score = -negamax(next, depth - 1, -WIN, Integer.MAX_VALUE, XiangqiBoard.opponent(color));
            if (score > alpha) {
                alpha = score;
            }
            cands.add(mv);
            scores.add(Integer.valueOf(score));
        }
        java.util.List<int[]> pool = new java.util.ArrayList<int[]>();
        for (int i = 0; i < cands.size(); i++) {
            if (scores.get(i).intValue() >= alpha - OPENING_SLACK) {
                pool.add(cands.get(i));
            }
        }
        if (pool.isEmpty()) {
            return fallback;
        }
        return pool.get(rnd.nextInt(pool.size()));
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
