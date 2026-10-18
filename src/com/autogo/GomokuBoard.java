package com.autogo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * 五子棋规则引擎 —— 纯逻辑，不碰 android.*，因此进得了 run_tests.sh 的 JVM 测试集
 * （tests/GomokuTest.java）。坐标与手顺的约定照抄围棋侧 Board.java，两边看起来是一套东西：
 *
 * <ul>
 *   <li>内部一维索引 idx = row * size + col，row = 0 在棋盘顶部（与 SGF 一致）</li>
 *   <li>EMPTY = 0 / BLACK = 1 / WHITE = 2，PASS = -1</li>
 *   <li>手顺 moves 每项 {color, point}，与围棋侧完全同形</li>
 * </ul>
 *
 * <h2>规则口径</h2>
 * 本项目自己定的口径（README 里另有一份，含边界情形与未覆盖项）：
 * <ol>
 *   <li>五连（5 个或更多连续同色子）即胜。自由规则下长连（6 连以上）也算赢。</li>
 *   <li>黑棋禁手（开关 forbiddenForBlack，默认关）：只对黑生效，白棋没有禁手。
 *     <ul>
 *       <li><b>长连</b>：落子后任一方向连续同色 &ge; 6 &rArr; 禁手（该点不许落）。</li>
 *       <li><b>四四</b>：这一手同时制造出 &ge; 2 个不同的「四子组」&rArr; 禁手。
 *           四子组 = 5 连窗口里的 4 颗黑子 + 1 个空点（再下一子就成五，且这个四子组里含这一手）。
 *           同一个四子组只算一次 —— 所以「活四」（两端都能成五）算<em>一个</em>四，不是四四。</li>
 *       <li><b>三三</b>：这一手同时制造出 &ge; 2 个方向的「活三」&rArr; 禁手。活三的判定用模拟法：
 *           存在一个空点，补上它就成「活四」（四子连续、两端皆空且都在盘内），且这个活四里含这一手。
 *           模拟法天然排除假活三（一端被堵、跳三补不上活四的）。同一方向最多算一个活三
 *           （实战中同一个新子在同一条线上造出两个活三，基本意味着已经成五，早被上面的分支拦掉）。</li>
 *       <li>五连优先：这一手要是正好成五，算赢，不算禁手。</li>
 *     </ul>
 *   </li>
 *   <li>棋盘下满仍未分胜负 &rArr; 和棋。</li>
 * </ol>
 *
 * <h2>性能</h2>
 * 禁手判定只在开关打开、且落子的是黑棋时才会跑；四子组只枚举过这一点的那几条 5 连窗口，
 * 活三只在自己这条线上找补点，所以单次判定是常数级小活，AI 反复调 isLegal 也扛得住。
 */
public class GomokuBoard {

    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;
    /** 五子棋没有虚手；留着是为了和围棋侧同名常量对齐。 */
    public static final int PASS = -1;

    /** 列字母表（与围棋侧同一套：跳过 I，免得和数字 1 混淆）。 */
    public static final String GTP_COLS = "ABCDEFGHJKLMNOPQRSTUVWXYZ";

    /** 四个方向：横、竖、右下、右上。 */
    private static final int[][] DIRS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    public final int size;
    /** 棋盘，一维；0 = 空。测试可以直接写（围棋侧 BoardTest 也是这么构造局面的）。 */
    public int[] grid;
    /** 黑棋是否禁手（连珠规则）。默认关。 */
    public boolean forbiddenForBlack;
    /** 落子历史，每项 {color, point}。 */
    public final List<int[]> moves = new ArrayList<>();
    /** 胜方；EMPTY 表示未分胜负（含和棋）。 */
    public int winner = EMPTY;
    public boolean gameOver = false;
    /** 终局原因，中文，直接可显示。 */
    public String endReason = "";
    /** 获胜的那一串坐标（长连时是整段）；未分胜负为 null。 */
    public int[] winLine = null;

    public GomokuBoard(int size, boolean forbiddenForBlack) {
        if (size < 5) throw new IllegalArgumentException("棋盘至少 5 路，实际 " + size);
        this.size = size;
        this.forbiddenForBlack = forbiddenForBlack;
        this.grid = new int[size * size];
    }

