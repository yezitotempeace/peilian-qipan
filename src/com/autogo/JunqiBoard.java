/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 军棋（陆战棋）规则引擎 —— 暗棋玩法，完全重写。
 *
 * <p><b>棋盘</b>：12 行 × 5 列，共 60 个可落子点；两军之间隔一条“前线带”
 * （3 个前线 + 2 座山界，山界不可通过）。棋盘按真实军棋绘制：</p>
 * <ul>
 *   <li>大本营：每方底线第 2、4 列（军旗必须放这里，进去的棋子不能再出来）；</li>
 *   <li>行营：每方 5 个，呈梅花形（第 2/3/4 排），行营中的棋子<b>不会被攻击</b>；</li>
 *   <li>铁路：第 1、5、6、10 行整行 + 左右两边线整列 + 中路（第 3 列）靠前线一段；
 *       铁路上普通棋子可直线滑行任意远（不可越子），<b>工兵</b>可沿铁路网拐弯；</li>
 *   <li>公路：其余连线，每次走一格；行营四周还有<b>斜线</b>，可斜走一步。</li>
 * </ul>
 *
 * <p><b>棋子</b>：每方 25 枚。司令、军长、军旗各一；师长、旅长、团长、营长、炸弹各二；
 * 连长、排长、工兵、地雷各三。军衔：司令>军长>师长>旅长>团长>营长>连长>排长>工兵。</p>
 *
 * <p><b>吃子</b>：高吃低，同级同归于尽；炸弹与任何棋子同归于尽；
 * 非工兵撞地雷则攻击方阵亡、地雷保留；<b>工兵挖雷</b>则地雷消失、工兵存活并占据该格。</p>
 *
 * <p><b>暗棋信息</b>：己方棋子始终可见；敌方棋子<b>只要没交战过就一直盖着</b>，
 * 交战过的双方棋子身份公开。棋子一旦移动过，就一定不是地雷/军旗（用于推断）。</p>
 *
 * <p><b>胜负</b>：吃掉对方军旗获胜；一方无合法着法判负；连续 200 步无吃子判和。</p>
 *
 * <p>本类只管规则，不碰 Android，可整体在 JVM 上单测。</p>
 */
public final class JunqiBoard {

    public static final int ROWS = 12;
    public static final int COLS = 5;

    /** 空。 */
    public static final int EMPTY = 0;
    /** 玩家（红，屏幕下半）。 */
    public static final int RED = 1;
    /** 电脑（黑，屏幕上半）。 */
    public static final int BLACK = 2;

    /** 棋子类型。军衔用 1..9（9 最高），特殊子用负数。 */
    public static final int SILING = 9;      // 司令
    public static final int JUNZHANG = 8;    // 军长
    public static final int SHIZHANG = 7;    // 师长
    public static final int LVZHANG = 6;     // 旅长
    public static final int TUANZHANG = 5;   // 团长
    public static final int YINGZHANG = 4;   // 营长
    public static final int LIANZHANG = 3;   // 连长
    public static final int PAIZHANG = 2;    // 排长
    public static final int GONGBING = 1;    // 工兵
    public static final int DILEI = -1;      // 地雷（不可动）
    public static final int ZHADAN = -2;     // 炸弹
    public static final int JUNQI = -3;      // 军旗（不可动）

    /** 无战斗（普通移动）。 */
    public static final int RES_MOVE = 0;
    /** 攻方吃掉守方。 */
    public static final int RES_WIN = 1;
    /** 攻方被吃。 */
    public static final int RES_LOSE = 2;
    /** 同归于尽。 */
    public static final int RES_BOTH = 3;

    /** 连续多少步无吃子判和。 */
    public static final int DRAW_STEPS = 200;

    /** 一枚棋子。 */
    public static final class Pc {
        public int side;         // RED / BLACK
        public int type;         // 上面常量之一
        public boolean revealed; // 是否已亮出（交战过 / 翻棋被翻开）
        public boolean moved;    // 是否移动过（移动过 ⇒ 一定不是地雷/军旗）
        public boolean faceDown; // 是否背面朝上（翻棋开局全扣；暗棋敌方扣着）

