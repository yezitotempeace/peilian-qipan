package com.autogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 军棋（二人陆战棋）棋盘与规则核心 —— 纯 Java，无 Android 依赖，可 JVM 单测。
 *
 * <p>本类采用「二人军棋 · 明棋」口径（暗棋在界面层靠隐藏棋子实现，AI 不得偷看）：
 * <ul>
 *   <li>棋盘 5 列 × 12 行；红方在下（行 9..11 一侧），黑方在上。</li>
 *   <li>大本营：(0,1)(0,3) 黑、(11,1)(11,3) 红 —— 进大本营的棋子不能再移动。</li>
 *   <li>行营：(2,1)(2,3)(3,2)(4,1)(4,3) 与镜像 (7,1)(7,3)(8,2)(9,1)(9,3)，行营里的棋子不能被吃。</li>
 *   <li>铁路：第 1/5/6/10 行整行；第 0/4 列的第 1..10 行；普通棋子可沿直线铁路任意远，工兵可沿铁路拐弯。</li>
 *   <li>棋子：司令 9 > 军长 8 > 师长 7 > 旅长 6 > 团长 5 > 营长 4 > 连长 3 > 排长 2 > 工兵 1；
 *       炸弹与任何棋子同归于尽（含地雷）；地雷不动、除工兵外谁碰谁死；军旗被吃即负。</li>
 * </ul>
 */
public final class JunqiBoard {

    public static final int COLS = 5;
    public static final int ROWS = 12;

    public static final int EMPTY = 0;
    public static final int RED = 1;
    public static final int BLACK = 2;

    /** 兵种（数值即军衔，越大越强；地雷/炸弹/军旗用负数）。 */
    public static final int FLAG = -2;
    public static final int BOMB = -1;
    public static final int MINE = -3;
    public static final int ENGINEER = 1;
    public static final int PLATOON = 2;
    public static final int COMPANY = 3;
    public static final int BATTALION = 4;
    public static final int REGIMENT = 5;
    public static final int BRIGADE = 6;
    public static final int DIVISION = 7;
    public static final int ARMY = 8;
    public static final int COMMANDER = 9;

    /** 每方 25 个棋子：司令 1、军长 1、师长 2、旅长 2、团长 2、营长 2、连长 3、排长 3、工兵 3、地雷 3、炸弹 2、军旗 1。 */
    private static final int[] ARMY_SET = {
            COMMANDER, ARMY, DIVISION, DIVISION, BRIGADE, BRIGADE, REGIMENT, REGIMENT,
            BATTALION, BATTALION, COMPANY, COMPANY, COMPANY, PLATOON, PLATOON, PLATOON,
            ENGINEER, ENGINEER, ENGINEER, MINE, MINE, MINE, BOMB, BOMB, FLAG
    };

    /** 一方可摆子的 30 个格：红方行 6..11、黑方行 0..5（各 5 列 × 6 行）。 */
    public static int setupRows(int color) {
        return color == RED ? 6 : 0;
    }

    private final int[] grid = new int[ROWS * COLS];
    private final int[] owner = new int[ROWS * COLS];
    private int sideToMove = RED;
    private final List<int[]> history = new ArrayList<int[]>();
    private int winner;
    private String endReason = "";

    public JunqiBoard() {
        setupStandard(null);
    }

    public JunqiBoard(boolean empty) {
        if (!empty) {
            setupStandard(null);
        }
    }

    /** 除军旗/地雷/炸弹外的 19 个兵种（随机摆子用）。 */
    private static final int[] OTHER_SET = {
            COMMANDER, ARMY, DIVISION, DIVISION, BRIGADE, BRIGADE, REGIMENT, REGIMENT,
            BATTALION, BATTALION, COMPANY, COMPANY, COMPANY, PLATOON, PLATOON, PLATOON,
            ENGINEER, ENGINEER, ENGINEER
    };

