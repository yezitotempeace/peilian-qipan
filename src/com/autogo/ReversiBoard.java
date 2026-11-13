/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 黑白棋（Reversi / Othello）规则引擎。
 *
 * <p>8×8 棋盘，黑先。落子必须能夹住对方至少一枚棋子并翻转它们；
 * 无棋可走的一方自动弃权（对方继续）；双方都无棋可走或棋盘下满即结束，子多者胜。</p>
 */
public class ReversiBoard {

    public static final int SIZE = 8;
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

    private final int[][] cell = new int[SIZE][SIZE];
    private int sideToMove = BLACK;
    private boolean gameOver;
    /** 连续弃权次数（双方都无棋可走 ⇒ 2 次 ⇒ 结束）。 */
    private int passes;
    /** 最近一手是否发生过翻子（供 UI 提示）。 */
    private boolean lastWasFlip;
    /** 最近一手翻掉了多少枚。 */
    private int lastFlipCount;

    public ReversiBoard() {
        reset();
    }

    public void reset() {
        for (int[] row : cell) {
            Arrays.fill(row, EMPTY);
        }
        cell[3][3] = WHITE;
        cell[4][4] = WHITE;
        cell[3][4] = BLACK;
        cell[4][3] = BLACK;
        sideToMove = BLACK;
        gameOver = false;
        passes = 0;
        lastWasFlip = false;
        lastFlipCount = 0;
    }

    public int at(int r, int c) {
        return cell[r][c];
    }

    public int sideToMove() {
        return sideToMove;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean lastWasFlip() {
        return lastWasFlip;
    }

    public int lastFlipCount() {
        return lastFlipCount;
    }

    public static int opponent(int side) {
        return side == BLACK ? WHITE : BLACK;
    }

    /** (r,c) 是否为 side 的合法落点：空格，且某个方向能夹住对方棋子。 */
    public boolean isLegal(int side, int r, int c) {
        if (gameOver || r < 0 || r >= SIZE || c < 0 || c >= SIZE || cell[r][c] != EMPTY) {
            return false;
        }
        int opp = opponent(side);
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int rr = r + dr, cc = c + dc, cnt = 0;
                while (rr >= 0 && rr < SIZE && cc >= 0 && cc < SIZE && cell[rr][cc] == opp) {
                    rr += dr;
                    cc += dc;
                    cnt++;
                }
                if (cnt > 0 && rr >= 0 && rr < SIZE && cc >= 0 && cc < SIZE && cell[rr][cc] == side) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<int[]> legalMoves(int side) {
        List<int[]> out = new ArrayList<int[]>();
        if (gameOver) {
            return out;
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (isLegal(side, r, c)) {
                    out.add(new int[]{r, c});
                }
            }
        }
        return out;
    }

    /** 落子并翻转被夹住的棋子。非法则返回 false。 */
    public boolean play(int side, int r, int c) {
        if (side != sideToMove || !isLegal(side, r, c)) {
            return false;
        }
        int opp = opponent(side);
        cell[r][c] = side;
        int flipped = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int rr = r + dr, cc = c + dc;
                List<int[]> line = new ArrayList<int[]>();
                while (rr >= 0 && rr < SIZE && cc >= 0 && cc < SIZE && cell[rr][cc] == opp) {
                    line.add(new int[]{rr, cc});
                    rr += dr;
                    cc += dc;
                }
                if (!line.isEmpty() && rr >= 0 && rr < SIZE && cc >= 0 && cc < SIZE
                        && cell[rr][cc] == side) {
                    for (int[] p : line) {
                        cell[p[0]][p[1]] = side;
                    }
                    flipped += line.size();
                }
            }
        }
        lastWasFlip = flipped > 0;
        lastFlipCount = flipped;
        advance();
        return true;
    }

    /** 推进回合：对方无棋则其弃权（己方继续）；双方都无棋或棋盘满则结束。 */
    private void advance() {
        int cur = sideToMove;
        int opp = opponent(cur);
        boolean curHas = !legalMoves(cur).isEmpty();
        boolean oppHas = !legalMoves(opp).isEmpty();
        if (count(EMPTY) == 0 || (!curHas && !oppHas)) {
            gameOver = true;
            return;
        }
        if (oppHas) {
            sideToMove = opp;
            passes = 0;
        } else {
            // 对方弃权，己方继续
            passes++;
            if (passes >= 2) {
                gameOver = true;
            }
        }
    }

    public int count(int side) {
        int n = 0;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (cell[r][c] == side) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 1 = 黑胜，2 = 白胜，0 = 平，-1 = 未结束。 */
    public int winner() {
        if (!gameOver) {
            return -1;
        }
        int b = count(BLACK), w = count(WHITE);
        return b > w ? BLACK : (w > b ? WHITE : 0);
    }

    /** 深拷贝（AI 搜索用）。 */
    public ReversiBoard copy() {
        ReversiBoard b = new ReversiBoard();
        for (int r = 0; r < SIZE; r++) {
            System.arraycopy(cell[r], 0, b.cell[r], 0, SIZE);
        }
        b.sideToMove = sideToMove;
        b.gameOver = gameOver;
        b.passes = passes;
        return b;
    }

    /** 测试挂钩：直接摆子。 */
    public void testPut(int r, int c, int side) {
        cell[r][c] = side;
    }

    /** 测试挂钩：直接设定回合与结束状态。 */
    public void testSetState(int side, boolean over) {
        sideToMove = side;
        gameOver = over;
    }
}