        Pc(int side, int type) {
            this.side = side;
            this.type = type;
        }
    }

    private static final int[] KIND = buildKind();          // 0=兵站 1=行营 2=大本营
    private static final boolean[][] RAIL = buildRail();
    private final Pc[] cell = new Pc[ROWS * COLS];

    /** 是否翻棋模式（翻牌算一步；暗棋为 false）。 */
    private final boolean flipMode;

    private int sideToMove = RED;
    private boolean setupPhase = true;
    private int quietSteps;
    private int result;                                     // 0=进行中 1=红胜 2=黑胜 3=和
    private String lastBattle = "";
    private final List<Pc> captured = new ArrayList<Pc>();

    /** 暗棋模式。 */
    public JunqiBoard(long seed) {
        this(seed, false);
    }

    /** flip=true 为翻棋：50 子随机扣着铺满除行营外的 50 个点位。 */
    public JunqiBoard(long seed, boolean flip) {
        flipMode = flip;
        Random rnd = new Random(seed);
        if (flip) {
            setupFlip(rnd);
            setupPhase = false;      // 翻棋没有布阵阶段，直接开翻
        } else {
            randomSetup(RED, rnd);
            randomSetup(BLACK, rnd);
        }
    }

    /** 翻棋布阵：双方 50 枚棋子打乱，扣着铺满除行营外的 50 个点位（行营留空作安全区）。 */
    private void setupFlip(Random rnd) {
        List<Integer> cells = new ArrayList<Integer>();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (KIND[idx(r, c)] != 1) {
                    cells.add(idx(r, c));
                }
            }
        }
        shuffle(cells, rnd);
        List<int[]> pool = new ArrayList<int[]>();
        int[][] set = {
                {RED, JUNQI, 1}, {RED, SILING, 1}, {RED, JUNZHANG, 1}, {RED, SHIZHANG, 2},
                {RED, LVZHANG, 2}, {RED, TUANZHANG, 2}, {RED, YINGZHANG, 2}, {RED, LIANZHANG, 3},
                {RED, PAIZHANG, 3}, {RED, GONGBING, 3}, {RED, DILEI, 3}, {RED, ZHADAN, 2},
                {BLACK, JUNQI, 1}, {BLACK, SILING, 1}, {BLACK, JUNZHANG, 1}, {BLACK, SHIZHANG, 2},
                {BLACK, LVZHANG, 2}, {BLACK, TUANZHANG, 2}, {BLACK, YINGZHANG, 2}, {BLACK, LIANZHANG, 3},
                {BLACK, PAIZHANG, 3}, {BLACK, GONGBING, 3}, {BLACK, DILEI, 3}, {BLACK, ZHADAN, 2},
        };
        for (int[] t : set) {
            for (int i = 0; i < t[2]; i++) {
                pool.add(new int[]{t[0], t[1]});
            }
        }
        shuffleInt(pool, rnd);
        for (int i = 0; i < cells.size() && i < pool.size(); i++) {
            int[] pt = pool.get(i);
            Pc p = new Pc(pt[0], pt[1]);
            p.faceDown = true;
            cell[cells.get(i)] = p;
        }
    }

    /** 是否翻棋模式。 */
    public boolean isFlipMode() {
        return flipMode;
    }

    /** 指定阵型构建（测试用）。formations[b][i] 依次对应 placeableCells(side) 的位置，<=0 表示留空。 */
    public JunqiBoard(int[][] formations) {
        flipMode = false;
        for (int b = 0; b < 2; b++) {
            int side = (b == 0) ? RED : BLACK;
            int[] f = formations[b];
            List<Integer> cells = placeableCells(side);
            for (int i = 0; i < cells.size() && i < f.length; i++) {
                if (f[i] > 0) {
                    cell[cells.get(i)] = new Pc(side, f[i]);
                }
            }
        }
        setupPhase = false;
    }

    // ------------------------------------------------------------ 地形

    private static int[] buildKind() {
        int[] k = new int[ROWS * COLS];
        for (int c : new int[]{1, 3}) {
            k[idx(0, c)] = 2;
            k[idx(11, c)] = 2;
        }
        int[][] camps = {
                {2, 1}, {2, 3}, {3, 2}, {4, 1}, {4, 3},
                {7, 1}, {7, 3}, {8, 2}, {9, 1}, {9, 3},
        };
        for (int[] rc : camps) {
            k[idx(rc[0], rc[1])] = 1;
        }
        return k;
    }

    private static boolean[][] buildRail() {
        boolean[][] rl = new boolean[ROWS][COLS];
        for (int c = 0; c < COLS; c++) {
            rl[1][c] = true;
            rl[5][c] = true;
            rl[6][c] = true;
            rl[10][c] = true;
        }
        for (int r = 0; r < ROWS; r++) {
            rl[r][0] = true;
            rl[r][4] = true;
        }
        for (int r = 4; r <= 7; r++) {
            rl[r][2] = true;
        }
        return rl;
    }

    public static int idx(int r, int c) {
        return r * COLS + c;
    }

    public static boolean isCamp(int r, int c) {
        return inBounds(r, c) && KIND[idx(r, c)] == 1;
    }

    public static boolean isHq(int r, int c) {
        return inBounds(r, c) && KIND[idx(r, c)] == 2;
    }

    public static boolean isRail(int r, int c) {
        return inBounds(r, c) && RAIL[r][c];
    }

    /** 该格棋子的归属（EMPTY = 无子）。 */
    public int ownerAt(int r, int c) {
        Pc p = at(r, c);
        return p == null ? EMPTY : p.side;
    }

    /** 该格棋子的类型（EMPTY = 无子）。 */
    public int typeAt(int r, int c) {
        Pc p = at(r, c);
        return p == null ? EMPTY : p.type;
    }

    /** 该格棋子是否应背面向上（只有“未亮出的敌方棋子”为真；玩家视角=红方）。 */
    public boolean faceDown(int r, int c) {
        Pc p = at(r, c);
        return p != null && p.faceDown;
    }

    /** 最近一次落子是否发生了战斗（用于吃子爆点动画）。 */
    public boolean lastWasFight() {
        return lastBattle != null && lastBattle.length() > 0;
    }

    private static boolean inBounds(int r, int c) {
        return r >= 0 && r < ROWS && c >= 0 && c < COLS;
    }

    /** 从 (r,c) 到 (nr,nc) 是否被山界挡住（仅中线 r5↔r6 的第 2/4 列被挡）。 */
    private boolean mountainBlocked(int r, int c, int nr, int nc) {
        boolean crossing = (r == 5 && nr == 6) || (r == 6 && nr == 5);
        return crossing && (c == 1 || c == 3) && nc == c;
    }

    // ------------------------------------------------------------ 布阵

    /** 本方可布子位置（兵站 + 大本营，共 25 个；行营开局必须留空）。 */
    public List<Integer> placeableCells(int side) {
        List<Integer> out = new ArrayList<Integer>();
        for (int r = 0; r < ROWS; r++) {
            if (sideOfRow(r) != side) {
                continue;
            }
            for (int c = 0; c < COLS; c++) {
                if (KIND[idx(r, c)] != 1) {
                    out.add(idx(r, c));
                }
            }
        }
        return out;
    }

    /** 该行属于哪一方（上半=黑，下半=红；无归属返回 0）。 */
    public int sideOfRow(int r) {
        if (r < 0 || r >= ROWS) {
            return 0;
        }
        return r <= 5 ? BLACK : RED;
    }

    /** 随机摆一个合法阵型：军旗进大本营、地雷限后两排、炸弹不进第一排。 */
    public void randomSetup(int side, Random rnd) {
        List<Integer> cells = placeableCells(side);
        for (int idx : cells) {
            cell[idx] = null;
        }
        shuffle(cells, rnd);          // 打乱后按顺序放，保证随机且均匀

        List<Integer> pool = new ArrayList<Integer>();
        addN(pool, JUNQI, 1);
        addN(pool, SILING, 1);
        addN(pool, JUNZHANG, 1);
        addN(pool, SHIZHANG, 2);
        addN(pool, LVZHANG, 2);
        addN(pool, TUANZHANG, 2);
        addN(pool, YINGZHANG, 2);
        addN(pool, LIANZHANG, 3);
        addN(pool, PAIZHANG, 3);
        addN(pool, GONGBING, 3);
        addN(pool, DILEI, 3);
        addN(pool, ZHADAN, 2);

        int frontRow = (side == RED) ? 6 : 5;
        int mineMin = (side == RED) ? 10 : 0;
        int mineMax = (side == RED) ? 11 : 1;

        // 先放受限棋子（军旗 / 地雷 / 炸弹）
        for (int t : new int[]{JUNQI, DILEI, DILEI, DILEI, ZHADAN, ZHADAN}) {
            for (int idx : cells) {
                if (cell[idx] != null) {
                    continue;
                }
                int r = idx / COLS;
                boolean ok;
                if (t == JUNQI) {
                    ok = isHq(r, idx % COLS);
                } else if (t == DILEI) {
                    ok = r >= mineMin && r <= mineMax;
                } else {
                    ok = r != frontRow;
                }
                if (ok) {
                    cell[idx] = new Pc(side, t);
                    pool.remove(Integer.valueOf(t));
                    break;
                }
            }
        }

        // 其余棋子按打乱顺序放剩余空位
        int p = 0;
        for (int idx : cells) {
            if (cell[idx] == null && p < pool.size()) {
                cell[idx] = new Pc(side, pool.get(p++));
            }
        }
        // 暗棋：己方明、敌方扣
        for (int idx : cells) {
            Pc pc = cell[idx];
            if (pc != null) {
                pc.faceDown = (pc.side == BLACK);
            }
        }
    }

    private static void addN(List<Integer> pool, int type, int n) {
        for (int i = 0; i < n; i++) {
            pool.add(type);
        }
    }

    private static void shuffleInt(List<int[]> a, Random rnd) {
        for (int i = a.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int[] t = a.get(i);
            a.set(i, a.get(j));
            a.set(j, t);
        }
    }

    private static void shuffle(List<Integer> a, Random rnd) {
        for (int i = a.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a.get(i);
            a.set(i, a.get(j));
            a.set(j, t);
        }
    }

    /** 布阵阶段：对调本方两枚棋子（玩家手动调整阵型）。 */
    public boolean swapOwn(int side, int r1, int c1, int r2, int c2) {
        if (!setupPhase || !inBounds(r1, c1) || !inBounds(r2, c2)) {
            return false;
        }
        Pc a = cell[idx(r1, c1)], b = cell[idx(r2, c2)];
        if (a == null || b == null || a.side != side || b.side != side) {
            return false;
        }
        if (!canSit(side, a.type, r2, c2) || !canSit(side, b.type, r1, c1)) {
            return false;
        }
        cell[idx(r1, c1)] = b;
        cell[idx(r2, c2)] = a;
        return true;
    }

    /** 棋子 type 能否放在 (r,c)（布阵约束）。 */
    private boolean canSit(int side, int type, int r, int c) {
        if (isCamp(r, c)) {
            return false;
        }
        int frontRow = (side == RED) ? 6 : 5;
        if (type == JUNQI) {
            return isHq(r, c);
        }
        if (type == DILEI) {
            return (side == RED) ? (r >= 10) : (r <= 1);
        }
        if (type == ZHADAN) {
            return r != frontRow;
        }
        return true;
    }

    /** 结束布阵，开始对局（红先）。 */
    public void beginBattle() {
        setupPhase = false;
        sideToMove = RED;
    }

    public boolean isSetupPhase() {
        return setupPhase;
    }

    // ------------------------------------------------------------ 查询

    public Pc at(int r, int c) {
        if (!inBounds(r, c)) {
            return null;
        }
        return cell[idx(r, c)];
    }

    public int sideToMove() {
        return sideToMove;
    }

    public boolean gameOver() {
        return result != 0;
    }

    /** 0=进行中 1=红胜 2=黑胜 3=和棋。 */
    public int result() {
        return result;
    }

    public String lastBattle() {
        return lastBattle;
    }

    public List<Pc> captured() {
        return captured;
    }

    public static String typeName(int t) {
        switch (t) {
            case SILING: return "司令";
            case JUNZHANG: return "军长";
            case SHIZHANG: return "师长";
            case LVZHANG: return "旅长";
            case TUANZHANG: return "团长";
            case YINGZHANG: return "营长";
            case LIANZHANG: return "连长";
            case PAIZHANG: return "排长";
            case GONGBING: return "工兵";
            case DILEI: return "地雷";
            case ZHADAN: return "炸弹";
            case JUNQI: return "军旗";
            default: return "?";
        }
    }

    /** 深拷贝（AI 搜索用）：地形是静态共享的，只复制棋子与对局状态。 */
    public JunqiBoard copy() {
        JunqiBoard b = new JunqiBoard(0L);
        for (int i = 0; i < cell.length; i++) {
            Pc p = cell[i];
            if (p == null) {
                b.cell[i] = null;
            } else {
                Pc q = new Pc(p.side, p.type);
                q.revealed = p.revealed;
                q.moved = p.moved;
                b.cell[i] = q;
            }
        }
        b.sideToMove = sideToMove;
        b.setupPhase = setupPhase;
        b.quietSteps = quietSteps;
        b.result = result;
        b.lastBattle = lastBattle;
        return b;
    }

    /** 该方所有棋子设为“已亮出”（确定性化搜索用：在这个采样的世界里视为全知）。 */
    public void revealAll(int side) {
        for (Pc p : cell) {
            if (p != null && p.side == side) {
                p.revealed = true;
            }
        }
    }

    // ------------------------------------------------------------ 测试挂钩

    /** 仅供测试：直接摆放一枚棋子（不走布阵校验）。 */
    public void testPut(int r, int c, int side, int type) {
        if (inBounds(r, c)) {
            cell[idx(r, c)] = new Pc(side, type);
        }
    }

    /** 仅供测试：清除一格。 */
    public void testClear(int r, int c) {
        if (inBounds(r, c)) {
            cell[idx(r, c)] = null;
        }
    }

    /** 仅供测试：跳过布阵，直接进入对局。 */
    public void testStartBattle() {
        setupPhase = false;
        sideToMove = RED;
    }

    /** 仅供 AI 搜索：直接替换一格棋子（保留棋子对象，不产生任何对局副作用）。 */
    public void aiSet(int r, int c, Pc p) {
        if (inBounds(r, c)) {
            cell[idx(r, c)] = p;
        }
    }

    // ------------------------------------------------------------ 走法生成

    private void roadNeighbors(int r, int c, List<int[]> out) {
        int[][] d = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] dd : d) {
            int nr = r + dd[0], nc = c + dd[1];
            if (!inBounds(nr, nc) || mountainBlocked(r, c, nr, nc)) {
                continue;
            }
            out.add(new int[]{nr, nc});
        }
        // 行营四周有斜线，可斜走一步
        if (isCamp(r, c)) {
            int[][] dg = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
            for (int[] dd : dg) {
                int nr = r + dd[0], nc = c + dd[1];
                if (inBounds(nr, nc)) {
                    out.add(new int[]{nr, nc});
                }
            }
        }
    }

    private void railNeighbors(int r, int c, List<int[]> out) {
        if (!isRail(r, c)) {
            return;
        }
        int[][] d = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] dd : d) {
            int nr = r + dd[0], nc = c + dd[1];
            if (!inBounds(nr, nc) || !RAIL[nr][nc] || mountainBlocked(r, c, nr, nc)) {
                continue;
            }
            out.add(new int[]{nr, nc});
        }
    }

    /** 地雷、军旗、以及进大本营的棋子都不能动。 */
    private boolean movable(Pc p, int r, int c) {
        if (p == null || p.type == DILEI || p.type == JUNQI) {
            return false;
        }
        return !isHq(r, c);
    }

    /** side 的所有合法着法，元素为 {fromRow, fromCol, toRow, toCol}。 */
    public List<int[]> legalMoves(int side) {
        List<int[]> out = new ArrayList<int[]>();
        if (gameOver() || setupPhase) {
            return out;
        }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Pc p = cell[idx(r, c)];
                if (p == null) {
                    continue;
                }
                if (flipMode) {
                    if (p.faceDown) {
                        out.add(new int[]{r, c, r, c});   // 翻牌也算一步
                    } else if (p.side == side && movable(p, r, c)) {
                        addMovesFrom(out, p, r, c);
                    }
                    continue;
                }
                if (p.side != side || !movable(p, r, c)) {
                    continue;
                }
                addMovesFrom(out, p, r, c);
            }
        }
        return out;
    }

    private void addMovesFrom(List<int[]> out, Pc p, int r, int c) {
        List<int[]> nb = new ArrayList<int[]>();
        roadNeighbors(r, c, nb);
        for (int[] n : nb) {
            addIfOk(out, p, r, c, n[0], n[1]);
        }
        if (isRail(r, c)) {
            if (p.type == GONGBING) {
                addEngineerRailMoves(out, p, r, c);
            } else {
                addStraightRailMoves(out, p, r, c);
            }
        }
    }

    /** 普通棋子：沿铁路直线滑行任意远，不可越子。 */
    private void addStraightRailMoves(List<int[]> out, Pc p, int r, int c) {
        int[][] d = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] dd : d) {
            int pr = r, pc = c;
            int nr = r + dd[0], nc = c + dd[1];
            while (inBounds(nr, nc) && isRail(nr, nc) && !mountainBlocked(pr, pc, nr, nc)) {
                Pc q = cell[idx(nr, nc)];
                if (q == null) {
                    addIfOk(out, p, r, c, nr, nc);
                } else {
                    if (q.side != p.side) {
                        addIfOk(out, p, r, c, nr, nc);
                    }
                    break;   // 有子挡路，直线到此为止
                }
                pr = nr;
                pc = nc;
                nr += dd[0];
                nc += dd[1];
            }
        }
    }

    /** 工兵：沿铁路网可拐弯，走到任意连通铁路格（路径不可被挡）。 */
    private void addEngineerRailMoves(List<int[]> out, Pc p, int r, int c) {
        boolean[][] seen = new boolean[ROWS][COLS];
        List<int[]> queue = new ArrayList<int[]>();
        queue.add(new int[]{r, c});
        seen[r][c] = true;
        int head = 0;
        while (head < queue.size()) {
            int[] cur = queue.get(head++);
            List<int[]> nb = new ArrayList<int[]>();
            railNeighbors(cur[0], cur[1], nb);
            for (int[] n : nb) {
                if (seen[n[0]][n[1]]) {
                    continue;
                }
                seen[n[0]][n[1]] = true;
                Pc q = cell[idx(n[0], n[1])];
                if (q != null && q.side == p.side) {
                    continue;                    // 己方子挡路
                }
                addIfOk(out, p, r, c, n[0], n[1]);
                if (q == null) {
                    queue.add(new int[]{n[0], n[1]});   // 空格才能继续往前
                }
            }
        }
    }

    /** 目标格合法性：己方格不行；敌方行营有子不能吃（行营安全）；去重。 */
    private void addIfOk(List<int[]> out, Pc p, int fr, int fc, int tr, int tc) {
        if (tr == fr && tc == fc) {
            return;
        }
        Pc q = cell[idx(tr, tc)];
        if (q != null && q.side == p.side) {
            return;
        }
        if (q != null && isCamp(tr, tc)) {
            return;
        }
        for (int[] m : out) {
            if (m[0] == fr && m[1] == fc && m[2] == tr && m[3] == tc) {
                return;
            }
        }
        out.add(new int[]{fr, fc, tr, tc});
    }

    // ------------------------------------------------------------ 落子与战斗

    public boolean isLegal(int side, int fr, int fc, int tr, int tc) {
        if (gameOver() || setupPhase) {
            return false;
        }
        if (flipMode && fr == tr && fc == tc) {
            Pc fp = at(fr, fc);
            return fp != null && fp.faceDown;   // 翻牌
        }
        Pc p = at(fr, fc);
        if (p == null || p.side != side || !movable(p, fr, fc)) {
            return false;
        }
        for (int[] m : legalMoves(side)) {
            if (m[0] == fr && m[1] == fc && m[2] == tr && m[3] == tc) {
                return true;
            }
        }
        return false;
    }

    /**
     * 走棋并结算战斗。返回攻方视角的 {@link #RES_MOVE}/{@link #RES_WIN}/{@link #RES_LOSE}/{@link #RES_BOTH}；
     * 非法着法返回 -1 且不改变局面。
     */
    public int play(int side, int fr, int fc, int tr, int tc) {
        if (!isLegal(side, fr, fc, tr, tc)) {
            return -1;
        }
        if (flipMode && fr == tr && fc == tc) {
            Pc fp = cell[idx(fr, fc)];
            if (fp == null || !fp.faceDown) {
                return -1;
            }
            fp.faceDown = false;
            fp.revealed = true;
            lastBattle = "翻出 " + typeName(fp.type);
            sideToMove = opponent(side);
            return RES_MOVE;
        }
        Pc p = cell[idx(fr, fc)];
        Pc q = cell[idx(tr, tc)];
        int res = RES_MOVE;

        if (q == null) {
            cell[idx(tr, tc)] = p;
            cell[idx(fr, fc)] = null;
            lastBattle = "";
        } else {
            p.revealed = true;      // 交战后双方亮身份
            q.revealed = true;
            p.faceDown = false;
            q.faceDown = false;
            int outcome = fight(p.type, q.type);
            if (outcome > 0) {
                cell[idx(tr, tc)] = p;
                cell[idx(fr, fc)] = null;
                captured.add(q);
                res = RES_WIN;
                lastBattle = typeName(p.type) + " 吃掉 " + typeName(q.type);
            } else if (outcome < 0) {
                cell[idx(fr, fc)] = null;
                captured.add(p);
                res = RES_LOSE;
                lastBattle = typeName(q.type) + " 吃掉 " + typeName(p.type);
            } else {
                cell[idx(fr, fc)] = null;
                cell[idx(tr, tc)] = null;
                captured.add(p);
                captured.add(q);
                res = RES_BOTH;
                lastBattle = typeName(p.type) + " 与 " + typeName(q.type) + " 同归于尽";
            }
            quietSteps = 0;
        }

        if (res == RES_MOVE) {
            quietSteps++;
        }
        p.moved = true;

        if (q != null && q.type == JUNQI) {
            result = (side == RED) ? 1 : 2;          // 扛旗
        } else if (quietSteps >= DRAW_STEPS) {
            result = 3;
        } else if (legalMoves(opponent(side)).isEmpty()) {
            result = (side == RED) ? 1 : 2;          // 对方无棋可走
        }

        if (result == 0) {
            sideToMove = opponent(side);
        } else {
            setupPhase = false;
        }
        return res;
    }

    public static int opponent(int side) {
        return side == RED ? BLACK : RED;
    }

    /**
     * 战斗判定（攻方视角）：>0 攻方胜，<0 守方胜，0 同归于尽。
     * 军衔高者胜；同级同归于尽；炸弹与任何子同归于尽；
     * 非工兵撞地雷 → 攻方亡、雷保留；工兵撞地雷 → 雷消、工兵胜。
     */
    static int fight(int attacker, int defender) {
        if (attacker == ZHADAN || defender == ZHADAN) {
            return 0;
        }
        if (defender == DILEI) {
            return attacker == GONGBING ? 1 : -1;
        }
        if (attacker == DILEI) {
            return -1;
        }
        if (attacker > defender) {
            return 1;
        }
        if (attacker < defender) {
            return -1;
        }
        return 0;
    }

    /** 调试用。 */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Pc p = cell[idx(r, c)];
                sb.append(p == null ? "··" : (p.side == RED ? "R" : "B") + Integer.toString(p.type));
                sb.append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
