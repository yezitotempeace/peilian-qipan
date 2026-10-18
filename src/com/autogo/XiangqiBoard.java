package com.autogo;

import java.util.ArrayList;
import java.util.List;

/**
 * 中国象棋规则引擎（纯 Java，无 Android 依赖，可 JVM 单测）。
 *
 * 坐标：row 0..9（0 = 黑方底线，9 = 红方底线），col 0..8。
 * 棋子编码：0 = 空；否则 (颜色 &lt;&lt; 4) | 类型，颜色 RED=1 / BLACK=2，
 * 类型 K=帅将 1 / A=仕士 2 / B=相象 3 / N=马 4 / R=车 5 / C=炮 6 / P=兵卒 7。
 * 红先走。走子一律「合法才落子」，非法返回 false（不抛异常，便于 AI 批量试走）。
 * 规则要点：蹩马腿、塞象眼、象不过河、士帅不出九宫、炮翻山吃子、
 * 兵过河后可横走、将帅照面非法、被将军时只能解将、无着可走即负（含困毙）。
 */
public final class XiangqiBoard {

    public static final int RED = 1;
    public static final int BLACK = 2;
    public static final int K = 1;   // 帅 / 将
    public static final int A = 2;   // 仕 / 士
    public static final int B = 3;   // 相 / 象
    public static final int N = 4;   // 马
    public static final int R = 5;   // 车
    public static final int C = 6;   // 炮
    public static final int P = 7;   // 兵 / 卒

    /** 棋子中文名（下标 = 兵种编码）。 */
    private static final String[] TYPE_NAMES = {"", "帅", "仕", "相", "马", "车", "炮", "兵"};

    /** 棋盘行数（10）与列数（9）。 */
    public static final int ROWS = 10;
    public static final int COLS = 9;

    private static final int RED_START = 6;   // 红兵起始行
    private static final int BLACK_START = 3; // 黑卒起始行

    private final int[][] grid = new int[ROWS][COLS];
    /** 落子历史，每手 = {fromRow, fromCol, toRow, toCol, 被吃子编码}。 */
    private final List<int[]> moves = new ArrayList<int[]>();
    private int sideToMove = RED;

    /** 开一个标准开局。 */
    public XiangqiBoard() {
        setupStandard();
    }

    /** 清空棋盘（给测试／摆残局用）。 */
    public XiangqiBoard(boolean empty) {
        if (!empty) {
            setupStandard();
        }
    }

    private void setupStandard() {
        put(0, 0, BLACK, R);
        put(0, 1, BLACK, N);
        put(0, 2, BLACK, B);
        put(0, 3, BLACK, A);
        put(0, 4, BLACK, K);
        put(0, 5, BLACK, A);
        put(0, 6, BLACK, B);
        put(0, 7, BLACK, N);
        put(0, 8, BLACK, R);
        put(2, 1, BLACK, C);
        put(2, 7, BLACK, C);
        for (int c = 0; c < COLS; c += 2) {
            put(3, c, BLACK, P);
        }
        put(9, 0, RED, R);
        put(9, 1, RED, N);
        put(9, 2, RED, B);
        put(9, 3, RED, A);
        put(9, 4, RED, K);
        put(9, 5, RED, A);
        put(9, 6, RED, B);
        put(9, 7, RED, N);
        put(9, 8, RED, R);
        put(7, 1, RED, C);
        put(7, 7, RED, C);
        for (int c = 0; c < COLS; c += 2) {
            put(6, c, RED, P);
        }
        sideToMove = RED;
    }

    // ---------- 基本读写 ----------

    /** 摆子（不走规则，只给测试／开局用）。 */
    public void put(int row, int col, int color, int type) {
        grid[row][col] = (color << 4) | type;
    }

    /** 取编码。 */
    public int at(int row, int col) {
        return grid[row][col];
    }

    /** 颜色（0 = 空）。 */
    public static int colorOf(int piece) {
        return piece == 0 ? 0 : piece >> 4;
    }

    /** 类型（0 = 空）。 */
    public static int typeOf(int piece) {
        return piece == 0 ? 0 : piece & 15;
    }

    /** 棋子的中文名。 */
    public static String typeName(int piece) {
        int t = typeOf(piece);
        return (t >= 1 && t < TYPE_NAMES.length) ? TYPE_NAMES[t] : "?";
    }

    /** 走法文字（如「A9 → A8」）。 */
    public static String moveText(int[] mv) {
        return colName(mv[1]) + (mv[0] + 1) + " → " + colName(mv[3]) + (mv[2] + 1);
    }

    public static int opponent(int color) {
        return color == RED ? BLACK : RED;
    }

    /** 该谁走。 */
    public int sideToMove() {
        return sideToMove;
    }

    /** 手数。 */
    public int moveCount() {
        return moves.size();
    }

    /** 落子历史（每手 {fromRow, fromCol, toRow, toCol, 被吃子}）。 */
    public List<int[]> history() {
        return moves;
    }