    /**
     * 随机摆子（暗棋开局用）；同 seed 摆法一致，便于测试。
     *
     * <p>按军棋规则摆：军旗必须在本方大本营（底线两格之一）；地雷只能放后两排；
     * 炸弹不能放第一排；其余子随机。以前是 25 个子完全随机，军旗可能出现在最前排，
     * 一步就被夺旗，AI 强弱也测不出来。
     */
    public void setupStandard(Random rnd) {
        java.util.Arrays.fill(grid, EMPTY);
        java.util.Arrays.fill(owner, EMPTY);
        history.clear();
        winner = 0;
        endReason = "";
        sideToMove = RED;
        Random r = rnd == null ? new Random(20261017L) : rnd;
        for (int color = RED; color <= BLACK; color++) {
            int base = setupRows(color);          // 红 6、黑 0
            int backRow = color == RED ? ROWS - 1 : 0;
            int frontRow = color == RED ? base : base + 5;
            // 摆子格 = 兵站 + 大本营（行营开局留空：安全区不能一上来就塞子，符合正规军棋）
            List<Integer> all = new ArrayList<Integer>();
            for (int row = base; row < base + 6; row++) {
                for (int col = 0; col < COLS; col++) {
                    if (!isCamp(row, col)) {
                        all.add(idx(row, col));
                    }
                }
            }
            // 军旗：底线两个大本营之一
            List<Integer> hq = new ArrayList<Integer>();
            hq.add(idx(backRow, 1));
            hq.add(idx(backRow, 3));
            java.util.Collections.shuffle(hq, r);
            int flagAt = hq.get(0);
            grid[flagAt] = FLAG;
            owner[flagAt] = color;

            // 地雷：后两排（去掉军旗那一格）
            List<Integer> backTwo = new ArrayList<Integer>();
            for (int row = backRow == 0 ? 0 : ROWS - 2; row <= (backRow == 0 ? 1 : ROWS - 1); row++) {
                for (int col = 0; col < COLS; col++) {
                    int p = idx(row, col);
                    if (p != flagAt) {
                        backTwo.add(p);
                    }
                }
            }
            java.util.Collections.shuffle(backTwo, r);
            for (int i = 0; i < 3; i++) {
                int p = backTwo.get(i);
                grid[p] = MINE;
                owner[p] = color;
            }

            // 炸弹：不能在第一排（去掉已用格）
            List<Integer> bombCells = new ArrayList<Integer>();
            for (int p : all) {
                int row = rowOf(p);
                if (row == frontRow) {
                    continue;
                }
                if (owner[p] != EMPTY) {
                    continue;
                }
                bombCells.add(p);
            }
            java.util.Collections.shuffle(bombCells, r);
            for (int i = 0; i < 2; i++) {
                int p = bombCells.get(i);
                grid[p] = BOMB;
                owner[p] = color;
            }

            // 其余 19 子：剩下的空格随机
            List<Integer> rest = new ArrayList<Integer>();
            for (int p : all) {
                if (owner[p] == EMPTY) {
                    rest.add(p);
                }
            }
            java.util.Collections.shuffle(rest, r);
            for (int i = 0; i < OTHER_SET.length; i++) {
                int p = rest.get(i);
                grid[p] = OTHER_SET[i];
                owner[p] = color;
            }
        }
    }

    // ---------- 坐标与基本读写 ----------

    public static int idx(int row, int col) {
        return row * COLS + col;
    }

    public static int rowOf(int point) {
        return point / COLS;
    }

    public static int colOf(int point) {
        return point % COLS;
    }

    public static boolean inBounds(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col < COLS;
    }

    public int at(int row, int col) {
        return grid[idx(row, col)];
    }

    public int ownerAt(int row, int col) {
        return owner[idx(row, col)];
    }

    public void put(int row, int col, int color, int type) {
        int p = idx(row, col);
        grid[p] = type;
        owner[p] = color;
    }


    private static final String[] TYPE_NAMES = {"地雷", "工兵", "排长", "连长", "营长", "团长", "旅长", "师长", "军长", "司令"};

    /** 兵种名（军旗 / 炸弹单独处理，其余按常量数值索引）。 */
    public static String typeName(int type) {
        if (type == FLAG) {
            return "军旗";
        }
        if (type == BOMB) {
            return "炸弹";
        }
        if (type == MINE) {
            return "地雷";
        }
        if (type >= ENGINEER && type < TYPE_NAMES.length) {
            return TYPE_NAMES[type];
        }
        return "";
    }

    public int sideToMove() {
        return sideToMove;
    }