    public static int opponent(int color) {
        if (color == BLACK) return WHITE;
        if (color == WHITE) return BLACK;
        return EMPTY;
    }

    public static boolean inBounds(int size, int row, int col) {
        return row >= 0 && row < size && col >= 0 && col < size;
    }

    /** (row, col) -> 内部索引；越界索引由 illegalReason 拦。 */
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

    public boolean isFull() {
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] == EMPTY) return false;
        }
        return true;
    }

    /** 返回非法原因；合法返回 null。不改变棋盘状态。 */
    public String illegalReason(int color, int point) {
        if (color != BLACK && color != WHITE) return "颜色不合法：" + color;
        if (point < 0 || point >= size * size) return "坐标越界：" + point;
        if (gameOver) return "棋局已结束（" + endReason + "）";
        if (grid[point] != EMPTY) return "该点已有子";
        if (isForbidden(color, row(point), col(point))) {
            return "黑棋禁手（" + forbiddenKind(row(point), col(point)) + "）";
        }
        return null;
    }

    public boolean isLegal(int color, int point) {
        return illegalReason(color, point) == null;
    }

    /** 落子。非法（含黑棋禁手点）时抛 IllegalMove。 */
    public void play(int color, int point) {
        String why = illegalReason(color, point);
        if (why != null) throw new IllegalMove(why);
        grid[point] = color;
        moves.add(new int[]{color, point});
        int r = row(point), c = col(point);
        int run = longestRun(color, r, c);
        if (run >= 5) {
            winner = color;
            gameOver = true;
            winLine = runPoints(color, r, c, run);
            String who = (color == BLACK ? "黑" : "白");
            endReason = who + (run >= 6 ? "长连" : "五连") + "，" + who + "胜";
        } else if (isFull()) {
            winner = EMPTY;
            gameOver = true;
            endReason = "棋盘已满，和棋";
        }
    }

    /**
     * 按手顺重建一副棋（悔棋 / 进程内恢复棋局都用它，别再各写一份重放）。
     *
     * <p>重放一律用「自由规则」建局：老手顺里可能有在禁手关着时走出来的四四 / 三三，
     * 拿现在的开关去重放会被判非法 —— 已经落下的手不该被重新审判。重放完再把开关置回。
     *
     * @param moves  每项 {color, point}（颜色由调用方给，本方法不替你推）
     * @param firstColor 只在调用方给的 moves 里颜色缺失时才需要 —— 这里按 moves 里的颜色走
     */
    public static GomokuBoard replay(List<int[]> moves, int size, boolean forbiddenForBlack) {
        GomokuBoard b = new GomokuBoard(size, false);
        for (int i = 0; i < moves.size(); i++) {
            int[] m = moves.get(i);
            b.play(m[0], m[1]);
        }
        b.forbiddenForBlack = forbiddenForBlack;
        return b;
    }

    /** 认输：对方胜。棋局已结束时抛 IllegalMove。 */
    public void resign(int color) {
        if (color != BLACK && color != WHITE) throw new IllegalMove("颜色不合法：" + color);
        if (gameOver) throw new IllegalMove("棋局已结束（" + endReason + "）");
        winner = opponent(color);
        gameOver = true;
        endReason = (color == BLACK ? "黑" : "白") + "认输，" + (winner == BLACK ? "黑" : "白") + "胜";
    }

    /** 该方向的连续同色段；返回 {起点行, 起点列, 长度}。调用前假定 (r, c) 处已有该色子。 */
    public static int[] runSpan(int[] g, int size, int r, int c, int color, int dr, int dc) {
        int sr = r, sc = c;
        while (inBounds(size, sr - dr, sc - dc) && g[(sr - dr) * size + (sc - dc)] == color) {
            sr -= dr;
            sc -= dc;
        }
        int er = r, ec = c;
        while (inBounds(size, er + dr, ec + dc) && g[(er + dr) * size + (ec + dc)] == color) {
            er += dr;
            ec += dc;
        }
        int len = 1, rr = sr, cc = sc;
        while (rr != er || cc != ec) {
            rr += dr;
            cc += dc;
            len++;
        }
        return new int[]{sr, sc, len};
    }

    /** p 是否落在该段里。 */
    private static boolean inSpan(int[] span, int[] dir, int size, int p) {
        int pr = p / size, pc = p % size;
        for (int k = 0; k < span[2]; k++) {
            if (span[0] + k * dir[0] == pr && span[1] + k * dir[1] == pc) return true;
        }
        return false;
    }

    /** 含 (r, c) 在内的最长连续同色子数（四方向取最大）。调用前假定 (r, c) 是该色子。 */
    public int longestRun(int color, int r, int c) {
        return runLengthOn(grid, size, color, r, c);
    }

    /** 同 longestRun，但棋盘由调用方给（AI 搜索用自己的 scratch 棋盘）。 */
    public static int runLengthOn(int[] grid, int size, int color, int r, int c) {
        int best = 0;
        for (int[] d : DIRS) {
            best = Math.max(best, runSpan(grid, size, r, c, color, d[0], d[1])[2]);
        }
        return best;
    }

    /** 获胜的那一串坐标（按方向排好，含 (r, c)）。 */
    private int[] runPoints(int color, int r, int c, int run) {
        for (int[] d : DIRS) {
            int[] span = runSpan(grid, size, r, c, color, d[0], d[1]);
            if (span[2] != run) continue;
            int[] pts = new int[run];
            for (int k = 0; k < run; k++) {
                pts[k] = (span[0] + k * d[0]) * size + (span[1] + k * d[1]);
            }
            return pts;
        }
        return null;
    }

    /**
     * 假定黑在 (r, c) 落子，是否构成禁手（长连 / 四四 / 三三）。
     * 白棋、开关关掉、非空点、越界 一律返回 false。不改变棋盘状态。
     */
    public boolean isForbidden(int color, int r, int c) {
        if (color != BLACK || !forbiddenForBlack) return false;
        if (!inBounds(size, r, c) || grid[r * size + c] != EMPTY) return false;
        int[] g = grid.clone();
        g[r * size + c] = BLACK;
        return isForbiddenOn(g, size, r, c);
    }

    /**
     * 同 isForbidden，但棋盘由调用方给，且假定 grid 里 (r, c) 已经写成黑子。
     * AI 搜索用的是自己的 scratch 棋盘（不构造 GomokuBoard），所以走这个入口。
     */
    public static boolean isForbiddenOn(int[] grid, int size, int r, int c) {
        int run = 0;
        for (int[] d : DIRS) {
            run = Math.max(run, runSpan(grid, size, r, c, BLACK, d[0], d[1])[2]);
        }
        if (run >= 6) return true;                            // 长连
        if (run == 5) return false;                           // 成五算赢，不算禁手
        if (countFours(grid, size, r, c) >= 2) return true;    // 四四
        return countOpenThreeDirs(grid, size, r, c) >= 2;     // 三三
    }

    /** 禁手种类的中文说法（给 illegalReason 用；调用前假定 isForbidden 为真）。 */
    public String forbiddenKind(int r, int c) {
        if (!inBounds(size, r, c)) return "越界";
        int[] g = grid.clone();
        g[r * size + c] = BLACK;
        return forbiddenKindOn(g, size, r, c);
    }

    /** 同 forbiddenKind，但棋盘由调用方给，且假定 grid 里该点已写成黑子。 */
    public static String forbiddenKindOn(int[] grid, int size, int r, int c) {
        int run = 0;
        for (int[] d : DIRS) {
            run = Math.max(run, runSpan(grid, size, r, c, BLACK, d[0], d[1])[2]);
        }
        if (run >= 6) return "长连";
        if (countFours(grid, size, r, c) >= 2) return "四四";
        if (countOpenThreeDirs(grid, size, r, c) >= 2) return "三三";
        return "禁手";
    }

    /**
     * g 里已经摆好这一手（黑在 (r, c)），数这一手制造出的「四子组」个数。
     * 四子组 = 某一个 5 连窗口里的 4 颗黑子 + 1 个空点，且这 4 颗里含 (r, c)。
     * 同一个四子组只算一次：按 (方向, 4 颗黑子的坐标排序后打包) 去重，所以活四只算一个四。
     */
    private static int countFours(int[] g, int size, int r, int c) {
        int p = r * size + c, n = 0;
        HashSet<Long> seen = new HashSet<>();
        for (int di = 0; di < DIRS.length; di++) {
            int dr = DIRS[di][0], dc = DIRS[di][1];
            for (int k = -4; k <= 0; k++) {                 // 枚举含 p 的 5 连窗口起点
                int wr = r + k * dr, wc = c + k * dc;
                int stones = 0, empty = -1;
                boolean hasP = false;
                for (int t = 0; t < 5; t++) {
                    int rr = wr + t * dr, cc = wc + t * dc;
                    if (!inBounds(size, rr, cc)) {
                        stones = -9;
                        break;
                    }
                    int v = g[rr * size + cc];
                    if (v == BLACK) stones++;
                    else if (v == WHITE) {
                        stones = -9;
                        break;
                    } else {
                        empty = rr * size + cc;
                    }
                    if (rr * size + cc == p) hasP = true;
                }
                if (stones != 4 || empty < 0 || !hasP) continue;
                int[] four = new int[4];
                int m = 0;
                for (int t = 0; t < 5; t++) {
                    int rr = wr + t * dr, cc = wc + t * dc;
                    int q = rr * size + cc;
                    if (q != empty) four[m++] = q;
                }
                Arrays.sort(four);
                // 打包去重键：方向 2 bit + 4 个点各 9 bit（9 bit 够装 size ≤ 22 的点号；
                // 本项目的 15 路远小于它）。故意不用 8 bit —— 那样 size ≥ 19 时点号会被截断，
                // 两条同方向的四有可能被误判成同一个四。
                long key = di;
                for (int t = 0; t < 4; t++) key = (key << 9) | four[t];
                if (seen.add(key)) n++;
            }
        }
        return n;
    }

    /**
     * g 里已经摆好这一手（黑在 (r, c)），数这一手制造出的「活三」方向数（同一方向最多算一个）。
     * 活三 = 存在一个空点，补上它就成「活四」（四子连续、两端皆空且在盘内），且那个活四里含 (r, c)。
     * 补点只可能在这一点所在的那条线上，所以每个方向只扫线上 ±4 个格，不做全盘模拟。
     */
    private static int countOpenThreeDirs(int[] g, int size, int r, int c) {
        int p = r * size + c, dirs = 0;
        for (int[] d : DIRS) {
            boolean found = false;
            for (int k = -4; k <= 4 && !found; k++) {
                if (k == 0) continue;
                int qr = r + k * d[0], qc = c + k * d[1];
                if (!inBounds(size, qr, qc)) continue;
                if (g[qr * size + qc] != EMPTY) continue;
                int[] g2 = g.clone();
                g2[qr * size + qc] = BLACK;
                int[] span = runSpan(g2, size, qr, qc, BLACK, d[0], d[1]);
                if (span[2] != 4 || !inSpan(span, d, size, p)) continue;
                int br = span[0] - d[0], bc = span[1] - d[1];
                int ar = span[0] + 4 * d[0], ac = span[1] + 4 * d[1];
                if (inBounds(size, br, bc) && g[br * size + bc] == EMPTY
                        && inBounds(size, ar, ac) && g[ar * size + ac] == EMPTY) {
                    found = true;
                }
            }
            if (found) dirs++;
        }
        return dirs;
    }

    /** 深拷贝（grid / 手顺 / 胜负状态 / 获胜线都独立）。 */
    /** 内部点号 -> 人类可读坐标（列字母 + 行号，行号从下往上数）；越界返回 ""。 */
    public static String pointToName(int size, int point) {
        if (point < 0 || point >= size * size) return "";
        return GTP_COLS.charAt(point % size) + String.valueOf(size - point / size);
    }

    public GomokuBoard copy() {
        GomokuBoard b = new GomokuBoard(size, forbiddenForBlack);
        System.arraycopy(grid, 0, b.grid, 0, grid.length);
        for (int[] mv : moves) b.moves.add(new int[]{mv[0], mv[1]});
        b.winner = winner;
        b.gameOver = gameOver;
        b.endReason = endReason;
        b.winLine = winLine == null ? null : winLine.clone();
        return b;
    }

    /** 非法落子。 */
    public static class IllegalMove extends RuntimeException {
        public IllegalMove(String msg) {
            super(msg);
        }
    }
}
