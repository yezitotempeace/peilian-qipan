/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 残局随机生成器（本地生成，不需要联网）。
 *
 * <p>做法是「构造式出题」：先按难度摆出一个<strong>必然成立</strong>的胜形，
 * 再随机化位置、方向、镜像和周围的干扰子。这样生成既快，又保证每道题一定有解。</p>
 *
 * <p>围棋残局会额外做<strong>合法性校验</strong>（每个棋组都必须至少有一口气），
 * 因为不合法的局面喂给引擎会让它崩溃。</p>
 */
public final class EndgameGen {

    public static final int GAME_GOMOKU = 0;
    public static final int GAME_GO = 1;
    public static final int GAME_XIANGQI = 2;

    public static final String[] GAME_NAMES = {"五子棋", "围棋", "象　棋"};
    public static final String[] DIFF_NAMES = {"弱", "中", "强"};

    private EndgameGen() {
    }

    /** 残局题：一套初始局面 + 目标说明。 */
    public static final class Puzzle {
        public final int game, diff;
        public final String title, goal;
        /** 五子棋 / 围棋：{row, col, color}。 */
        public final int[][] stones;
        /** 象棋：{row, col, color, type}。 */
        public final int[][] pieces;
        public final int size;
        public final long seed;

        Puzzle(int game, int diff, String title, String goal, int size,
               int[][] stones, int[][] pieces, long seed) {
            this.game = game;
            this.diff = diff;
            this.title = title;
            this.goal = goal;
            this.size = size;
            this.stones = stones;
            this.pieces = pieces;
            this.seed = seed;
        }
    }

    /** 随机生成一道残局。 */
    public static Puzzle generate(int game, int diff, long seed) {
        Random rnd = new Random(seed ^ (game * 7919L) ^ (diff * 104729L));
        if (game == GAME_GOMOKU) {
            return genGomoku(diff, rnd, seed);
        }
        if (game == GAME_XIANGQI) {
            return genXiangqi(diff, rnd, seed);
        }
        return genGo(diff, rnd, seed);
    }

    // ================================================================ 五子棋

    private static Puzzle genGomoku(int diff, Random rnd, long seed) {
        int n = 15;
        int r = 4 + rnd.nextInt(7);
        int c = 4 + rnd.nextInt(7);
        List<int[]> stones = new ArrayList<int[]>();
        List<int[]> protect = new ArrayList<int[]>();
        String title, goal;

        if (diff == 0) {
            for (int i = 1; i <= 4; i++) {
                stones.add(new int[]{r, c + i, GomokuBoard.BLACK});
            }
            protect.add(new int[]{r, c});
            protect.add(new int[]{r, c + 5});
            title = "一手连珠";
            goal = "黑先。四子连排、两头都空，任选一头补上就是五连。";
        } else if (diff == 1) {
            for (int i = 1; i <= 3; i++) {
                stones.add(new int[]{r, c + i, GomokuBoard.BLACK});
            }
            protect.add(new int[]{r, c});
            protect.add(new int[]{r, c + 4});
            protect.add(new int[]{r, c - 1});
            title = "活三成活四";
            goal = "黑先。把活三补成活四——两头都空，对方堵一头你走另一头，必胜。";
        } else {
            stones.add(new int[]{r, c - 1, GomokuBoard.BLACK});
            stones.add(new int[]{r, c + 1, GomokuBoard.BLACK});
            stones.add(new int[]{r - 1, c, GomokuBoard.BLACK});
            stones.add(new int[]{r + 1, c, GomokuBoard.BLACK});
            protect.add(new int[]{r, c});
            protect.add(new int[]{r, c - 2});
            protect.add(new int[]{r, c + 2});
            protect.add(new int[]{r - 2, c});
            protect.add(new int[]{r + 2, c});
            title = "双活三";
            goal = "黑先。在十字的中心落一子，同时做出横、竖两个活三——对方只能堵一个，另一个就成活四。";
        }

        int sym = rnd.nextInt(8);
        List<int[]> mapped = applySym(stones, protect, sym, n, rnd, true);
        int[][] arr = toArr(mapped);
        return new Puzzle(GAME_GOMOKU, diff, title, goal, n, arr, null, seed);
    }

    // ================================================================ 围棋

