/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 2048 的纯逻辑：4×4、四方向滑动、同值合并一次、合并得分、每次有效移动生成一个 2（90%）或 4（10%）。
 *
 * <p>规则按原版：达到 2048 算赢，但可以继续玩；没有空格且四方向都动不了才结束。
 * 这个类不碰 UI、不碰彩蛋 —— 2048 没有彩蛋。</p>
 */
public class Game2048 {

    public static final int SIZE = 4;
    public static final int WIN = 2048;
    public static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3;

    private final int[] cells = new int[SIZE * SIZE];
    private Random rnd;
    private int score;
    private boolean won;
    private boolean over;
    private int lastSpawn = -1;
    private final boolean[] merged = new boolean[SIZE * SIZE];
    private final List<MoveAnim> anims = new ArrayList<MoveAnim>();

    /** 一步里每个原始块的去向，供 UI 做滑动动画。from/to 是 0..15 索引。 */
    public static class MoveAnim {
        public final int from, to, value;
        public final boolean merged;
        MoveAnim(int from, int to, int value, boolean merged) {
            this.from = from; this.to = to; this.value = value; this.merged = merged;
        }
    }

    public Game2048() {
        this(new Random());
    }

    public Game2048(long seed) {
        this(new Random(seed));
    }

    public Game2048(Random r) {
        rnd = r == null ? new Random() : r;
        reset();
    }

    public void reset() {
        java.util.Arrays.fill(cells, 0);
        java.util.Arrays.fill(merged, false);
        score = 0;
        won = false;
        over = false;
        lastSpawn = -1;
        anims.clear();
        spawn();
        spawn();
    }

    public int at(int row, int col) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) {
            return 0;
        }
        return cells[row * SIZE + col];
    }

    public int score() { return score; }
    public boolean won() { return won; }
    public boolean over() { return over; }
    public int lastSpawnIndex() { return lastSpawn; }
    public boolean mergedAt(int index) { return index >= 0 && index < merged.length && merged[index]; }
    public List<MoveAnim> lastAnims() { return anims; }

    public int[] snapshot() { return cells.clone(); }

    /** 读档 / 测试用：直接放一个局面，不生成新子。 */
    public void load(int[] values, int score) {
        java.util.Arrays.fill(cells, 0);
        if (values != null) {
            System.arraycopy(values, 0, cells, 0, Math.min(values.length, cells.length));
        }
        this.score = Math.max(0, score);
        won = false;
        over = false;
        lastSpawn = -1;
        anims.clear();
        java.util.Arrays.fill(merged, false);
        for (int v : cells) {
            if (v >= WIN) {
                won = true;
            }
        }
        over = !canMove();
    }

    /** 返回这次是否真的动了；没动就不生成新子、不加分。 */
    public boolean move(int dir) {
        if (over) {
            return false;
        }
        java.util.Arrays.fill(merged, false);
        anims.clear();
        lastSpawn = -1;
        int[] before = cells.clone();
        int gained = 0;
        for (int line = 0; line < SIZE; line++) {
            int count = 0;
            int[] vals = new int[SIZE];
            int[] froms = new int[SIZE];
            for (int pos = 0; pos < SIZE; pos++) {
                int idx = cellIndex(dir, line, pos);
                if (cells[idx] != 0) {
                    vals[count] = cells[idx];
                    froms[count] = idx;
                    count++;
                }
            }
            List<Out> outs = new ArrayList<Out>();
            for (int i = 0; i < count; i++) {
                Out last = outs.isEmpty() ? null : outs.get(outs.size() - 1);
                if (last != null && last.value == vals[i] && !last.merged) {
                    last.value *= 2;
                    last.merged = true;
                    last.srcVals.add(Integer.valueOf(vals[i]));
                    last.srcIdx.add(Integer.valueOf(froms[i]));
                    gained += last.value;
                } else {
                    Out o = new Out(vals[i]);
                    o.srcVals.add(Integer.valueOf(vals[i]));
                    o.srcIdx.add(Integer.valueOf(froms[i]));
                    outs.add(o);
                }
            }
            for (int pos = 0; pos < SIZE; pos++) {
                cells[cellIndex(dir, line, pos)] = 0;
            }
            for (int oi = 0; oi < outs.size(); oi++) {
                Out o = outs.get(oi);
                int dest = cellIndex(dir, line, oi);
                cells[dest] = o.value;
                if (o.merged) {
                    merged[dest] = true;
                }
                for (int k = 0; k < o.srcIdx.size(); k++) {
                    anims.add(new MoveAnim(o.srcIdx.get(k).intValue(), dest,
                            o.srcVals.get(k).intValue(), o.merged));
                }
            }
        }
        if (java.util.Arrays.equals(before, cells)) {
            anims.clear();
            return false;
        }
        score += gained;
        spawn();
        for (int v : cells) {
            if (v >= WIN) {
                won = true;
            }
        }
        over = !canMove();
        return true;
    }

    private static int cellIndex(int dir, int line, int pos) {
        if (dir == LEFT) return line * SIZE + pos;
        if (dir == RIGHT) return line * SIZE + (SIZE - 1 - pos);
        if (dir == UP) return pos * SIZE + line;
        return (SIZE - 1 - pos) * SIZE + line;
    }

    private static class Out {
        int value;
        boolean merged;
        final List<Integer> srcVals = new ArrayList<Integer>();
        final List<Integer> srcIdx = new ArrayList<Integer>();
        Out(int value) { this.value = value; }
    }

    /**
     * 一行/一列向索引 0 方向压缩并合并；返回有效块数。mg 标出合并后块的位置（0 起）。
     */
    private static int slide(int[] line, int[] out, boolean[] mg) {
        int n = 0;
        for (int i = 0; i < SIZE; i++) {
            if (line[i] == 0) {
                continue;
            }
            if (n > 0 && out[n - 1] == line[i] && !mg[n - 1]) {
                out[n - 1] *= 2;
                mg[n - 1] = true;
            } else {
                out[n++] = line[i];
            }
        }
        return n;
    }

    public boolean canMove() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int v = at(r, c);
                if (v == 0) {
                    return true;
                }
                if (c + 1 < SIZE && at(r, c + 1) == v) {
                    return true;
                }
                if (r + 1 < SIZE && at(r + 1, c) == v) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 有效移动后随机空格生成 2（90%）或 4（10%）。 */
    private void spawn() {
        int empty = 0;
        for (int v : cells) {
            if (v == 0) {
                empty++;
            }
        }
        if (empty == 0) {
            lastSpawn = -1;
            return;
        }
        int pick = rnd.nextInt(empty);
        int idx = -1;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == 0) {
                if (pick == 0) {
                    idx = i;
                    break;
                }
                pick--;
            }
        }
        if (idx < 0) {
            lastSpawn = -1;
            return;
        }
        cells[idx] = rnd.nextInt(10) == 0 ? 4 : 2;
        lastSpawn = idx;
    }
}