    /** 找到某方的帅 / 将，返回 {row, col}，找不到返回 null。 */
    public int[] kingOf(int color) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] == ((color << 4) | K)) {
                    return new int[] {r, c};
                }
            }
        }
        return null;
    }

    private static boolean inBoard(int r, int c) {
        return r >= 0 && r < ROWS && c >= 0 && c < COLS;
    }

    private static boolean inPalace(int color, int r, int c) {
        if (c < 3 || c > 5) {
            return false;
        }
        return color == RED ? (r >= 7 && r <= 9) : (r >= 0 && r <= 2);
    }

    // ---------- 走法 ----------

    /** 从 (row,col) 出发的全部「不乱走」着法（不含「走后被将军」过滤）。 */
    private void pseudoMoves(int row, int col, List<int[]> out) {
        int piece = grid[row][col];
        if (piece == 0) {
            return;
        }
        int color = colorOf(piece);
        int type = typeOf(piece);
        if (type == K) {
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] m : d) {
                addIfOk(row, col, row + m[0], col + m[1], color, out, false);
            }
        } else if (type == A) {
            int[][] d = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
            for (int[] m : d) {
                addIfOk(row, col, row + m[0], col + m[1], color, out, false);
            }
        } else if (type == B) {
            int[][] d = {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
            for (int[] m : d) {
                int r2 = row + m[0], c2 = col + m[1];
                if (!inBoard(r2, c2)) {
                    continue;
                }
                if (crossedRiver(color, r2)) {
                    continue;   // 象不过河
                }
                if (grid[row + m[0] / 2][col + m[1] / 2] != 0) {
                    continue;   // 塞象眼
                }
                addIfOk(row, col, r2, c2, color, out, false);
            }
        } else if (type == N) {
            int[][] d = {{2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {1, -2}, {-1, 2}, {-1, -2}};
            for (int[] m : d) {
                int r2 = row + m[0], c2 = col + m[1];
                if (!inBoard(r2, c2)) {
                    continue;
                }
                int legR = row + (Math.abs(m[0]) == 2 ? m[0] / 2 : 0);
                int legC = col + (Math.abs(m[1]) == 2 ? m[1] / 2 : 0);
                if (grid[legR][legC] != 0) {
                    continue;   // 蹩马腿
                }
                addIfOk(row, col, r2, c2, color, out, false);
            }
        } else if (type == R) {
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] m : d) {
                int r2 = row + m[0], c2 = col + m[1];
                while (inBoard(r2, c2)) {
                    if (grid[r2][c2] == 0) {
                        out.add(new int[] {row, col, r2, c2});
                    } else {
                        if (colorOf(grid[r2][c2]) != color) {
                            out.add(new int[] {row, col, r2, c2});
                        }
                        break;
                    }
                    r2 += m[0];
                    c2 += m[1];
                }
            }
        } else if (type == C) {
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] m : d) {
                int r2 = row + m[0], c2 = col + m[1];
                boolean screen = false;
                while (inBoard(r2, c2)) {
                    int p = grid[r2][c2];
                    if (!screen) {
                        if (p == 0) {
                            out.add(new int[] {row, col, r2, c2});
                        } else {
                            screen = true;   // 这个子当炮架
                        }
                    } else {
                        if (p != 0) {
                            if (colorOf(p) != color) {
                                out.add(new int[] {row, col, r2, c2});   // 翻山吃子
                            }
                            break;
                        }
                    }
                    r2 += m[0];
                    c2 += m[1];
                }
            }
        } else if (type == P) {
            int forward = color == RED ? -1 : 1;
            addIfOk(row, col, row + forward, col, color, out, false);
            if (crossedRiver(color, row)) {
                addIfOk(row, col, row, col - 1, color, out, false);
                addIfOk(row, col, row, col + 1, color, out, false);
            }
        }
    }

    private boolean crossedRiver(int color, int row) {
        return color == RED ? row <= 4 : row >= 5;
    }

    private void addIfOk(int row, int col, int r2, int c2, int color, List<int[]> out, boolean jump) {
        if (!inBoard(r2, c2)) {
            return;
        }
        if (typeOf(grid[row][col]) == K || typeOf(grid[row][col]) == A) {
            if (!inPalace(color, r2, c2)) {
                return;
            }
        }
        int target = grid[r2][c2];
        if (target != 0 && colorOf(target) == color) {
            return;
        }
        out.add(new int[] {row, col, r2, c2});
    }

    /** 一方当前的全部合法着法，每项 {fromRow, fromCol, toRow, toCol}。 */
    public List<int[]> legalMoves(int color) {
        List<int[]> out = new ArrayList<int[]>();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (colorOf(grid[r][c]) != color) {
                    continue;
                }
                List<int[]> ps = new ArrayList<int[]>();
                pseudoMoves(r, c, ps);
                for (int[] mv : ps) {
                    if (isLegalIfPlayed(mv[0], mv[1], mv[2], mv[3])) {
                        out.add(mv);
                    }
                }
            }
        }
        return out;
    }

    /** 试走是否合法（不吃将、不走后自将、不造成将帅照面、轮到谁就走谁）。 */
    public boolean isLegal(int fromRow, int fromCol, int toRow, int toCol) {
        if (!inBoard(fromRow, fromCol) || !inBoard(toRow, toCol)) {
            return false;
        }
        int piece = grid[fromRow][fromCol];
        if (piece == 0 || colorOf(piece) != sideToMove) {
            return false;
        }
        for (int[] mv : legalMoves(sideToMove)) {
            if (mv[0] == fromRow && mv[1] == fromCol && mv[2] == toRow && mv[3] == toCol) {
                return true;
            }
        }
        return false;
    }

    private boolean isLegalIfPlayed(int fromRow, int fromCol, int toRow, int toCol) {
        int mover = grid[fromRow][fromCol];
        int color = colorOf(mover);
        int taken = grid[toRow][toCol];
        if (typeOf(taken) == K) {
            return false;   // 正规规则里不可能吃将（照面已经非法）
        }
        grid[toRow][toCol] = mover;
        grid[fromRow][fromCol] = 0;
        boolean ok = !inCheck(color) && !kingsFace();
        grid[fromRow][fromCol] = mover;
        grid[toRow][toCol] = taken;
        return ok;
    }

    /** 两帅／两将同列且中间无子（照面）。 */
    public boolean kingsFace() {
        int[] rk = kingOf(RED);
        int[] bk = kingOf(BLACK);
        if (rk == null || bk == null) {
            return false;
        }
        if (rk[1] != bk[1]) {
            return false;
        }
        int c = rk[1];
        int lo = Math.min(rk[0], bk[0]), hi = Math.max(rk[0], bk[0]);
        for (int r = lo + 1; r < hi; r++) {
            if (grid[r][c] != 0) {
                return false;
            }
        }
        return true;
    }

    /** 某方是否正被将军（含将帅照面）。 */
    public boolean inCheck(int color) {
        int[] king = kingOf(color);
        if (king == null) {
            return true;
        }
        int enemy = opponent(color);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (colorOf(grid[r][c]) != enemy) {
                    continue;
                }
                List<int[]> ps = new ArrayList<int[]>();
                pseudoMoves(r, c, ps);
                for (int[] mv : ps) {
                    if (mv[2] == king[0] && mv[3] == king[1]) {
                        return true;
                    }
                }
            }
        }
        return kingsFace();
    }

    /** 落子；合法才落并换手，返回是否落成。 */
    public boolean play(int fromRow, int fromCol, int toRow, int toCol) {
        if (!isLegal(fromRow, fromCol, toRow, toCol)) {
            return false;
        }
        int mover = grid[fromRow][fromCol];
        int taken = grid[toRow][toCol];
        grid[toRow][toCol] = mover;
        grid[fromRow][fromCol] = 0;
        moves.add(new int[] {fromRow, fromCol, toRow, toCol, taken});
        sideToMove = opponent(sideToMove);
        return true;
    }

    /** 被将死（正被将军且无合法着法）。 */
    public boolean isCheckmate(int color) {
        return inCheck(color) && legalMoves(color).isEmpty();
    }

    /** 困毙（未被将军但无合法着法）—— 中国象棋里同样是输。 */
    public boolean isStalemate(int color) {
        return !inCheck(color) && legalMoves(color).isEmpty();
    }

    /** 棋局是否已结束（一方无着可走）。 */
    public boolean gameOver() {
        return legalMoves(sideToMove).isEmpty();
    }

    /** 胜方（0 = 未分胜负）。 */
    public int winner() {
        if (legalMoves(sideToMove).isEmpty()) {
            return opponent(sideToMove);
        }
        return 0;
    }

    /** 结果文字，例如「红胜（黑方被将死）」。 */
    public String resultString() {
        if (legalMoves(sideToMove).isEmpty()) {
            int win = opponent(sideToMove);
            String how = inCheck(sideToMove) ? "被将死" : "被困毙";
            return (win == RED ? "红胜" : "黑胜") + "（" + (sideToMove == RED ? "红方" : "黑方") + how + "）";
        }
        return "未分胜负";
    }

    /** 深拷贝（给 AI 试走用）。 */
    public XiangqiBoard copy() {
        XiangqiBoard b = new XiangqiBoard(true);
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(grid[r], 0, b.grid[r], 0, COLS);
        }
        b.sideToMove = sideToMove;
        for (int[] mv : moves) {
            b.moves.add(new int[] {mv[0], mv[1], mv[2], mv[3], mv[4]});
        }
        return b;
    }

    /** 列名（A..I，跳过 I 的约定不用于象棋：象棋用 A..I 全 9 列）。 */
    public static String colName(int col) {
        return String.valueOf((char) ('A' + col));
    }

    /** 一步棋的文字（如「车 (9,0)→(8,0)」）。 */
    public static String moveName(int[] mv) {
        return "(" + mv[0] + "," + mv[1] + ")→(" + mv[2] + "," + mv[3] + ")";
    }
}
