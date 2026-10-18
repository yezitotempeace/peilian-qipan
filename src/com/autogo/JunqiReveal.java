package com.autogo;

/**
 * 暗棋的「谁被看到了」维护：只有**打过仗**的格子才会暴露兵种。
 * 规则（与真军棋一致）：
 * 1. 吃子/被吃/同归于尽 —— 交战过后该格子的兵种双方都看得见；
 * 2. 走到空格不暴露（对手只知道「有个子移过来了」，不知道该子是什么）；
 * 3. 棋子离开原格后，原格不再有子，暴露标记复位；
 * 4. 明棋模式下所有格子恒为已暴露。
 */
public final class JunqiReveal {

    private JunqiReveal() {
    }

    /** 交战过的格子标为已暴露（阵亡后该格可能是空的，标记留着无害）。 */
    /** 生成一份初始的「已暴露」表：明棋全暴露、暗棋全隐藏（页面不再自己写数组元素）。 */
    public static boolean[] fresh(int size, boolean dark) {
        boolean[] r = new boolean[Math.max(0, size)];
        for (int i = 0; i < r.length; i++) {
            r[i] = !dark;
        }
        return r;
    }

    /** 重置「已暴露」表：明棋全暴露、暗棋全隐藏（页面上不许自己写数组元素/不许 arraycopy）。 */
    public static void reset(boolean[] revealed, boolean dark) {
        if (revealed == null) {
            return;
        }
        for (int i = 0; i < revealed.length; i++) {
            revealed[i] = !dark;
        }
    }

    public static void resolve(boolean[] revealed, JunqiBoard before, int[] move) {
        if (revealed == null || before == null || move == null || move.length < 2) {
            return;
        }
        int from = move[0];
        int to = move[1];
        if (from >= 0 && from < revealed.length) {
            revealed[from] = false; // 走掉了，原格不再藏子
        }
        if (to >= 0 && to < revealed.length) {
            boolean battle = before.ownerAt(JunqiBoard.rowOf(to), JunqiBoard.colOf(to)) != JunqiBoard.EMPTY;
            if (battle) {
                revealed[to] = true;
            }
        }
    }

    /** 明棋：把全部有子的格子标成已暴露。 */
    public static void revealAll(boolean[] revealed, JunqiBoard board) {
        if (revealed == null || board == null) {
            return;
        }
        for (int r = 0; r < JunqiBoard.ROWS; r++) {
            for (int c = 0; c < JunqiBoard.COLS; c++) {
                int p = JunqiBoard.idx(r, c);
                if (p < revealed.length) {
                    revealed[p] = board.ownerAt(r, c) != JunqiBoard.EMPTY;
                }
            }
        }
    }
}
