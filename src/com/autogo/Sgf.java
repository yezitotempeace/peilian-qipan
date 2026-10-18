package com.autogo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SGF FF[4] 读写（子集）。读的部分是 autogo/sgf.py 的 Java 移植，语义严格对齐：
 *
 * <ul>
 *   <li>不是完整的 SGF 树解析器：只保留主线，**读到第一个分支点就停**，
 *       分支之后的主线手顺也会被丢弃。本项目自己生成的棋谱没有分支，不受影响。</li>
 *   <li>strict=true 时主线出现非法手抛异常 —— 静默跳过会得到一个与棋谱不符的
 *       盘面，那比报错更危险。</li>
 *   <li>遇分支图停在分支点，且**若主线在变化图之后继续，后面那些手会被丢弃**
 *       （合法 SGF，但本项目不解析；调用方应把 {@link Parsed#hasVariations}
 *       提示给用户）。多局棋谱只读第 1 局，见 {@link Parsed#multiGame}。</li>
 * </ul>
 */
public final class Sgf {
    /** 棋谱目录里最多放多少局（超出就报错，不覆盖）。 */
    private static final int MAX_GAMES = 9999;

    private Sgf() {
    }

    // ------------------------------------------------------------------
    // 写
    // ------------------------------------------------------------------

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("]", "\\]");
    }

    public static String toSgf(Board b, String blackName, String whiteName, String result) {
        return toSgf(b, blackName, whiteName, result, "", "");
    }

    /**
     * 把摆子原文解算成「只含 AB/AW 的写法」。
     *
     * 为什么必须做：快照里带 {@code AE}（擦子）会让真引擎整谱拒收
     * （实测 v1.18.2：{@code Could not load sgf: ... initial board position contains
     * invalid stones or zero-liberty stones}），同步于是退回「只重放手顺」，
     * 摆子整批丢掉 —— 比原始写法更糟。这里按顺序把摆子记到空盘上再重新输出，
     * {@code AE} 的效果被吸收、token 不再出现。
     *
     * 只有原文真含 {@code AE} 时才改写，其余原样返回（保留 {@code AB[aa:cc]}
     * 这类简写，避免为了没用处的规整改动面变大）。解析不了也原样返回 ——
     * 宁可不改，也不要凭空丢摆子。
     */
    public static String resolvedSetup(String setup, int size) {
        if (setup == null || setup.isEmpty() || !containsToken(setup, "AE")) return setup == null ? "" : setup;
        int[] g = new int[size * size];
        java.util.Arrays.fill(g, Board.EMPTY);
        try {
            for (int[] st : setupStones(setup, size)) g[st[1]] = st[0];
        } catch (RuntimeException ex) {
            return setup;
        }
        StringBuilder ab = new StringBuilder(), aw = new StringBuilder();
        for (int i = 0; i < g.length; i++) {
            if (g[i] == Board.BLACK) ab.append('[').append(Board.pointToSgf(i, size)).append(']');
            else if (g[i] == Board.WHITE) aw.append('[').append(Board.pointToSgf(i, size)).append(']');
        }
        if (ab.length() == 0 && aw.length() == 0) return "";
        StringBuilder out = new StringBuilder();
        if (ab.length() > 0) out.append("AB").append(ab);
        if (aw.length() > 0) out.append("AW").append(aw);
        return out.toString();
    }

    /** 摆子原文里有没有某个属性名（自己解析，别用子串碰运气）。 */
    private static boolean containsToken(String setup, String name) {
        for (String[] t : tokenize(setup)) {
            if (t[0].equals(name)) return true;
        }
        return false;
    }

    /**
     * @param handicap 原谱的 HA 值（没有则空串）
     * @param setup    原谱的 AB/AW/AE 摆子属性原文（没有则空串）。不写它的话，
     *                 导入死活题/让子谱再保存会把摆子丢掉。
     */
    public static String toSgf(Board b, String blackName, String whiteName, String result,
                               String handicap, String setup) {
        return toSgf(b, blackName, whiteName, result, handicap, setup, Board.EMPTY);
    }

    /**
     * @param firstColor 本谱第一手的行棋方；传 {@link Board#EMPTY} 表示按 HA/摆子/手顺自己推。
     *   为什么要写 {@code PL}：实测真引擎**服从 PL 并覆盖它自己的推断**（例如
     *   {@code PL[B]AB[cc]} 引擎按黑先、{@code PL[W]AB[cc][gg]AW[dd]} 按白先），
     *   而「只有 AB 摆子」「HA 与 AW 并存」这类形态引擎的推断与我们不一致。
     *   写上 PL 让快照里的「该谁走」与 App 完全一致；顺带补上「保存不写 PL」的信息损失。
     */
    public static String toSgf(Board b, String blackName, String whiteName, String result,
                               String handicap, String setup, int firstColor) {
        String clean = resolvedSetup(setup, b.size);
        StringBuilder sb = new StringBuilder();
        sb.append("(;GM[1]FF[4]CA[UTF-8]AP[AutoGo:1.0]");
        sb.append("SZ[").append(b.size).append(']');
        if (handicap != null && !handicap.isEmpty()) {
            sb.append("HA[").append(escape(handicap)).append(']');
        }
        sb.append("KM[").append(trimNum(b.komi)).append(']');
        sb.append("RU[Chinese]");
        sb.append("PB[").append(escape(blackName)).append(']');
        sb.append("PW[").append(escape(whiteName)).append(']');
        sb.append("DT[").append(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())).append(']');
        if (result != null && !result.isEmpty()) sb.append("RE[").append(escape(result)).append(']');
        int first = (firstColor == Board.BLACK || firstColor == Board.WHITE) ? firstColor
                : firstColorOf(b, java.util.Collections.<String, String>emptyMap(), handicap, clean);
        sb.append("PL[").append(Board.colorToChar(first)).append(']');
        if (clean != null && !clean.isEmpty()) sb.append(clean);
        for (int[] m : b.moves) {
            sb.append(';').append(Board.colorToChar(m[0])).append('[')
                    .append(Board.pointToSgf(m[1], b.size)).append(']');
        }
        sb.append(')');
        return sb.toString();
    }

    private static String trimNum(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    /** 写到目录，返回文件；文件名形如 game_001.sgf。 */
    public static File write(File dir, Board b, String blackName, String whiteName, String result)
            throws IOException {
        return write(dir, b, blackName, whiteName, result, "", "");
    }

    /** 带摆子/让子的保存（见 {@link #toSgf(Board, String, String, String, String, String)}）。 */
    public static File write(File dir, Board b, String blackName, String whiteName, String result,
                             String handicap, String setup) throws IOException {
        return write(dir, b, blackName, whiteName, result, handicap, setup, Board.EMPTY);
    }

    /**
     * 带先手方的保存（见 {@link #toSgf(Board, String, String, String, String, String, int)}）。
     * 存盘时把 PL 写进去，将来读回来（自己或别的工具）才知道是谁先走 —— 尤其是
     * 让子谱/题谱，靠 AB/HA 反推未必与当时的实际先后手一致。
     */
    public static File write(File dir, Board b, String blackName, String whiteName, String result,
                             String handicap, String setup, int firstColor) throws IOException {
        if (!dir.exists()) dir.mkdirs();
        File f = null;
        for (int n = 1; n <= MAX_GAMES; n++) {
            File cand = new File(dir, String.format(Locale.US, "game_%03d.sgf", n));
            if (!cand.exists()) {
                f = cand;
                break;
            }
        }
        if (f == null) {
            // 原来的 do/while 在达到上限时会带着「最后一个已存在的文件」退出循环，
            // 紧接着 new FileOutputStream(f) 把它截断覆盖 —— 实测：目录里 9999 个
            // game_*.sgf 时第 10000 次保存返回 game_9999.sgf、文件名总数没变、
            // 旧内容被换掉，用户毫无提示地丢了一局棋谱。宁可报错。
            throw new IOException("棋谱目录已满（" + MAX_GAMES + " 局），请先清理再继续");
        }
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(toSgf(b, blackName, whiteName, result, handicap, setup, firstColor)
                    .getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    public static final class Parsed {
        public final Board board;
        public final Map<String, String> root;
        /** 原谱的 AB/AW/AE 摆子属性原文（形如 AB[aa][bb]AW[cc]）；没有则为空串。 */
        public final String setup;
        /** 原谱的 HA 让子数；没有则为空串。 */
        public final String handicap;
        /** 原谱是否含变化图（主线之外的分支）。 */
        public final boolean hasVariations;
        /** 第一个括号块之后是否还有另一局（多局棋谱）。 */
        public final boolean multiGame;
        /**
         * 主线在变化图之后是否还有手。为真表示**确实丢手了**（不含变化图本身），
         * 界面要给出比「含变化图」更重的说法。
         */
        public final boolean mainLineTruncated;
        /**
         * 本谱**先走**的那一方（{@link Board#BLACK} / {@link Board#WHITE}）。
         *
         * 调用方算「该谁走」必须用它，**不能用「手数奇偶」** —— 那假定了黑先，而
         * 让子谱（`HA[2]` 起）是白先。实测（真 KataGo v1.18.2）：`HA[2]AB[cc][gg]`
         * 0 手时引擎认为该白走，按奇偶算却给出黑走，于是给引擎发 `genmove B`；
         * 引擎不校验轮次、照常出子 ⇒ 白方等于白送一手，让子的作用被削弱。
         */
        public final int firstColor;

        Parsed(Board board, Map<String, String> root, String setup, String handicap,
               boolean hasVariations, boolean multiGame, boolean mainLineTruncated,
               int firstColor) {
            this.board = board;
            this.root = root;
            this.setup = setup;
            this.handicap = handicap;
            this.hasVariations = hasVariations;
            this.multiGame = multiGame;
            this.mainLineTruncated = mainLineTruncated;
            this.firstColor = firstColor;
        }

        public String result() {
            return root.containsKey("RE") ? root.get("RE") : "";
        }

        public String blackName() {
            return root.containsKey("PB") ? root.get("PB") : "Black";
        }

        public String whiteName() {
            return root.containsKey("PW") ? root.get("PW") : "White";
        }
    }

    /**
     * 把 SGF 文本切成 (属性名, 属性值) 序列，忽略节点/分支结构。
     * 遇到嵌套的 '(' 就停 —— 那是变化图，混进来会把各分支的手顺拼成一条。
     */
    /** 只把「真正的空白类字符」（含全角空格/NBSP/BOM）当空白，用于判断块后残留。 */
    private static String unicodeTrim(String s) {
        int a = 0, b = s.length();
        while (a < b && isSpaceish(s.charAt(a))) a++;
        while (b > a && isSpaceish(s.charAt(b - 1))) b--;
        return s.substring(a, b);
    }

    private static boolean isSpaceish(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\uFEFF';
    }

    /**
     * 残留文字里是否真的还有落子/摆子节点。用 {@link #tokenize} 而不是找 ';'：
     * 说明文字、URL、&nbsp; 里都可能出现分号，但不会出现 B[..]/W[..]/AB[..]。
     * 实测（本机 JVM）：找 ';' 的第一版会把「尾部 &nbsp;」「尾部 URL 含 ;」
     * 「尾部说明文字含 ;」「全角空格 + 第二局」这些**以前读得下**的文件一律拒掉。
     */
    private static boolean tailHasMoveNodes(String rest) {
        try {
            for (String[] t : tokenize(rest)) {
                String n = t[0];
                if (n.equals("B") || n.equals("W") || n.equals("AB")
                        || n.equals("AW") || n.equals("AE")) {
                    return true;
                }
            }
        } catch (RuntimeException ignore) {
            return true;
        }
        return false;
    }

    /**
     * 主线在变化图之后是否还有手。这是**合法 SGF**（教材谱常见），但本解析器读到
     * 分支点就停，后面那些手会丢 —— 单独扫一遍才能知道，因为截块扫描到第一个平衡
     * 块就 break 了。
     */
    /**
     * 本谱先走的一方。
     *
     * 优先用主线第一手的颜色（最可靠）；0 手谱再看显式 `PL[...]`，最后才按 `HA`
     * 让子数按 SGF 惯例判白先。**不要用「手数奇偶」** —— 见 {@link Parsed#firstColor}。
     */
    private static int firstColorOf(Board board, Map<String, String> root, String handicap,
            String setup) {
        if (!board.moves.isEmpty()) {
            return board.moves.get(0)[0];
        }
        String pl = root.get("PL");
        if (pl != null) {
            String v = pl.trim();
            if (v.startsWith("W") || v.startsWith("w") || v.startsWith("2")) return Board.WHITE;
            if (v.startsWith("B") || v.startsWith("b") || v.startsWith("1")) return Board.BLACK;
        }
        try {
            if (Integer.parseInt(handicap.trim()) >= 2) return Board.WHITE;   // SGF 惯例：让子局白先
        } catch (RuntimeException ignore) {
            // HA 缺失或不是数字：往下走
        }
        // 只有黑摆子、没有白摆子 ⇒ 按让子/题谱惯例白先。
        // 实测（真 KataGo v1.18.2）：`AB[cc]` 0 手的快照 loadsgf 后引擎报「该白走」，
        // 而我们原来按「无 HA 就黑先」判 ⇒ 双方对「该谁走」不一致。
        int ab = 0, aw = 0;
        try {
            // 用解算后的摆子（AE 已吸收），与 toSgf 推 PL 时用同一份口径 ——
            // 否则 AB[cc]AE[cc] 这种「摆了又擦」的写法两边结论不一致。
            for (int[] st : setupStones(resolvedSetup(setup, board.size), board.size)) {
                if (st[0] == Board.BLACK) ab++;
                else if (st[0] == Board.WHITE) aw++;
            }
        } catch (RuntimeException ignore) {
            // 摆子解析不了：当没有摆子处理
        }
        if (ab > 0 && aw == 0) return Board.WHITE;
        return Board.BLACK;
    }

    private static boolean mainLineContinuesAfterVariation(String block) {
        int depth = 0;
        boolean inValue = false;
        boolean sawVar = false;
        char prev = 0;
        for (int i = 0; i < block.length(); i++) {
            char c = block.charAt(i);
            if (inValue) {
                if (c == '\\') i++;
                else if (c == ']') inValue = false;
                continue;
            }
            if (c == '[') inValue = true;
            else if (c == '(') {
                depth++;
                if (depth > 1) sawVar = true;
            } else if (c == ')') depth--;
            else if (sawVar && depth == 1 && prev == ';') {
                // 变化图之后、depth 回到 1 时又出现节点：主线还有内容没读进来。
                // 属性名与 '[' 之间可能有空格（`;W [ee]`），摆子节点（`;AB[ff]`）也算 ——
                // 实测这两种写法原来都会漏判（提示会说「含变化图」而不是「主线还有手」）。
                int j = i;
                while (j < block.length() && Character.isLetter(block.charAt(j))) j++;
                int k = j;
                while (k < block.length() && Character.isWhitespace(block.charAt(k))) k++;
                if (k < block.length() && block.charAt(k) == '[') {
                    String name = block.substring(i, j).toUpperCase(java.util.Locale.US);
                    if (name.equals("B") || name.equals("W") || name.equals("AB")
                            || name.equals("AW") || name.equals("AE")) {
                        return true;
                    }
                }
            }
            if (!Character.isWhitespace(c)) prev = c;
        }
        return false;
    }

    /**
     * 把 AB[..]AW[..]AE[..] 原文解析成 (颜色, 点) 序列。
     * 悔棋重建/进程回收恢复/引擎同步都要复用摆子 —— 摆子不在 {@code board.moves} 里，
     * 只重放 moves 会把摆子丢掉（见 AGENT_RULES.md 第 2 条）。
     */
    public static List<int[]> setupStones(String setupProps, int size) {
        List<int[]> out = new ArrayList<>();
        for (String[] t : tokenize(setupProps)) {
            String n = t[0];
            if (!n.equals("AB") && !n.equals("AW") && !n.equals("AE")) continue;
            int color = n.equals("AB") ? Board.BLACK
                    : n.equals("AW") ? Board.WHITE : Board.EMPTY;
            for (int pt : setupPoints(t[1], size)) out.add(new int[]{color, pt});
        }
        return out;
    }

    private static List<String[]> tokenize(String text) {
        List<String[]> tokens = new ArrayList<>();
        int i = 0, n = text.length(), depth = 0;
        while (i < n) {
            char ch = text.charAt(i);
            if (ch == '(') {
                depth++;
                if (depth > 1) break;
                i++;
                continue;
            }
            if (ch == ')') {
                depth--;
                if (depth <= 0) break;
                i++;
                continue;
            }
            if (ch == ';' || Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            int start = i;
            while (i < n && Character.isLetter(text.charAt(i))) i++;
            String name = text.substring(start, i).toUpperCase(Locale.US);
            if (name.isEmpty()) {
                i++;
                continue;
            }
            while (i < n && Character.isWhitespace(text.charAt(i))) i++;
            while (i < n && text.charAt(i) == '[') {
                i++;
                StringBuilder buf = new StringBuilder();
                while (i < n) {
                    char c = text.charAt(i);
                    if (c == '\\' && i + 1 < n) {
                        buf.append(text.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (c == ']') {
                        i++;
                        break;
                    }
                    buf.append(c);
                    i++;
                }
                tokens.add(new String[]{name, buf.toString()});
                while (i < n && Character.isWhitespace(text.charAt(i))) i++;
            }
        }
        return tokens;
    }

    public static Parsed parse(String text) {
        return parse(text, true);
    }

    public static Parsed parse(String text, boolean strict) {
        if (text == null) throw new IllegalArgumentException("不是合法的 SGF：内容为空");
        int start = text.indexOf('(');
        if (start < 0) throw new IllegalArgumentException("不是合法的 SGF：缺少 '('");

        // 截到第一个平衡的括号块。
        // **必须跳过 [...] 属性值**：值里可以出现任何字符，包括未配对的 ')' 和
        // 转义写法 '\）'。只数字符（原实现）会把值里的 ')' 当成结构结束，于是在
        // 属性值内部提前截断 —— 后续所有手数静默消失、不抛异常，界面还照常显示
        // 「已读入棋谱：0 手」。实测（本机 JVM，v38 源码）：
        //   (;GM[1]SZ[9]C[先试 ) 后];B[cc];W[dd])  -> 手数 0（应为 2）
        //   (;GM[1]SZ[9]C[a\\)b];B[cc];W[dd])       -> 手数 0（转义形式同样中招）
        //   (;GM[1]SZ[9]C[先试 (a)];B[cc];W[dd])    -> 手数 2（配对的恰好无害）
        int depth = 0, end = text.length();
        boolean inValue = false;
        boolean blockClosed = false;
        boolean sawVariation = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inValue) {
                if (c == '\\') i++;        // 转义：下一个字符属于值本身
                else if (c == ']') inValue = false;
                continue;
            }
            if (c == '[') inValue = true;
            else if (c == '(') {
                depth++;
                if (depth > 1) sawVariation = true;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    end = i + 1;
                    blockClosed = true;
                    break;
                }
            }
        }
        List<String[]> tokens = tokenize(text.substring(start, end));

        Map<String, String> root = new LinkedHashMap<>();
        for (String[] t : tokens) {
            String name = t[0];
            if (!root.containsKey(name) && !name.equals("B") && !name.equals("W")
                    && !name.equals("AB") && !name.equals("AW") && !name.equals("AE")) {
                root.put(name, t[1]);
            }
        }

        int size = 19;
        String sz = root.get("SZ");
        if (sz != null && !sz.trim().isEmpty()) {
            String first = sz.contains(":") ? sz.substring(0, sz.indexOf(':')) : sz;
            try {
                size = Integer.parseInt(first.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("SGF 里的 SZ 无法解析：" + sz);
            }
        }
        if (size < 2 || size > 25) {
            throw new IllegalArgumentException("SGF 指定的棋盘大小不受支持：" + size);
        }

        double komi = 7.5;
        String km = root.get("KM");
        if (km != null) {
            try {
                komi = Double.parseDouble(km.trim());
            } catch (NumberFormatException ignored) {
                // 贴目写坏了不该让整份棋谱读不出来
            }
        }

        Board board = new Board(size, komi);
        // 把 AB/AW/AE 的原文留一份：toSgf 原来只写手数，导入死活题/让子谱再保存时
        // 摆子会静默消失（实测：5 子写回只剩 2 子、HA[2]AB[dd][pp] 只剩 1 子）。
        StringBuilder setupProps = new StringBuilder();
        String lastSetupName = "";
        for (String[] t : tokens) {
            String name = t[0], value = t[1];
            if (name.equals("AB") || name.equals("AW") || name.equals("AE")) {
                int color = name.equals("AB") ? Board.BLACK
                        : name.equals("AW") ? Board.WHITE : Board.EMPTY;
                // 摆子坐标写坏/越界时**必须报错**，口径与落子一致（`;B[pp]` 在 9 路上
                // 是抛异常的）。原来这里静默丢弃：实测 `AB[pp]`(9路) 盘上 0 子、
                // `AB[aa][pp]` 只落 1 子，界面照常显示「已读入棋谱」—— 又是一份与
                // 棋谱不符的盘面。
                List<Integer> pts;
                try {
                    pts = setupPoints(value, size);
                } catch (IllegalArgumentException e) {
                    if (strict) {
                        throw new IllegalArgumentException(
                                "棋谱的 " + name + " 摆子无法解析：" + e.getMessage());
                    }
                    continue;
                }
                for (int point : pts) {
                    board.grid[point] = color;
                }
                // tokenize 一个 [...] 出一个 token，所以 AB[cc][dd] 是两个 token。
                // 同名连续属性要并回一条 AB[cc][dd]（FF[4] 允许重复属性，但合并后
                // 更接近原谱、也更整洁）。
                if (name.equals(lastSetupName)) {
                    setupProps.append('[').append(escape(value)).append(']');
                } else {
                    setupProps.append(name).append('[').append(escape(value)).append(']');
                }
                lastSetupName = name;
            } else if (name.equals("B") || name.equals("W")) {
                int color = name.equals("B") ? Board.BLACK : Board.WHITE;
                int point;
                try {
                    point = Board.sgfToPoint(value, size);
                } catch (IllegalArgumentException e) {
                    if (strict) {
                        throw new IllegalArgumentException(
                                "棋谱第 " + (board.moves.size() + 1) + " 手 " + Board.colorToChar(color)
                                        + " 的坐标无法解析：" + value);
                    }
                    continue;
                }
                String reason = board.illegalReason(color, point);
                if (reason == null) {
                    board.play(color, point);
                } else if (strict) {
                    throw new IllegalArgumentException(
                            "棋谱第 " + (board.moves.size() + 1) + " 手 " + Board.colorToChar(color)
                                    + " " + Board.pointToGtp(point, size) + " 非法：" + reason);
                }
            }
        }
        // 护栏：一只手都没解析出来、盘上也一颗子都没有，而结构本身就是坏的
        // （值没闭合 / 括号块没收尾 / 第一条主线之前就出现了变化图）—— 这多半是
        // 这份棋谱的结构不被支持，而不是「这局棋真的没下」。本文件开头写了
        // 「静默跳过会得到一个与棋谱不符的盘面，那比报错更危险」，所以如实报错。
        //
        // 判据**必须是结构信号，不能是文本子串**。第一版用 text.contains(";B[")
        // 判断，结果把注释里写了「正解：;B[cc]」的**合法题谱**直接拒了 —— 实测
        // (;GM[1]FF[4]SZ[19]AB[dd][pd][dp][pp]C[正解：;B[cc]（见变化图）]) 报错，
        // 而且报错文案还把原因说反了。那是用一个 bug 换另一个 bug。
        // 残留检查：第一个括号块之后还有内容、里面含节点标记 ';'，却不以 '(' 开头
        // —— 说明扫描在棋谱内容之前就把块切断了，后面那些手会被静默丢掉。
        // 实测（本机 JVM，v40）：
        //   (;GM[1]SZ[9]C[x]) ;B [cc] ;W [dd] )  -> 原来 0 手、不报错（丢掉 2 手）
        //   (;GM[1]SZ[9];B[cc]);W[dd];B[ee])     -> 原来只读 1 手、不报错（丢掉 2 手）
        // 多局棋谱（尾巴以 '(' 开头）是合法写法，不报错，只在界面上提示。
        String rest = unicodeTrim(text.substring(end));
        boolean multiGame = rest.startsWith("(");
        if (strict && !rest.isEmpty() && !multiGame && tailHasMoveNodes(rest)) {
            throw new IllegalArgumentException(
                    "这份棋谱在第一个括号块之后还有没被读进来的内容（第一个块读到第 "
                            + end + " 个字符就结束了），说明它的括号结构不被完整支持。"
                            + "为避免只读进一部分手顺却装作读全了，这里直接报错。");
        }
        // 护栏：一只手都没解析出来，而结构本身有问题。
        // 判据**必须是结构信号，不能是文本子串**：第一版用 text.contains(";B[")
        // 判断，结果把注释里写了「正解：;B[cc]」的**合法题谱**直接拒了（实测
        // (;GM[1]FF[4]SZ[19]AB[dd][pd][dp][pp]C[正解：;B[cc]（见变化图）]) 被拒，
        // 而且报错文案还把原因说反了）——那是用一个 bug 换另一个 bug。
        //
        // 信号要分开用：合法棋谱**不可能**值未闭合、**也不可能**块未收尾，这两个
        // 无条件生效；而 sawVariation 需要「盘上无子」兜底，否则会把「合法题谱 +
        // 解答变化图」（AB[aa](;B[cc])(;B[ee])）误拒。这里原来把三个信号都挂在
        // isEmptyBoard 上，导致同一份值未闭合的坏谱「无摆子报错、有摆子静默给
        // 残缺盘面」（实测 H1 vs H1b），被审计员打穿。
        if (strict && board.moves.size() == 0
                && (inValue || !blockClosed || (isEmptyBoard(board) && sawVariation))) {
            throw new IllegalArgumentException(
                    "这份棋谱一只手都没解析出来，而且它的括号结构不完整"
                            + "（属性值没闭合、括号块没收尾，或变化图直接挂在根节点下）。"
                            + "为避免给你一个与棋谱不符的盘面，这里直接报错。");
        }
        String ha = root.get("HA") == null ? "" : root.get("HA");
        return new Parsed(board, root, setupProps.toString(), ha, sawVariation, multiGame,
                sawVariation && mainLineContinuesAfterVariation(text.substring(start, end)),
                firstColorOf(board, root, ha, setupProps.toString()));
    }

    /** 盘上是不是一颗子都没有（含 AB/AW 摆的子）。 */
    private static boolean isEmptyBoard(Board b) {
        for (int i = 0; i < b.grid.length; i++) {
            if (b.grid[i] != Board.EMPTY) return false;
        }
        return true;
    }

    /**
     * 解析 AB/AW/AE 的一个属性值，返回坐标列表。
     *
     * tokenize 是「一个 [...] 出一个 token」，所以 value 要么是单点 "aa"，
     * 要么是 SGF 的矩形简写 "aa:cc"（表示矩形内所有点的并集，两端顺序任意）。
     * 原实现按两字符硬切：'aa:cc' 里第二个点切成 ':c'、解析失败被静默丢掉 ——
     * 实测 (;GM[1]SZ[9]AB[aa:cc];W[dd]) 只落下一个点（应为 9 个）。
     */
    private static List<Integer> setupPoints(String value, int size) {
        List<Integer> out = new ArrayList<>();
        String v = value.trim();
        if (v.isEmpty()) {
            return out;
        }
        // SGF 的虚手/认输保留字：`pass` 是 4 个字母，按「一个 token 塞多组坐标」
        // 切就变成 pa+ss —— 实测 19 路上 `AB[pass]` 会静默落 2 子。保留字必须当 0 子。
        if (v.equalsIgnoreCase("pass") || v.equalsIgnoreCase("resign")) {
            return out;
        }
        int colon = v.indexOf(':');
        if (colon < 0) {
            // 兼容「一个 token 里塞了多组坐标」的写法，按两字符切
            if (v.length() % 2 != 0) {
                throw new IllegalArgumentException("摆子坐标长度不是偶数：" + v);
            }
            for (int i = 0; i + 1 < v.length(); i += 2) {
                int p = Board.sgfToPoint(v.substring(i, i + 2), size);
                if (p != Board.PASS) {
                    out.add(p);
                }
            }
            return out;
        }
        String a = v.substring(0, colon).trim();
        String c = v.substring(colon + 1).trim();
        if (a.length() != 2 || c.length() != 2) {
            throw new IllegalArgumentException("矩形摆子写法无法解析：" + v);
        }
        int r1 = Character.toLowerCase(a.charAt(0)) - 'a';
        int c1 = Character.toLowerCase(a.charAt(1)) - 'a';
        int r2 = Character.toLowerCase(c.charAt(0)) - 'a';
        int c2 = Character.toLowerCase(c.charAt(1)) - 'a';
        if (r1 < 0 || c1 < 0 || r2 < 0 || c2 < 0
                || r1 >= size || c1 >= size || r2 >= size || c2 >= size) {
            throw new IllegalArgumentException("矩形摆子超出 " + size + " 路盘面：" + v);
        }
        for (int r = Math.min(r1, r2); r <= Math.max(r1, r2); r++) {
            for (int col = Math.min(c1, c2); col <= Math.max(c1, c2); col++) {
                out.add(r * size + col);
            }
        }
        return out;
    }
}