    private static Puzzle genGo(int diff, Random rnd, long seed) {
        int n = 9;
        String title, goal;
        int[][] whites, blacks;

        if (diff == 0) {
            whites = new int[][]{{4, 4}, {5, 4}};
            blacks = new int[][]{{3, 4}, {4, 3}, {4, 5}, {5, 3}, {5, 5}};
            title = "一手提子";
            goal = "黑先。这块白棋只剩最后一口气，直接闭气把它提掉。";
        } else if (diff == 1) {
            whites = new int[][]{{4, 4}, {4, 5}, {5, 4}, {5, 5}};
            blacks = new int[][]{{3, 4}, {3, 5}, {4, 3}, {4, 6}, {5, 3}, {5, 6}, {6, 4}};
            title = "全歼四子";
            goal = "黑先。这块白棋被围得只剩一口气，闭气提掉它，白赚四颗子。";
        } else {
            whites = new int[][]{{4, 4}, {4, 5}, {4, 6}, {5, 4}, {5, 6}};
            blacks = new int[][]{{3, 4}, {3, 5}, {3, 6}, {3, 7}, {4, 3}, {4, 8},
                                 {5, 3}, {5, 7}, {6, 4}, {6, 6}};
            title = "点要害";
            goal = "黑先。这块白棋还有两口气，但共用一个要害——先点在要害上，它就再也逃不掉，随后整块提掉。";
        }

        // 多试几次，直到摆出「合法」的局面（每个棋组都至少有一口气），
        // 不合法的局面喂给引擎会让它崩，所以这里必须校验。
        for (int attempt = 0; attempt < 60; attempt++) {
            List<int[]> stones = new ArrayList<int[]>();
            for (int[] p : whites) {
                stones.add(new int[]{p[0], p[1], Board.WHITE});
            }
            for (int[] p : blacks) {
                stones.add(new int[]{p[0], p[1], Board.BLACK});
            }
            // 随机镜像 / 旋转
            int sym = rnd.nextInt(8);
            List<int[]> mapped = new ArrayList<int[]>();
            for (int[] s : stones) {
                int[] q = sym8(s[0], s[1], sym, n);
                mapped.add(new int[]{q[0], q[1], s[2]});
            }
            // 周围再摆几颗子（只摆合法的）
            boolean[][] used = new boolean[n][n];
            for (int[] s : mapped) {
                used[s[0]][s[1]] = true;
            }
            Board probe = new Board(n, 7.5);
            for (int[] s : mapped) {
                probe.grid[probe.idx(s[0], s[1])] = s[2];
            }
            int extra = 4 + rnd.nextInt(4);
            int tries = 0;
            while (extra > 0 && tries < 120) {
                tries++;
                int rr = rnd.nextInt(n), cc = rnd.nextInt(n);
                if (used[rr][cc] || probe.grid[probe.idx(rr, cc)] != Board.EMPTY) {
                    continue;
                }
                int color = rnd.nextBoolean() ? Board.BLACK : Board.WHITE;
                if (!probe.isLegal(color, probe.idx(rr, cc))) {
                    continue;   // 会形成无气子 / 自杀 ⇒ 跳过，保证局面合法
                }
                probe.grid[probe.idx(rr, cc)] = color;
                used[rr][cc] = true;
                mapped.add(new int[]{rr, cc, color});
                extra--;
            }
            if (!legalPosition(probe)) {
                continue;   // 极少数情况下仍不合法 ⇒ 重摆
            }
            int[][] arr = toArr(mapped);
            return new Puzzle(GAME_GO, diff, title, goal, n, arr, null, seed);
        }
        // 兜底：不走随机干扰子，只摆核心块（一定合法）
        List<int[]> stones = new ArrayList<int[]>();
        for (int[] p : whites) {
            stones.add(new int[]{p[0], p[1], Board.WHITE});
        }
        for (int[] p : blacks) {
            stones.add(new int[]{p[0], p[1], Board.BLACK});
        }
        int[][] arr = toArr(stones);
        return new Puzzle(GAME_GO, diff, title, goal, n, arr, null, seed);
    }

    /** 校验局面合法：任何一颗子的棋组都必须至少有一口气（无气子不该出现在盘面上）。 */
    private static boolean legalPosition(Board b) {
        boolean[] seen = new boolean[b.grid.length];
        for (int p = 0; p < b.grid.length; p++) {
            if (b.grid[p] == Board.EMPTY || seen[p]) {
                continue;
            }
            Board.Group g = b.group(p);
            for (int s : g.stones) {
                seen[s] = true;
            }
            if (g.liberties.length == 0) {
                return false;
            }
        }
        return true;
    }

    // ================================================================ 象棋

    /** 已验证的将杀步数：{红车1, 红车2, 黑士个数}。 */
    private static final int[][][] XQ_SPOTS = {
            {{1, 0}, {1, 8}, {1}},
            {{1, 0}, {3, 8}, {1}},
            {{1, 8}, {3, 0}, {1}},
            {{1, 0}, {2, 8}, {1}},
            {{3, 0}, {3, 8}, {1}},
            {{3, 0}, {4, 8}, {1}},
            {{4, 0}, {4, 8}, {1}},
            {{3, 8}, {4, 0}, {1}},
            {{3, 0}, {4, 0}, {1}},
            {{3, 0}, {5, 0}, {1}},
            {{4, 0}, {5, 0}, {1}},
            {{3, 0}, {6, 0}, {1}},
            {{3, 0}, {3, 8}, {2}},
            {{3, 0}, {4, 8}, {2}},
            {{3, 8}, {4, 0}, {2}},
    };