    public int winner() {
        return winner;
    }

    public String endReason() {
        return endReason;
    }

    public boolean gameOver() {
        return winner != 0;
    }

    public List<int[]> history() {
        return new ArrayList<int[]>(history);
    }

    public static int opponent(int color) {
        return color == RED ? BLACK : RED;
    }

    // ---------- 特殊格 ----------

    public static boolean isHq(int row, int col) {
        return col == 1 || col == 3 ? (row == 0 || row == ROWS - 1) : false;
    }

    public static boolean isCamp(int row, int col) {
        if (col == 2) {
            return row == 3 || row == 8;
        }
        if (col == 1 || col == 3) {
            return row == 2 || row == 4 || row == 7 || row == 9;
        }
        return false;
    }

    /** 铁路格（可沿铁路长距离移动）。 */
    public static boolean isRail(int row, int col) {
        if (row == 1 || row == 5 || row == 6 || row == 10) {
            return true;
        }
        return (col == 0 || col == 4) && row >= 1 && row <= 10;
    }

    public static boolean sameRailLine(int r1, int c1, int r2, int c2) {
        if (r1 == r2) {
            return isRail(r1, c1) && isRail(r2, c2);
        }
        if (c1 == c2) {
            return isRail(r1, c1) && isRail(r2, c2);
        }
        return false;
    }

    public static String pointName(int row, int col) {
        return String.valueOf((char) ('A' + col)) + (ROWS - row);
    }

    private JunqiBoard(JunqiBoard o) {
        System.arraycopy(o.grid, 0, grid, 0, grid.length);
        System.arraycopy(o.owner, 0, owner, 0, owner.length);
        sideToMove = o.sideToMove;
        winner = o.winner;
        endReason = o.endReason;
        history.addAll(o.history);
    }

    public JunqiBoard copy() {
        return new JunqiBoard(this);
    }


    // ---------- 走子与战斗 ----------

    /** 该格棋子能不能动（地雷、军旗、以及进了大本营的棋子都不能动）。 */
    public boolean canMovePiece(int point) {
        int t = grid[point];
        if (owner[point] == EMPTY || t == MINE || t == FLAG) {
            return false;
        }
        return !isHq(rowOf(point), colOf(point));
    }

