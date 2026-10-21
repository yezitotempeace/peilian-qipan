/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Random;

/**
 * 数独（9×9）的纯逻辑：生成、求解、唯一解校验、冲突判定、笔记、撤销。
 *
 * <p>规则按通行定义：每行、每列、每个 3×3 宫里 1–9 各出现且只出现一次；
 * 行 / 列 / 宫 <b>三个方向都不能重复</b>。玩家填错只是标红（不拦），见 {@link #conflictAt}。</p>
 *
 * <p>出题走标准「挖洞法」：随机回溯出一个终盘 → 随机顺序逐个挖空 →
 * 每挖一个都用求解器数解的个数（必须恰好 1），不唯一就填回去。</p>
 *
 * <p>难度三档，用户要求：简单 / 普通照常，困难要「特别特别难」。
 * 难度不只看提示数（提示数多 ≠ 简单），而是看<b>解题需要的试探深度</b>
 * {@link Stats#guesses}：0 = 全程裸单/隐性单就能解；≥2 = 必须反复试填。</p>
 */
public final class SudokuGame {

    public static final int N = 9;
    public static final int CELLS = 81;

    public static final int EASY = 0, NORMAL = 1, HARD = 2;
    public static final String[] DIFF_NAMES = {"简单", "普通", "困难"};

    /** 每格所属的宫（0..8）。 */
    private static final int[] BOX = new int[CELLS];

    static {
        for (int i = 0; i < CELLS; i++) {
            BOX[i] = (i / N / 3) * 3 + (i % N) / 3;
        }
    }

    private final int[] solution = new int[CELLS];
    private final int[] givens = new int[CELLS];
    private final int[] board = new int[CELLS];
    /** 每格候选数位图：bit d（1..9）代表数字 d。 */
    private final int[] notes = new int[CELLS];
    private final Deque<int[]> undo = new ArrayDeque<int[]>();

    private int difficulty = NORMAL;
    private long seed;
    private int hints;

    public SudokuGame() {
        this(NORMAL, System.nanoTime());
    }

    public SudokuGame(int diff, long seed) {
        newGame(diff, seed);
    }

    /** 内部用：只建空对象，不生成题（读档用，省掉一次白生成）。 */
    private SudokuGame(boolean empty) {
    }

    /** 从存档恢复；三个数组都可以传 null。 */
    public static SudokuGame restore(int[] givensIn, int[] boardIn, int[] notesIn, int diff, long seed) {
        SudokuGame g = new SudokuGame(true);
        g.load(givensIn, boardIn, notesIn, diff, seed);
        return g;
    }

    // ───────────────────────── 读取状态 ─────────────────────────

    public int difficulty() { return difficulty; }

    public long seed() { return seed; }

    public int hintsUsed() { return hints; }

    public int at(int r, int c) { return inside(r, c) ? board[r * N + c] : 0; }

    public int solutionAt(int r, int c) { return inside(r, c) ? solution[r * N + c] : 0; }

    public boolean isGiven(int r, int c) { return inside(r, c) && givens[r * N + c] != 0; }

    public int noteMask(int r, int c) { return inside(r, c) ? notes[r * N + c] : 0; }

    public int[] snapshot() { return board.clone(); }

    public int[] givenSnapshot() { return givens.clone(); }

    public int[] noteSnapshot() { return notes.clone(); }

    /** 题面给出的数字个数。 */
    public int clues() {
        int n = 0;
        for (int i = 0; i < CELLS; i++) {
            if (givens[i] != 0) {
                n++;
            }
        }
        return n;
    }

    /** 还空着的格子数。 */
    public int remaining() {
        int n = 0;
        for (int i = 0; i < CELLS; i++) {
            if (board[i] == 0) {
                n++;
            }
        }
        return n;
    }

    public boolean canUndo() { return !undo.isEmpty(); }

    // ───────────────────────── 填数 ─────────────────────────

    /**
     * 玩家填数（0 = 清除）。
     *
     * @return false 表示这个格是题面的数字、不能改
     */
    public boolean set(int r, int c, int v) {
        if (!inside(r, c) || v < 0 || v > 9) {
            return false;
        }
        int i = r * N + c;
        if (givens[i] != 0) {
            return false;
        }
        pushUndo(i);
        board[i] = v;
        if (v != 0) {
            notes[i] = 0;
        }
        return true;
    }

    /** 切换某格的一个候选数（笔记）。已填的格和题面格不能记。 */
    public boolean toggleNote(int r, int c, int d) {
        if (!inside(r, c) || d < 1 || d > 9) {
            return false;
        }
        int i = r * N + c;
        if (givens[i] != 0 || board[i] != 0) {
            return false;
        }
        notes[i] ^= 1 << d;
        return true;
    }