    private static Puzzle genXiangqi(int diff, Random rnd, long seed) {
        List<int[][]> pool = new ArrayList<int[][]>();
        int want = diff + 1;
        for (int[][] s : XQ_SPOTS) {
            if (s[2][0] == want || (diff == 2 && s[2][0] == 2)) {
                pool.add(s);
            }
        }
        if (pool.isEmpty()) {
            pool.add(XQ_SPOTS[0]);
        }
        int[][] pick = pool.get(rnd.nextInt(pool.size()));
        boolean mirror = rnd.nextBoolean();

        int r1 = pick[0][0], c1 = pick[0][1], r2 = pick[1][0], c2 = pick[1][1];
        if (mirror) {
            c1 = 8 - c1;
            c2 = 8 - c2;
        }

        List<int[]> pieces = new ArrayList<int[]>();
        pieces.add(new int[]{0, 4, XiangqiBoard.BLACK, XiangqiBoard.K});
        if (pick[2][0] == 1) {
            pieces.add(new int[]{0, 3, XiangqiBoard.BLACK, XiangqiBoard.A});
        } else {
            pieces.add(new int[]{0, 3, XiangqiBoard.BLACK, XiangqiBoard.A});
            pieces.add(new int[]{0, 5, XiangqiBoard.BLACK, XiangqiBoard.A});
        }
        if (rnd.nextBoolean()) {
            pieces.add(new int[]{2, 2, XiangqiBoard.BLACK, XiangqiBoard.P});
        }
        pieces.add(new int[]{9, 4, XiangqiBoard.RED, XiangqiBoard.K});
        pieces.add(new int[]{r1, c1, XiangqiBoard.RED, XiangqiBoard.R});
        pieces.add(new int[]{r2, c2, XiangqiBoard.RED, XiangqiBoard.R});
        pieces.add(new int[]{2, 4, XiangqiBoard.RED, XiangqiBoard.N});

        String[] titles = {"一步将死", "两步将死", "三步将死"};
        String[] goals = {
                "红先。把车沉到黑方底线，将、士、马三面夹击，一步将死黑将。",
                "红先。车还差一步才到位，先调整位置，两步之内把黑将将死。",
                "红先。黑方士护住九宫，要连将三步、一步步封死它的落脚点，最后一举将死。"};
        int[][] arr = toArr(pieces);
        return new Puzzle(GAME_XIANGQI, diff, titles[diff], goals[diff], 0, null, arr, seed);
    }

    // ================================================================ 工具

    private static int[][] toArr(List<int[]> list) {
        int[][] arr = new int[list.size()][];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /** 正方形的 8 种对称（旋转 / 镜像），用来随机化残局方向。 */
    private static int[] sym8(int r, int c, int k, int n) {
        int rr = n - 1 - r;
        int cc = n - 1 - c;
        switch (k % 8) {
            case 0: return new int[]{r, c};
            case 1: return new int[]{c, rr};
            case 2: return new int[]{rr, cc};
            case 3: return new int[]{cc, r};
            case 4: return new int[]{r, cc};
            case 5: return new int[]{rr, c};
            case 6: return new int[]{c, r};
            default: return new int[]{cc, rr};
        }
    }

    /** 摆好核心形后，随机加干扰子（避开关键点），并随机整体对称。 */
    private static List<int[]> applySym(List<int[]> stones, List<int[]> protect,
                                        int sym, int n, Random rnd, boolean addExtra) {
        List<int[]> mapped = new ArrayList<int[]>();
        for (int[] s : stones) {
            int[] q = sym8(s[0], s[1], sym, n);
            mapped.add(new int[]{q[0], q[1], s[2]});
        }
        List<int[]> prot2 = new ArrayList<int[]>();
        for (int[] s : protect) {
            prot2.add(sym8(s[0], s[1], sym, n));
        }
        if (addExtra) {
            boolean[][] used = new boolean[n][n];
            for (int[] s : mapped) {
                used[s[0]][s[1]] = true;
            }
            for (int[] s : prot2) {
                if (s[0] >= 0 && s[0] < n && s[1] >= 0 && s[1] < n) {
                    used[s[0]][s[1]] = true;
                }
            }
            int extra = 5 + rnd.nextInt(4);
            int tries = 0;
            while (extra > 0 && tries < 200) {
                tries++;
                int rr = rnd.nextInt(n), cc = rnd.nextInt(n);
                if (used[rr][cc]) {
                    continue;
                }
                used[rr][cc] = true;
                mapped.add(new int[]{rr, cc, GomokuBoard.WHITE});
                extra--;
            }
        }
        return mapped;
    }
}