    /** 某一方的全部合法着法，每个着法 {fromPoint, toPoint}。 */
    public List<int[]> legalMoves(int color) {
        List<int[]> out = new ArrayList<int[]>();
        for (int p = 0; p < grid.length; p++) {
            if (owner[p] != color || !canMovePiece(p)) {
                continue;
            }
            int r = rowOf(p);
            int c = colOf(p);
            int type = grid[p];
            for (int[] d : DIRS) {
                int nr = r + d[0];
                int nc = c + d[1];
                if (inBounds(nr, nc)) {
                    addIfOk(out, p, idx(nr, nc), color);
                }
            }
            if (!isRail(r, c)) {
                continue;
            }
            if (type == ENGINEER) {
                engineerRail(out, p, color);
            } else {
                for (int[] d : DIRS) {
                    int nr = r + d[0];
                    int nc = c + d[1];
                    while (inBounds(nr, nc) && isRail(nr, nc)) {
                        int q = idx(nr, nc);
                        if (owner[q] == color) {
                            break;
                        }
                        if (owner[q] != EMPTY && isCamp(nr, nc)) {
                            break;
                        }
                        out.add(new int[]{p, q});
                        if (owner[q] != EMPTY) {
                            break;
                        }
                        nr += d[0];
                        nc += d[1];
                    }
                }
            }
        }
        return out;
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private void addIfOk(List<int[]> out, int from, int to, int color) {
        int o = owner[to];
        if (o == color) {
            return;
        }
        if (o != EMPTY && isCamp(rowOf(to), colOf(to))) {
            return;
        }
        out.add(new int[]{from, to});
    }

    /** 工兵沿铁路拐弯：只能走铁路格，己方格挡路，敌方格只能作为终点。 */
    private void engineerRail(List<int[]> out, int from, int color) {
        boolean[] seen = new boolean[grid.length];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<Integer>();
        seen[from] = true;
        queue.add(from);
        while (!queue.isEmpty()) {
            int p = queue.poll();
            int r = rowOf(p);
            int c = colOf(p);
            for (int[] d : DIRS) {
                int nr = r + d[0];
                int nc = c + d[1];
                if (!inBounds(nr, nc) || !isRail(nr, nc)) {
                    continue;
                }
                int np = idx(nr, nc);
                if (seen[np]) {
                    continue;
                }
                seen[np] = true;
                if (owner[np] == color) {
                    continue;
                }
                if (owner[np] != EMPTY && isCamp(nr, nc)) {
                    continue;
                }
                out.add(new int[]{from, np});
                if (owner[np] == EMPTY) {
                    queue.add(np);
                }
            }
        }
    }

    public boolean isLegal(int fr, int fc, int tr, int tc) {
        if (!inBounds(fr, fc) || !inBounds(tr, tc)) {
            return false;
        }
        int from = idx(fr, fc);
        if (owner[from] != sideToMove || !canMovePiece(from)) {
            return false;
        }
        int to = idx(tr, tc);
        List<int[]> all = legalMoves(sideToMove);
        for (int[] mv : all) {
            if (mv[0] == from && mv[1] == to) {
                return true;
            }
        }
        return false;
    }

    /** 落子：合法的才落，返回是否真的走了。战斗结果写在 {@link #lastBattle()}。 */
    public boolean play(int fr, int fc, int tr, int tc) {
        if (gameOver() || !isLegal(fr, fc, tr, tc)) {
            return false;
        }
        int from = idx(fr, fc);
        int to = idx(tr, tc);
        int atk = grid[from];
        int atkOwner = owner[from];
        int def = grid[to];
        int defOwner = owner[to];
        lastAtkType = atk;
        lastDefType = def;
        lastResult = 0;
        lastAttacker = atkOwner;
        history.add(new int[]{fr, fc, tr, tc});
        if (defOwner == EMPTY) {
            move(from, to);
            lastBattle = "移动";
        } else if (def == FLAG) {
            winner = atkOwner;
            endReason = "军旗被夺";
            move(from, to);
            lastBattle = "夺旗";
            lastResult = atkOwner;
            return true;
        } else if (atk == BOMB || def == BOMB) {
            clear(from);
            clear(to);
            lastBattle = "炸弹同归于尽";
        } else if (def == MINE) {
            if (atk == ENGINEER) {
                move(from, to);
                lastBattle = "工兵挖雷";
                lastResult = atkOwner;
            } else {
                clear(from);
                lastBattle = "踩雷阵亡";
                lastResult = defOwner;
            }
        } else if (atk > def) {
            move(from, to);
            lastBattle = "吃掉对方";
            lastResult = atkOwner;
        } else if (atk < def) {
            clear(from);
            lastBattle = "攻击失败阵亡";
            lastResult = defOwner;
        } else {
            clear(from);
            clear(to);
            lastBattle = "同级同归于尽";
        }
        sideToMove = opponent(atkOwner);
        if (winner == 0 && legalMoves(sideToMove).isEmpty()) {
            winner = opponent(sideToMove);
            endReason = "对方无子可走";
        }
        return true;
    }

    private String lastBattle = "";
    private int lastAtkType;
    private int lastDefType;
    private int lastResult;
    private int lastAttacker;

    public String lastBattle() {
        return lastBattle;
    }

    /** 上一手交战双方兵种与结果，供界面把「被吃的对方兵种」翻出来给人看。 */
    public int lastAtkType() { return lastAtkType; }
    public int lastDefType() { return lastDefType; }
    /** 上一手战斗胜方颜色（RED/BLACK）；同归于尽/普通移动为 0。 */
    public int lastResult() { return lastResult; }
    /** 上一手进攻方颜色（用于把「对方被吃的兵种」翻出来给人看）。 */
    public int lastAttacker() { return lastAttacker; }

    private void move(int from, int to) {
        grid[to] = grid[from];
        owner[to] = owner[from];
        clear(from);
    }

    private void clear(int p) {
        grid[p] = EMPTY;
        owner[p] = EMPTY;
    }

    public int count(int color) {
        int n = 0;
        for (int i = 0; i < grid.length; i++) {
            if (owner[i] == color) {
                n++;
            }
        }
        return n;
    }
}