    /** 撤销上一步（填数 / 擦除 / 提示）。 */
    public boolean undo() {
        int[] u = undo.pollLast();
        if (u == null) {
            return false;
        }
        board[u[0]] = u[1];
        notes[u[0]] = u[2];
        return true;
    }

    private void pushUndo(int i) {
        undo.addLast(new int[]{i, board[i], notes[i]});
        if (undo.size() > 512) {
            undo.pollFirst();
        }
    }

    /**
     * 提示：把指定的空格填成正确答案；不给格子就挑第一个空格。
     *
     * @return false 表示没有空格可提示
     */
    public boolean hint(int r, int c) {
        if (!inside(r, c)) {
            return false;
        }
        int i = r * N + c;
        if (board[i] != 0 || solution[i] == 0) {
            return false;
        }
        pushUndo(i);
        board[i] = solution[i];
        notes[i] = 0;
        hints++;
        return true;
    }

    public boolean hint() {
        for (int i = 0; i < CELLS; i++) {
            if (board[i] == 0) {
                return hint(i / N, i % N);
            }
        }
        return false;
    }

    // ───────────────────────── 规则判定 ─────────────────────────

    /**
     * 这一格是否和「同行 / 同列 / 同宫」里的数字重复。
     *
     * <p>这就是数独唯一的填写约束：自己所在的行、列、宫都不能再出现同样的数字。
     * 填错只标红、不拦（按用户要求的行为 A）。</p>
     */
    public boolean conflictAt(int r, int c) {
        int v = at(r, c);
        if (v == 0) {
            return false;
        }
        for (int k = 0; k < N; k++) {
            if (k != c && at(r, k) == v) {
                return true;
            }
            if (k != r && at(k, c) == v) {
                return true;
            }
        }
        int br = (r / 3) * 3;
        int bc = (c / 3) * 3;
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) {
                int rr = br + a;
                int cc = bc + b;
                if ((rr != r || cc != c) && at(rr, cc) == v) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 整盘有没有冲突格。 */
    public boolean hasConflict() {
        for (int i = 0; i < CELLS; i++) {
            if (conflictAt(i / N, i % N)) {
                return true;
            }
        }
        return false;
    }

    /** 冲突格数量。 */
    public int conflictCount() {
        int n = 0;
        for (int i = 0; i < CELLS; i++) {
            if (conflictAt(i / N, i % N)) {
                n++;
            }
        }
        return n;
    }

    /** 填满且无冲突 = 完成。 */
    public boolean solved() {
        for (int i = 0; i < CELLS; i++) {
            if (board[i] == 0) {
                return false;
            }
        }
        return !hasConflict();
    }

    /** 某个空格当前可填的数字位图（1..9）。 */
    public int candidatesAt(int r, int c) {
        if (!inside(r, c)) {
            return 0;
        }
        int i = r * N + c;
        if (board[i] != 0) {
            return 0;
        }
        return candidates(board, i);
    }

    // ───────────────────────── 生成 / 存档 ─────────────────────────

    public void newGame(int diff, long seed) {
        this.difficulty = (diff < EASY || diff > HARD) ? NORMAL : diff;
        this.seed = seed;
        Random rnd = new Random(seed);
        int[] sol = new int[CELLS];
        fillRandom(sol, rnd);
        System.arraycopy(sol, 0, solution, 0, CELLS);
        int[] puz = buildPuzzle(sol, this.difficulty, rnd);
        System.arraycopy(puz, 0, givens, 0, CELLS);
        System.arraycopy(puz, 0, board, 0, CELLS);
        Arrays.fill(notes, 0);
        undo.clear();
        hints = 0;
    }

    /** 读档：题面 + 当前填数 + 笔记 + 难度 + 种子；答案由题面反解。 */
    public void load(int[] givensIn, int[] boardIn, int[] notesIn, int diff, long seed) {
        Arrays.fill(givens, 0);
        Arrays.fill(board, 0);
        Arrays.fill(notes, 0);
        Arrays.fill(solution, 0);
        if (givensIn != null) {
            System.arraycopy(givensIn, 0, givens, 0, Math.min(givensIn.length, CELLS));
        }
        if (boardIn != null) {
            System.arraycopy(boardIn, 0, board, 0, Math.min(boardIn.length, CELLS));
        }
        if (notesIn != null) {
            System.arraycopy(notesIn, 0, notes, 0, Math.min(notesIn.length, CELLS));
        }
        this.difficulty = (diff < EASY || diff > HARD) ? NORMAL : diff;
        this.seed = seed;
        hints = 0;
        undo.clear();
        int[] s = givens.clone();
        if (fillDeterministic(s)) {
            System.arraycopy(s, 0, solution, 0, CELLS);
        }
    }

    // ───────────────────────── 求解器（对外也给测试用） ─────────────────────────

    /** 求解统计：{@link #guesses} 越大越难（0 = 不用试探就能解）。 */
    public static final class Stats {
        /** 成功路径上「必须试探」的次数：0 = 全靠裸单/隐性单。 */
        public int guesses;
        /** 搜索访问的结点数。 */
        public int nodes;
        public boolean solved;
    }

    /** 用「最少候选优先」的回溯解一遍，统计难度画像。 */
    public static Stats analyse(int[] grid) {
        Stats st = new Stats();
        int[] g = grid.clone();
        st.solved = solve(g, 0, st);
        return st;
    }

    private static boolean solve(int[] g, int branches, Stats st) {
        st.nodes++;
        int i = pickCell(g);
        if (i == -2) {
            return false;
        }
        if (i < 0) {
            return true;
        }
        int m = candidates(g, i);
        boolean guess = Integer.bitCount(m) > 1;
        if (guess && branches + 1 > st.guesses) {
            st.guesses = branches + 1;
        }
        for (int d = 1; d <= 9; d++) {
            if ((m & (1 << d)) == 0) {
                continue;
            }
            g[i] = d;
            if (solve(g, guess ? branches + 1 : branches, st)) {
                return true;
            }
            g[i] = 0;
        }
        return false;
    }

    /** 数解的个数，最多数到 limit 个（判唯一解时传 2 就够）。 */
    public static int countSolutions(int[] grid, int limit) {
        return count(grid.clone(), limit);
    }

    private static int count(int[] g, int limit) {
        int i = pickCell(g);
        if (i == -2) {
            return 0;
        }
        if (i < 0) {
            return 1;
        }
        int m = candidates(g, i);
        int total = 0;
        for (int d = 1; d <= 9; d++) {
            if ((m & (1 << d)) == 0) {
                continue;
            }
            g[i] = d;
            total += count(g, limit - total);
            g[i] = 0;
            if (total >= limit) {
                return total;
            }
        }
        return total;
    }

    /** 选候选最少的空格；-1 = 已填满，-2 = 已经矛盾。 */
    private static int pickCell(int[] g) {
        int best = -1;
        int bestCount = 10;
        for (int i = 0; i < CELLS; i++) {
            if (g[i] != 0) {
                continue;
            }
            int n = Integer.bitCount(candidates(g, i));
            if (n == 0) {
                return -2;
            }
            if (n < bestCount) {
                bestCount = n;
                best = i;
                if (n == 1) {
                    break;
                }
            }
        }
        return best;
    }

    private static int candidates(int[] g, int i) {
        int mask = 0x3FE; // bit 1..9
        int r = i / N;
        int c = i % N;
        for (int k = 0; k < N; k++) {
            mask &= ~(1 << g[r * N + k]);
            mask &= ~(1 << g[k * N + c]);
        }
        int br = (r / 3) * 3;
        int bc = (c / 3) * 3;
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) {
                mask &= ~(1 << g[(br + a) * N + bc + b]);
            }
        }
        return mask & 0x3FE;
    }

    private static int countClues(int[] g) {
        int n = 0;
        for (int i = 0; i < CELLS; i++) {
            if (g[i] != 0) {
                n++;
            }
        }
        return n;
    }

    private static boolean fillRandom(int[] g, Random rnd) {
        int i = pickCell(g);
        if (i == -2) {
            return false;
        }
        if (i < 0) {
            return true;
        }
        int[] digits = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
        for (int k = digits.length - 1; k > 0; k--) {
            int j = rnd.nextInt(k + 1);
            int t = digits[k];
            digits[k] = digits[j];
            digits[j] = t;
        }
        for (int k = 0; k < digits.length; k++) {
            int d = digits[k];
            if (!allowed(g, i, d)) {
                continue;
            }
            g[i] = d;
            if (fillRandom(g, rnd)) {
                return true;
            }
            g[i] = 0;
        }
        return false;
    }

    /** 只填 1..9 顺序，用在读档反解（同一题面永远解出同一答案）。 */
    private static boolean fillDeterministic(int[] g) {
        int i = pickCell(g);
        if (i == -2) {
            return false;
        }
        if (i < 0) {
            return true;
        }
        for (int d = 1; d <= 9; d++) {
            if (!allowed(g, i, d)) {
                continue;
            }
            g[i] = d;
            if (fillDeterministic(g)) {
                return true;
            }
            g[i] = 0;
        }
        return false;
    }

    private static boolean allowed(int[] g, int i, int d) {
        int r = i / N;
        int c = i % N;
        for (int k = 0; k < N; k++) {
            if (g[r * N + k] == d || g[k * N + c] == d) {
                return false;
            }
        }
        int br = (r / 3) * 3;
        int bc = (c / 3) * 3;
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) {
                if (g[(br + a) * N + bc + b] == d) {
                    return false;
                }
            }
        }
        return true;
    }

    // ───────────────────────── 挖洞出题 ─────────────────────────

    /**
     * 从终盘挖出题目。
     *
     * <p>简单 / 普通照常（简单保证全程不用试探，普通允许最多 1 次试探）；
     * 困难按用户要求做到「特别特别难」：提示数压到 26 以下，且必须至少 2 次试探才能解。</p>
     */
    private static int[] buildPuzzle(int[] solution, int diff, Random rnd) {
        int attempts = diff == HARD ? 8 : (diff == EASY ? 4 : 2);
        int target = diff == EASY ? 46 : (diff == NORMAL ? 32 : 24);
        int[] best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int a = 0; a < attempts; a++) {
            int[] puz = solution.clone();
            if (a > 0) {
                int[] s2 = new int[CELLS];
                fillRandom(s2, rnd);
                System.arraycopy(s2, 0, puz, 0, CELLS);
            }
            digTo(puz, target, rnd);
            int clues = countClues(puz);
            Stats st = analyse(puz);
            if (diff == EASY) {
                if (st.guesses == 0 && clues >= 40) {
                    return puz;
                }
            } else if (diff == NORMAL) {
                if (st.guesses <= 1 && clues >= 28) {
                    return puz;
                }
            } else {
                if (st.guesses >= 2 && clues <= 26) {
                    return puz;
                }
            }
            // 挑最接近要求的留作兜底：难档优先试探次数多、提示数少
            int score = st.guesses * 100 - clues;
            if (score > bestScore) {
                bestScore = score;
                best = puz;
            }
        }
        return best != null ? best : solution.clone();
    }

    private static void digTo(int[] puz, int targetClues, Random rnd) {
        int[] order = new int[CELLS];
        for (int i = 0; i < CELLS; i++) {
            order[i] = i;
        }
        for (int k = order.length - 1; k > 0; k--) {
            int j = rnd.nextInt(k + 1);
            int t = order[k];
            order[k] = order[j];
            order[j] = t;
        }
        for (int k = 0; k < order.length; k++) {
            if (countClues(puz) <= targetClues) {
                return;
            }
            int i = order[k];
            if (puz[i] == 0) {
                continue;
            }
            int keep = puz[i];
            puz[i] = 0;
            if (countSolutions(puz, 2) != 1) {
                puz[i] = keep;
            }
        }
    }

    private static boolean inside(int r, int c) {
        return r >= 0 && r < N && c >= 0 && c < N;
    }

    /** 存档编码：81 位数字，0 表示空。 */
    public static String encode(int[] grid) {
        StringBuilder sb = new StringBuilder(CELLS);
        for (int i = 0; i < CELLS; i++) {
            int v = grid == null || i >= grid.length ? 0 : grid[i];
            sb.append((char) ('0' + (v < 0 || v > 9 ? 0 : v)));
        }
        return sb.toString();
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** 笔记存档编码：每格 3 位十六进制（位图最大 0x3FE）。 */
    public static String encodeNotes(int[] notes) {
        StringBuilder sb = new StringBuilder(CELLS * 3);
        for (int i = 0; i < CELLS; i++) {
            int v = (notes == null || i >= notes.length ? 0 : notes[i]) & 0x3FF;
            sb.append(HEX[(v >> 8) & 0xF]).append(HEX[(v >> 4) & 0xF]).append(HEX[v & 0xF]);
        }
        return sb.toString();
    }

    /** 解析笔记存档；长度不对返回 null。 */
    public static int[] decodeNotes(String s) {
        if (s == null || s.length() != CELLS * 3) {
            return null;
        }
        int[] out = new int[CELLS];
        for (int i = 0; i < CELLS; i++) {
            int a = hexVal(s.charAt(i * 3));
            int b = hexVal(s.charAt(i * 3 + 1));
            int c = hexVal(s.charAt(i * 3 + 2));
            if (a < 0 || b < 0 || c < 0) {
                return null;
            }
            out[i] = (a << 8) | (b << 4) | c;
        }
        return out;
    }

    private static int hexVal(char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        }
        if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        }
        return -1;
    }

    /** 解析存档；长度不对返回 null。 */
    public static int[] decode(String s) {
        if (s == null || s.length() != CELLS) {
            return null;
        }
        int[] out = new int[CELLS];
        for (int i = 0; i < CELLS; i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') {
                return null;
            }
            out[i] = ch - '0';
        }
        return out;
    }
}
