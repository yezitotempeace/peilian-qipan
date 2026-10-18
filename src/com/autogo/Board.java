package com.autogo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 围棋规则引擎 —— autogo/board.py 的 Java 移植，行为严格对齐：
 * <ul>
 *   <li>默认只判简单劫（不判位置超级劫），避免误杀引擎给出的合法手</li>
 *   <li>数子按中国规则：己方棋子 + 只被己方围住的空点，白方加贴目</li>
 *   <li>内部一维索引 idx = row * size + col，row = 0 在棋盘顶部（与 SGF 一致）</li>
 * </ul>
 */
public class Board {
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

    /** GTP 列名，按惯例跳过 I。 */
    public static final String GTP_COLS = "ABCDEFGHJKLMNOPQRSTUVWXYZ";

    public static final int PASS = -1;

    public final int size;
    public final double komi;
    public int[] grid;
    public int koPoint = PASS;
    /** 落子历史，每项为 {color, point}，point == PASS 表示虚手。 */
    public final List<int[]> moves = new ArrayList<>();
    /** captures[BLACK] = 黑方提掉的白子数。 */
    public final int[] captures = new int[3];

    private int[][] neighbors;

    public Board(int size, double komi) {
        if (size < 2 || size > 25) throw new IllegalArgumentException("棋盘边长需在 2..25 之间");
        this.size = size;
        this.komi = komi;
        this.grid = new int[size * size];
        buildNeighbors();
    }

