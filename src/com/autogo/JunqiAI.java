package com.autogo;

import java.util.List;

/**
 * 军棋 AI（暗棋/明棋共用）。核心约束：**不得偷看**未暴露的敌子 ——
 * 决策前先把「未暴露的敌子」统一替换成 {@link #UNKNOWN}，因此改变这些棋子的真实兵种
 * 不会改变 AI 的走法（这一条用测试钉住）。
 */
public final class JunqiAI {

    /** 未暴露的敌子在 AI 眼里就是「一个排长」，只是一个占位估值。 */
    public static final int UNKNOWN = JunqiBoard.PLATOON;

    public static final int FAST = 0;
    public static final int NORMAL = 1;
    public static final int STRONG = 2;

    private int level = NORMAL;

    public JunqiAI() {
    }

    public JunqiAI(int level) {
        setLevel(level);
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        if (level < FAST || level > STRONG) {
            this.level = NORMAL;
        } else {
            this.level = level;
        }
    }

    /** 把棋盘上「不是 color 一方、且没被暴露」的棋子统一换成 {@link #UNKNOWN}。 */
    public static JunqiBoard masked(JunqiBoard board, int color, boolean[] revealed) {
        JunqiBoard view = board.copy();
        for (int r = 0; r < JunqiBoard.ROWS; r++) {
            for (int c = 0; c < JunqiBoard.COLS; c++) {
                int owner = view.ownerAt(r, c);
                if (owner == JunqiBoard.EMPTY || owner == color) {
                    continue;
                }
                int p = JunqiBoard.idx(r, c);
                if (revealed == null || p >= revealed.length || !revealed[p]) {
                    view.put(r, c, owner, UNKNOWN);
                }
            }
        }
        return view;
    }

    /**
     * 出一手。返回 {fromRow, fromCol, toRow, toCol}；终局或无棋可走返回 null。
     *
     * @param revealed 长度 = COLS*ROWS；true 表示该格棋子已暴露（双方都看得见）。可为 null（全暗）
     */
    public int[] genmove(JunqiBoard board, int color, boolean[] revealed) {
        if (board == null || board.gameOver()) {
            return null;
        }
        JunqiBoard view = masked(board, color, revealed);
        List<int[]> moves = view.legalMoves(color);
        if (moves.isEmpty()) {
            return null;
        }
        int[] best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int[] mv : moves) {
            int s = score(view, color, mv);
            if (s > bestScore) {
                bestScore = s;
                best = mv;
            }
        }
        return best == null ? null : to4(best);
    }

    /** legalMoves 用的是 {fromPoint, toPoint}，对外返回 {fr, fc, tr, tc}。 */
    private static int[] to4(int[] two) {
        return new int[]{JunqiBoard.rowOf(two[0]), JunqiBoard.colOf(two[0]),
                JunqiBoard.rowOf(two[1]), JunqiBoard.colOf(two[1])};
    }

    private int score(JunqiBoard view, int color, int[] mv) {
        JunqiBoard next = view.copy();
        int before = evaluate(next, color);
        next.play(JunqiBoard.rowOf(mv[0]), JunqiBoard.colOf(mv[0]),
                JunqiBoard.rowOf(mv[1]), JunqiBoard.colOf(mv[1]));
        int s = evaluate(next, color) - before;
        next = null;
        return s;
    }

    /** 从 color 视角看子力差（兵种常量越大越强；军旗/炸弹/地雷按特殊值算）。 */
    public static int evaluate(JunqiBoard board, int color) {
        int sum = 0;
        for (int r = 0; r < JunqiBoard.ROWS; r++) {
            for (int c = 0; c < JunqiBoard.COLS; c++) {
                int owner = board.ownerAt(r, c);
                if (owner == JunqiBoard.EMPTY) {
                    continue;
                }
                int v = value(board.at(r, c));
                sum += (owner == color) ? v : -v;
            }
        }
        return sum;
    }

    private static int value(int type) {
        switch (type) {
            case JunqiBoard.COMMANDER: return 900;
            case JunqiBoard.ARMY: return 700;
            case JunqiBoard.DIVISION: return 500;
            case JunqiBoard.BRIGADE: return 400;
            case JunqiBoard.REGIMENT: return 300;
            case JunqiBoard.BATTALION: return 250;
            case JunqiBoard.COMPANY: return 200;
            case JunqiBoard.PLATOON: return 150;
            case JunqiBoard.ENGINEER: return 120;
            case JunqiBoard.BOMB: return 260;
            case JunqiBoard.MINE: return 180;
            case JunqiBoard.FLAG: return 3000;
            default: return 100;
        }
    }
}
