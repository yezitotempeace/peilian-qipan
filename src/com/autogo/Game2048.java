package com.autogo;

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
        lastSpawn = -1;
        boolean moved = false;
        int gained = 0;
        if (dir == LEFT || dir == RIGHT) {
            for (int r = 0; r < SIZE; r++) {
                int[] line = new int[SIZE];
                for (int c = 0; c < SIZE; c++) {
                    line[c] = dir == LEFT ? at(r, c) : at(r, SIZE - 1 - c);
                }
                int[] out = new int[SIZE];
                boolean[] mg = new boolean[SIZE];
                int n = slide(line, out, mg);
                for (int c = 0; c < SIZE; c++) {
                    int value = out[c];
                    int target = dir == LEFT ? c : SIZE - 1 - c;
                    int idx = r * SIZE + target;
                    if (cells[idx] != value) {
                        moved = true;
                    }
                    cells[idx] = value;
                    if (mg[c]) {
                        merged[idx] = true;
                        gained += value;
                    }
                }
            }
        } else {
            for (int c = 0; c < SIZE; c++) {
                int[] line = new int[SIZE];
                for (int r = 0; r < SIZE; r++) {
                    line[r] = dir == UP ? at(r, c) : at(SIZE - 1 - r, c);
                }
                int[] out = new int[SIZE];
                boolean[] mg = new boolean[SIZE];
                slide(line, out, mg);
                for (int r = 0; r < SIZE; r++) {
                    int value = out[r];
                    int target = dir == UP ? r : SIZE - 1 - r;
                    int idx = target * SIZE + c;
                    if (cells[idx] != value) {
                        moved = true;
                    }
                    cells[idx] = value;
                    if (mg[r]) {
                        merged[idx] = true;
                        gained += value;
                    }
                }
            }
        }
        if (!moved) {
            return false;
        }
        score += gained;
        spawn();
        if (score > 0) {
            // 分数不参与胜负，只是记分
        }
        for (int v : cells) {
            if (v >= WIN) {
                won = true;
            }
        }
        over = !canMove();
        return true;
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