    private void buildNeighbors() {
        neighbors = new int[grid.length][];
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int idx = r * size + c;
                int[] nb = new int[4];
                int k = 0;
                if (r > 0) nb[k++] = idx - size;
                if (r < size - 1) nb[k++] = idx + size;
                if (c > 0) nb[k++] = idx - 1;
                if (c < size - 1) nb[k++] = idx + 1;
                neighbors[idx] = java.util.Arrays.copyOf(nb, k);
            }
        }
    }

    public static int opponent(int color) {
        return color == BLACK ? WHITE : BLACK;
    }

    public int idx(int row, int col) {
        return row * size + col;
    }

    public int row(int point) {
        return point / size;
    }

    public int col(int point) {
        return point % size;
    }

    public int at(int point) {
        return grid[point];
    }

    // ------------------------------------------------------------------
    // 棋块与气
    // ------------------------------------------------------------------

    public static final class Group {
        public final int[] stones;
        public final int[] liberties;

        Group(int[] s, int[] l) {
            stones = s;
            liberties = l;
        }
    }

    public Group group(int point) {
        int color = grid[point];
        if (color == EMPTY) throw new IllegalArgumentException("空点没有棋块");
        boolean[] inStones = new boolean[grid.length];
        boolean[] inLibs = new boolean[grid.length];
        List<Integer> stones = new ArrayList<>();
        List<Integer> libs = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(point);
        inStones[point] = true;
        while (!stack.isEmpty()) {
            int cur = stack.pop();
            stones.add(cur);
            for (int nb : neighbors[cur]) {
                int v = grid[nb];
                if (v == EMPTY) {
                    if (!inLibs[nb]) {
                        inLibs[nb] = true;
                        libs.add(nb);
                    }
                } else if (v == color && !inStones[nb]) {
                    inStones[nb] = true;
                    stack.push(nb);
                }
            }
        }
        return new Group(toArray(stones), toArray(libs));
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    // ------------------------------------------------------------------
    // 合法性
    // ------------------------------------------------------------------

    /** 返回非法原因；合法返回 null。point == PASS 表示虚手，总是合法。不改变棋盘状态。 */
    public String illegalReason(int color, int point) {
        if (point == PASS) return null;
        if (point < 0 || point >= grid.length) return "坐标越界";
        if (grid[point] != EMPTY) return "该点已有子";
        if (point == koPoint) return "劫争禁着点";

        int[] backup = grid.clone();
        int savedKo = koPoint;
        try {
            int[] captured = place(color, point);
            if (captured.length == 0 && group(point).liberties.length == 0) return "自杀手";
            return null;
        } finally {
            grid = backup;
            koPoint = savedKo;
        }
    }

    public boolean isLegal(int color, int point) {
        return illegalReason(color, point) == null;
    }

    // ------------------------------------------------------------------
    // 落子
    // ------------------------------------------------------------------

    /**
     * 在 point 落子并提子，更新劫点。返回被提掉的棋子索引。
     * 注意：先把所有被提棋块找齐再统一清除，而不是边找边清 —— 否则先被清掉的
     * 棋块会让相邻敌块凭空多出气，提子判定就错了（与 board.py 一致）。
     */
    private int[] place(int color, int point) {
        grid[point] = color;
        int opp = opponent(color);
        List<Integer> captured = new ArrayList<>();
        boolean[] seen = new boolean[grid.length];
        for (int nb : neighbors[point]) {
            if (grid[nb] != opp || seen[nb]) continue;
            Group g = group(nb);
            for (int s : g.stones) seen[s] = true;
            if (g.liberties.length == 0) {
                for (int s : g.stones) captured.add(s);
            }
        }
        for (int s : captured) grid[s] = EMPTY;

        // 简单劫：恰好提一子，且己方落下的这颗子自成一块且只有一气
        Group own = group(point);
        if (captured.size() == 1 && own.stones.length == 1 && own.liberties.length == 1) {
            koPoint = captured.get(0);
        } else {
            koPoint = PASS;
        }
        return toArray(captured);
    }

    /** 落子。非法时抛 IllegalMove。返回被提掉的棋子索引。 */
    public int[] play(int color, int point) {
        String reason = illegalReason(color, point);
        if (reason != null) {
            throw new IllegalMove(color + " 落子 " + pointToGtp(point, size) + " 非法：" + reason);
        }
        if (point == PASS) {
            moves.add(new int[]{color, PASS});
            koPoint = PASS;
            return new int[0];
        }
        int[] captured = place(color, point);
        captures[color] += captured.length;
        moves.add(new int[]{color, point});
        return captured;
    }

    public int[] playGtp(int color, String vertex) {
        return play(color, gtpToPoint(vertex, size));
    }

    // ------------------------------------------------------------------
    // 数子
    // ------------------------------------------------------------------

    /** 中国规则数子，返回 {黑, 白 + 贴目}。 */
    public double[] scoreArea() {
        double black = 0, white = 0;
        for (int v : grid) {
            if (v == BLACK) black++;
            else if (v == WHITE) white++;
        }
        boolean[] visited = new boolean[grid.length];
        for (int start = 0; start < grid.length; start++) {
            if (grid[start] != EMPTY || visited[start]) continue;
            int count = 0;
            boolean touchesBlack = false, touchesWhite = false;
            Deque<Integer> stack = new ArrayDeque<>();
            stack.push(start);
            visited[start] = true;
            while (!stack.isEmpty()) {
                int cur = stack.pop();
                count++;
                for (int nb : neighbors[cur]) {
                    int v = grid[nb];
                    if (v == EMPTY) {
                        if (!visited[nb]) {
                            visited[nb] = true;
                            stack.push(nb);
                        }
                    } else if (v == BLACK) touchesBlack = true;
                    else touchesWhite = true;
                }
            }
            if (touchesBlack && !touchesWhite) black += count;
            else if (touchesWhite && !touchesBlack) white += count;
        }
        return new double[]{black, white + komi};
    }

    /** 返回形如 "B+3.5" / "W+2.0" / "Draw" 的结果串。 */
    public String resultString() {
        double[] s = scoreArea();
        double diff = s[0] - s[1];
        if (Math.abs(diff) < 1e-9) return "Draw";
        return (diff > 0 ? "B+" : "W+") + String.format(java.util.Locale.US, "%.1f", Math.abs(diff));
    }

    // ------------------------------------------------------------------
    // 坐标转换
    // ------------------------------------------------------------------

    /** "Q16" -> 内部索引；"pass"/"" -> PASS。 */
    public static int gtpToPoint(String vertex, int size) {
        String v = vertex.trim().toUpperCase(java.util.Locale.US);
        if (v.isEmpty() || v.equals("PASS")) return PASS;
        char colChar = v.charAt(0);
        int col = GTP_COLS.indexOf(colChar);
        if (col < 0 || col >= size) throw new IllegalArgumentException("GTP 列越界: " + vertex);
        int rowNum;
        try {
            rowNum = Integer.parseInt(v.substring(1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法解析 GTP 坐标: " + vertex);
        }
        if (rowNum < 1 || rowNum > size) throw new IllegalArgumentException("GTP 行号越界: " + vertex);
        return (size - rowNum) * size + col;
    }

    /** 内部索引 -> "Q16"；PASS -> "pass"。 */
    public static String pointToGtp(int point, int size) {
        if (point == PASS) return "pass";
        int row = point / size, col = point % size;
        return GTP_COLS.charAt(col) + String.valueOf(size - row);
    }

    /** SGF 坐标 "aa"（左上角）-> 内部索引。 */
    public static int sgfToPoint(String text, int size) {
        String t = text.trim();
        if (t.isEmpty() || (t.equals("tt") && size <= 19)) return PASS;
        if (t.length() != 2) throw new IllegalArgumentException("无法解析 SGF 坐标: " + text);
        int row = Character.toLowerCase(t.charAt(0)) - 'a';
        int col = Character.toLowerCase(t.charAt(1)) - 'a';
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new IllegalArgumentException("SGF 坐标越界: " + text);
        }
        return row * size + col;
    }

    public static String pointToSgf(int point, int size) {
        if (point == PASS) return "";
        return String.valueOf((char) ('a' + point / size)) + (char) ('a' + point % size);
    }

    public static String colorToChar(int color) {
        return color == BLACK ? "B" : color == WHITE ? "W" : "?";
    }

    public static int charToColor(String c) {
        String s = c.trim().toUpperCase(java.util.Locale.US);
        if (s.equals("B") || s.equals("BLACK")) return BLACK;
        if (s.equals("W") || s.equals("WHITE")) return WHITE;
        throw new IllegalArgumentException("无法识别的颜色: " + c);
    }

    // ------------------------------------------------------------------

    public Board copy() {
        Board b = new Board(size, komi);
        b.grid = grid.clone();
        b.koPoint = koPoint;
        b.captures[BLACK] = captures[BLACK];
        b.captures[WHITE] = captures[WHITE];
        for (int[] m : moves) b.moves.add(new int[]{m[0], m[1]});
        return b;
    }

    /** 非法落子。 */
    public static class IllegalMove extends RuntimeException {
        public IllegalMove(String msg) {
            super(msg);
        }
    }
}
