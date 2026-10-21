/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

import com.autogo.Analysis;
import com.autogo.Analysis;
import com.autogo.Board;
import com.autogo.GameReview;
import com.autogo.GtpEngine;
import com.autogo.Sgf;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GTP 客户端 + 分析解析的端到端测试。
 *
 * GtpEngine 只依赖 java.io / Process，没有任何 Android 类，所以可以在 JVM 上直接
 * 对着一个真的 KataGo 进程跑 —— 这样握手、banner 跳过、多行响应、错误响应、
 * 超时、以及 kata-genmove_analyze 整条链路都能在装机之前验证掉。
 *
 * 用法：java -cp classes GtpTest <katago> <model> <config>
 */
public class GtpTest {

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();


    /**
     * GameReview 的端到端验证：真引擎、真棋谱，跑完整盘分析再出结论。
     *
     * 这一步查的是纯函数测不到的东西：逐手「分析 -> undo -> 走实际那手」的推进是否对得上、
     * 每一手是不是真的拿到了合法胜率、盘面有没有越走越偏。
     * 用独立的引擎实例，免得和上面的超时用例互相污染。
     */
    private static void testGameReviewE2E(String exe, String model, String cfg) throws Exception {
        System.out.println("整盘分析 + 胜负结论（真引擎）");
        List<String> eargs = Arrays.asList("gtp", "-model", model, "-config", cfg,
                "-override-config", "maxVisits=30");
        GtpEngine engine = null;
        try {
            engine = new GtpEngine(exe, eargs, new File("/tmp/gtptest-engine.log"));
            engine.send("name", 180_000);

            // 一盘 9 路短棋：黑占角、白乱下几手，保证黑白都有上手数
            int[][] pts = {
                    {Board.BLACK, Board.sgfToPoint("cc", 9)},
                    {Board.WHITE, Board.sgfToPoint("gg", 9)},
                    {Board.BLACK, Board.sgfToPoint("gc", 9)},
                    {Board.WHITE, Board.sgfToPoint("cg", 9)},
                    {Board.BLACK, Board.sgfToPoint("ee", 9)},
                    {Board.WHITE, Board.sgfToPoint("ge", 9)},
            };
            List<int[]> moves = new ArrayList<>();
            for (int[] m : pts) moves.add(m);

            long t0 = System.currentTimeMillis();
            // 没有起点快照时也不能留下临时文件（复盘屏现在总是先构造 File 再交给 analyze）
            final File noStartTmp = new File("/tmp/autogo-jvmtest/e2e-nostart.sgf");
            noStartTmp.delete();
            GameReview r = GameReview.analyze(engine, moves, 9, 7.5, 30, Board.BLACK, null,
                    noStartTmp, null);
            check(!noStartTmp.exists(), "没有起点快照时不产生临时文件");
            System.out.println("  分析 " + moves.size() + " 手用了 "
                    + (System.currentTimeMillis() - t0) + " ms");

            // bestPv 是复盘屏要显示的东西（MainActivity.java:1118 把它传给 ReviewActivity），
            // 第 32 轮审计指出它在分析路径上只在 GameReview.java:223 被填、此前**零断言**。
            // 这里只压结构与「不是整盘都空」，不绑 KataGo 的具体着法（免得偶发红灯）。
            check(r.bestPv.length == moves.size() + 1, "bestPv 长度 = 手数 + 1（实际 "
                    + r.bestPv.length + "）");
            // 三条都只做「内部自洽/形状」检查，不绑 KataGo 的具体着法：第 33 轮审计实测（11 次真引擎）
            // 每格都非空、每格首着都等于同一下标的 bestMove、最短 pv 有 4 着 ⇒ 这三条稳定不偶发。
            int pvNonEmpty = 0, pvDisagree = 0, pvShortest = Integer.MAX_VALUE;
            for (int i = 0; i < r.bestPv.length; i++) {
                String pv = r.bestPv[i];
                if (pv == null || pv.trim().isEmpty()) continue;
                String[] pvWords = pv.trim().split("\\s+");
                pvNonEmpty++;
                if (pvWords.length < pvShortest) pvShortest = pvWords.length;
                String best = r.bestMove[i];
                if (best != null && !best.isEmpty() && !pvWords[0].equals(best)) pvDisagree++;
            }
            // 配对判据（第 35 轮审计的 e3 变体逼出来的）：引擎**答了**（那一手胜率不是 NaN）就必须给主变化；
            // 引擎没答（NaN）的格子空着才正常 —— 那种情况上面那条「每一手都拿到合法胜率」本来就会红。
            // 这样既能抓住「漏最后一格」这类 off-by-one（e3），又不会因为引擎偶发不答而误红。
            int pvMissing = 0;
            for (int i = 0; i < r.bestPv.length; i++) {
                if (!Double.isNaN(r.wrToMove[i]) && (r.bestPv[i] == null || r.bestPv[i].trim().isEmpty())) {
                    pvMissing++;
                }
            }
            check(pvMissing == 0, "bestPv：引擎答了的每一手都要有主变化（缺 " + pvMissing + " 格，非空 "
                    + pvNonEmpty + "/" + r.bestPv.length + "）");
            check(pvDisagree == 0, "bestPv 每格首着与 bestMove 一致（不一致 " + pvDisagree + " 格）");
            check(pvShortest >= 2, "bestPv 是着法序列而不是单个着法名（最短 " + pvShortest + " 着）");

            check(!r.cancelled, "没有被取消");
            checkEq(r.wrToMove.length, moves.size() + 1, "wrToMove 长度 = 手数 + 1");
            checkEq(r.loss.length, moves.size(), "loss 长度 = 手数");

            int good = 0;
            for (double w : r.wrToMove) if (w >= 0 && w <= 1) good++;
            checkEq(good, r.wrToMove.length,
                    "每一手都拿到合法胜率（不是 NaN —— 说明逐手推进没错位）");

            boolean lossOk = true;
            for (double l : r.loss) if (l < 0 || l > 1 || Double.isNaN(l)) lossOk = false;
            check(lossOk, "每手损失都在 [0,1] 内");

            double[] curve = r.blackWinrates();
            checkEq(curve.length, moves.size() + 1, "黑方胜率曲线长度正确");
            boolean curveOk = true;
            for (double w : curve) if (w < 0 || w > 1) curveOk = false;
            check(curveOk, "黑方胜率曲线都在 [0,1] 内");

            // 结论：要么指出了一手败着（带手数、位置、亏损、推荐），要么承认是细棋
            GameReview.Verdict v = r.verdict("B+2.5", moves);
            check(v.headline.contains("黑胜 2.5"), "headline 用了传入的结果串：" + v.headline);
            check(v.reason != null && !v.reason.isEmpty(), "给出了原因：" + v.reason);
            boolean reasonOk = v.reason.startsWith("全程没有明显失误")
                    || (v.reason.contains("第 ") && v.reason.contains("亏 "));
            check(reasonOk, "原因说得清楚（要么指出败着、要么说明是细棋）：" + v.reason);
            check(v.oneLine().contains("｜"), "一行结论拼接正常：" + v.oneLine());
            System.out.println("  结论: " + v.oneLine());
            if (v.worstIndex >= 0) {
                check(v.worstIndex < moves.size(), "败着下标在范围内");
                check(v.worstLoss >= GameReview.BLUNDER_THRESHOLD, "败着亏损达到阈值");
            }

            // 分析过程中反复 undo/play，最后引擎盘面必须回到棋谱终局 ——
            // 用 showboard 数一下子，确认没有多一颗少一颗
            // showboard 的棋盘行形如 " 7 . . . . . . O2. ."，子用 X/O 标、后面跟手数。
            // 表头（列字母）不以数字开头，靠这个把它排除掉。
            String[] rows = engine.send("showboard", 60_000).split("\n");
            int stones = 0;
            for (String row : rows) {
                String t = row.trim();
                if (t.isEmpty() || !Character.isDigit(t.charAt(0))) continue;
                for (char c : t.toCharArray()) {
                    if (c == 'X' || c == 'O') stones++;
                }
            }
            // 分析期间引擎自己多下了一手没 undo，所以允许比棋谱多一颗
            check(stones == moves.size() || stones == moves.size() + 1,
                    "引擎盘面石子数与棋谱一致（实际 " + stones + "，棋谱 " + moves.size()
                            + "）—— 回归：逐手 undo/play 曾漏掉最后一手");
            // 取消路径：Progress 一上来就返回 false，结果必须标记为 cancelled。
            // 回归：GameReview.cancelled 原先写死 false，这个标记形同虚设，
            // 调用方（MainActivity 的 !review.cancelled）也就成了空判断。
            // 放在最后做：取消时引擎自己那手不会被 undo，会弄脏盘面。
            // 回调里能看见「这一轮已经算出来的部分结果」—— 复盘屏复用它刷界面全靠这条契约
            // （J1 合并后 ReviewActivity.runAnalysis 依赖它）。
            final double[] seen = new double[]{Double.NaN};
            GameReview stopped = GameReview.analyze(engine, moves, 9, 7.5, 30, Board.BLACK, null,
                    null, (rev, idx, total) -> {
                        seen[0] = rev.wrToMove[0];
                        return false;
                    });
            check(stopped.cancelled, "Progress 返回 false 时 cancelled 必须为 true");
            check(seen[0] >= 0 && seen[0] <= 1,
                    "回调里能读到已算出的 wrToMove[0]（实际 " + seen[0] + "）");
            // 回归：GameReview 的数组曾经是 0 填充，复盘屏读它之后会把「还没算到的尾部」
            // 当成 0% 胜率画出来（J1 合并后实测：0.369149 0.0 0.0）。必须是 NaN。
            check(Double.isNaN(stopped.wrToMove[stopped.wrToMove.length - 1]),
                    "被取消时没算到的点保持 NaN，而不是 0.0（实际 "
                            + stopped.wrToMove[stopped.wrToMove.length - 1] + "）");
            check(Double.isNaN(stopped.loss[stopped.loss.length - 1]),
                    "被取消时没算到的 loss 同样保持 NaN（实际 "
                            + stopped.loss[stopped.loss.length - 1] + "）");
            check(!r.cancelled, "正常跑完的一轮不应被标记为取消");
            // startLoaded 的语义是「起点局面是对的」：这盘没有摆子，空盘起就对了。
            // 只有「有摆子但加载失败」才为 false（那种情况界面会提示只作参考）。
            check(r.startLoaded, "常规谱（无摆子）复盘起点也算正确");
            check(stopped.n == moves.size(), "被取消时 n 仍是总手数（期望 " + moves.size()
                    + "，实际 " + stopped.n + "）");
        } finally {
            if (engine != null) engine.close();
        }
    }

    /**
     * 带摆子的复盘（Lead 第 12 轮修的「复盘在空盘上算」）。
     *
     * 判别力：修复前复盘是在空盘上重放 moves，第一点算出来 ≈ 空盘那一侧的值；
     * 这里拿「把起点快照直接交给引擎问同一个分析点」当真值，两者必须对得上。
     * 若有人把 loadsgf 那段去掉，这个数会跳到空盘值上（差 30 个胜率点）。
     */
    /**
     * 「谁走」的第三方裁判：真引擎 showboard 里的 Next player 行。
     *
     * 审计员第 15 轮建议。App 里所有先后手判断最终都归到 GameReview.sideToMoveOf ——
     * 万一我们整体搞反（比如退回「手数奇偶」），用自己测自己是测不出来的：期望值也是我们写的。
     * 这里让 KataGo 自己说该谁走，两边对不上就红。
     *
     * 六例：让子谱靠 PL 定（0 手）、让子谱靠手顺定（1 手）、非交替同色连下两手（旧奇偶写法答错的那种）、
     * 直接读含手顺的文本、读我们自己写出的整盘、以及「把写出串最后一手改色」看引擎是否跟着改。
     * 后三例针对的是「手顺颜色本身」这一层 —— 前三例交给引擎的只有起点快照，管不到它。
     */
    private static void testNextPlayerAgrees(String exe, String model, String cfg) throws Exception {
        System.out.println("谁走：与引擎 showboard 的 Next player 对齐（真引擎）");
        List<String> eargs = Arrays.asList("gtp", "-model", model, "-config", cfg,
                "-override-config", "maxVisits=30");
        GtpEngine engine = null;
        try {
            engine = new GtpEngine(exe, eargs, new File("/tmp/gtptest-nextplayer.log"));
            engine.send("name", 180_000);
            engine.boardsize(9);
            engine.komi(7.5);
            // 让子谱 0 手：起点快照只有摆子 + PL[W]，引擎该答白
            checkNextPlayer(engine, "(;GM[1]FF[4]SZ[9]KM[7.5]RU[Chinese]HA[2]AB[cc][gg])",
                    new String[][]{}, 1);
            // 让子谱 1 手：快照带 PL[W] 起点，再让引擎走白那手 ⇒ 该答黑
            checkNextPlayer(engine, "(;GM[1]FF[4]SZ[9]KM[7.5]RU[Chinese]HA[2]AB[cc][gg])",
                    new String[][]{{"W", "E5"}}, 2);
            // 非交替：同色连下两手 ⇒ 该答白（旧的手数奇偶写法按 size%2==0 会答黑）
            checkNextPlayer(engine, "(;GM[1]FF[4]SZ[9]KM[7.5]RU[Chinese])",
                    new String[][]{{"B", "E5"}, {"B", "F5"}}, 3);
            // 例4/5/6：前 3 例交给引擎的快照只有起点，管不到「手顺的颜色本身」这一层 ——
            // 那一层原来只有我们自己的 JVM 测试覆盖（审计员第 16 轮指出的边界）。
            // 例4：直接把「含手顺」的文本交给引擎读。
            String lit = "(;GM[1]FF[4]SZ[9]KM[7.5]RU[Chinese];B[ee];B[ff])";
            Sgf.Parsed litP = Sgf.parse(lit);
            checkNextPlayerText(engine, lit, new String[][]{}, litP.board.moves, litP.firstColor, 4);
            // 例5：我们自己写出的整盘（含手顺）再读回来，两端都得对上。
            Board bb = new Board(9, 7.5);
            bb.play(Board.BLACK, Board.sgfToPoint("ee", 9));
            bb.play(Board.BLACK, Board.sgfToPoint("ff", 9));
            String written = Sgf.toSgf(bb, "AutoGo", "AutoGo", "", "", "", Board.BLACK);
            int side5 = checkNextPlayerText(engine, written, new String[][]{}, bb.moves, Board.BLACK, 5);
            // 例6：把写出串最后一手改成白 —— 引擎的答案也应跟着改。用来证明这层对颜色敏感，
            // 而不是「引擎看不见手顺颜色」才碰巧对上。
            String tampered = written.replace(";B[ff]", ";W[ff]");
            check(tampered.contains(";W[ff]"), "篡改最后一手颜色成功：" + tampered);
            // 防呆：篡改必须真的改动了串（否则这条用例是空验证）
            check(!written.equals(tampered), "篡改确实改动了写出串");
            Sgf.Parsed tp = Sgf.parse(tampered);
            int side6 = checkNextPlayerText(engine, tampered, new String[][]{}, tp.board.moves,
                    tp.firstColor, 6);
            // 审计员第 17 轮：上面两条只是日志级证据，引擎答案"翻转"本身没有断言。
            // 这里把它钉死：例5 引擎答白、例6 引擎答黑 ⇒ 引擎确实在按手顺颜色算，不是碰巧对上。
            check(side6 != side5, "改色后引擎的答案也翻转了（例5=" + sideName(side5)
                    + "，例6=" + sideName(side6) + "）");
        } finally {
            if (engine != null) engine.close();
        }
    }

    /** 把「起点快照 + 再补走几手」这种情形转成下面那个通用形式。 */
    private static int checkNextPlayer(GtpEngine engine, String rec, String[][] plays, int no)
            throws Exception {
        Sgf.Parsed p = Sgf.parse(rec);
        String snap = Sgf.toSgf(new Board(9, 7.5), "AutoGo", "AutoGo", "",
                p.handicap, p.setup, p.firstColor);
        return checkNextPlayerText(engine, snap, plays, p.board.moves, p.firstColor, no);
    }

    /**
     * 把整段 SGF 文本交给引擎读，再补走 {@code plays}，然后比「引擎说该谁走」与我们的判据。
     *
     * @param sgfText 交给引擎的文本（可以是只有起点的快照，也可以是含手顺的整盘）
     * @param mv 我们对同一局面的手顺（只按颜色记；坐标不参与判断）
     */
    private static int checkNextPlayerText(GtpEngine engine, String sgfText, String[][] plays,
            List<int[]> mv, int firstColor, int no) throws Exception {
        File tmp = new File("/tmp/autogo-jvmtest/nextplayer.sgf");
        tmp.getParentFile().mkdirs();
        java.nio.file.Files.write(tmp.toPath(),
                sgfText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        engine.loadsgf(tmp.getAbsolutePath());
        List<int[]> judge = new ArrayList<>(mv);
        for (String[] pl : plays) {
            engine.play(pl[0].equals("B") ? Board.BLACK : Board.WHITE, pl[1]);
            judge.add(new int[]{pl[0].equals("B") ? Board.BLACK : Board.WHITE, 0});
        }
        String sb = engine.send("showboard", 30_000);
        int engineSide = engineNextPlayer(sb);
        int ours = GameReview.sideToMoveOf(judge, firstColor, judge.size());
        System.out.println("  例" + no + "：手顺 " + judge.size() + " 手（先手方 "
                + sideName(firstColor) + "）→ 引擎说该 " + sideName(engineSide)
                + " 走，我们的判据给 " + sideName(ours));
        checkEq(engineSide, ours, "例" + no + "：引擎说该 " + sideName(engineSide)
                + " 走，我们的判据给 " + sideName(ours) + "（手顺 " + judge.size() + " 手）");
        return engineSide;
    }

    /** 读 showboard 的 Next player 行；读不到返回 EMPTY（断言会红，且能看出是解析问题）。 */
    private static int engineNextPlayer(String showboard) {
        for (String line : showboard.split("\n")) {
            int k = line.indexOf("Next player:");
            if (k < 0) continue;
            String v = line.substring(k + "Next player:".length()).trim();
            if (v.startsWith("White")) return Board.WHITE;
            if (v.startsWith("Black")) return Board.BLACK;
        }
        return Board.EMPTY;
    }

    private static String sideName(int side) {
        return side == Board.BLACK ? "黑" : (side == Board.WHITE ? "白" : "解析失败");
    }

    /**
     * 起点那一手没算到时，结论必须是有保留的措辞。
     *
     * 为什么单列一条（第 25 轮审计的变体 D）：套件原先压得住 `verdictOf`，却压不住
     * 「把 `verdict()` 那一行委托改回从 `blackWinrates()` 反推」—— 那样改整套件仍全绿。
     *
     * 做法：包一层真引擎，只把**第一次** `kata-genmove_analyze` 响应里带 winrate 的 info 行吃掉。
     * 于是 `wrToMove[0]` 是 NaN，而它顺延出来的 `blackWinrates()[0]` 仍是 0.5 ——
     * 只有「用原始数组判起点」的实现才说得对。
     *
     * 注意这一行上还写着候选手（pv）和目差，所以吃掉它会**连带**清掉 `bestMove[0]` / `scoreLead[0]`
     * （第 26 轮审计实测：`bestMove[0]` 由 `F5` 变成空串）。对这条用例没影响（唯一可测的败着在第 2 手，
     * 用的是 `bestMove[1]`），但别写成「只影响 winrate」。
     */
    private static final class NoFirstWinrate extends GtpEngine {
        private boolean eaten;
        NoFirstWinrate(String exe, List<String> args, File log) throws java.io.IOException {
            super(exe, args, log);
        }
        @Override
        public String send(String command, long timeoutMs) throws java.io.IOException {
            String resp = super.send(command, timeoutMs);
            if (eaten || !command.startsWith("kata-genmove_analyze")) return resp;
            eaten = true;
            StringBuilder sb = new StringBuilder();
            for (String line : resp.split("\n")) {
                if (line.startsWith("info") && line.contains("winrate")) continue;
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private static void testVerdictStartGap(String exe, String model, String cfg) throws Exception {
        System.out.println("起点没算到时 verdict() 的措辞（真引擎 + 吃掉第一次 analyze 的 winrate）");
        List<String> eargs = Arrays.asList("gtp", "-model", model, "-config", cfg,
                "-override-config", "maxVisits=30");
        GtpEngine engine = null;
        try {
            engine = new NoFirstWinrate(exe, eargs, new File("/tmp/gtptest-startgap.log"));
            engine.boardsize(9);
            engine.komi(7.0);
            engine.clearBoard();
            List<int[]> moves = new ArrayList<>();
            moves.add(new int[]{Board.BLACK, Board.sgfToPoint("cc", 9)});
            moves.add(new int[]{Board.WHITE, Board.sgfToPoint("gg", 9)});
            File tmp = new File("/tmp/autogo-jvmtest/startgap.sgf");
            tmp.delete();
            GameReview r = GameReview.analyze(engine, moves, 9, 7.0, 30, Board.BLACK, null, tmp, null);
            check(Double.isNaN(r.wrToMove[0]), "第一次 analyze 的 winrate 确实被吃掉了（起点是 NaN）");
            check(!Double.isNaN(r.blackWinrates()[0]),
                    "顺延数组的 index 0 仍是 0.5（所以不能从它反推起点）");
            GameReview.Verdict v = r.verdict("W+2.5", moves);
            check(!v.reason.contains("从头") && !v.reason.contains("全程"),
                    "起点没算到时不能说「从头/全程」：" + v.reason);
            System.out.println("  起点未算到的结论: " + v.reason);
            // 两条数据无关的前提（第 26 轮审计实测出来的假绿口子）：如果包装器把**每一次** analyze 的
            // winrate 都吃掉，文案会退化成兜底句「这盘没有算到可用的胜率…」，那条否定断言反而照样绿 ——
            // 用例就什么都没压住了。所以先钉死「跑完了」+「只有起点是 NaN」。
            check(!r.cancelled, "分析跑完了（没被取消）");
            boolean restOk = true;
            for (int i = 1; i < r.wrToMove.length; i++) if (Double.isNaN(r.wrToMove[i])) restOk = false;
            check(restOk, "第 1 手之后都算到了（只有起点是 NaN）");
            // 这里只压确定性红线，不压具体措辞：尾句走哪条分支（「从第 1 手起再未落后」/「第 N 手起再未落后」/
            // 「胜负到最后才见分晓」）取决于引擎那 30 次访问算出什么（审计员实测五五开），写死会变成偶发红灯。
            // 措辞细节由 BoardTest 的合成数组用例（v7/v8/v9/v10，零引擎噪声）压 —— 第 27 轮审计实测：
            // 这条引擎用例对「起点判据改错」只有约一半检出率（D 3/8、A 5/8，前缀回归 0 检出），
            // 所以它是补充、不是主力。
            System.out.println("  结论: " + v.reason);
        } finally {
            if (engine != null) engine.close();
        }
    }

    private static void testReviewWithSetup(String exe, String model, String cfg) throws Exception {
        System.out.println("带摆子的复盘（真引擎）");
        List<String> eargs = Arrays.asList("gtp", "-model", model, "-config", cfg,
                "-override-config", "maxVisits=30");
        GtpEngine engine = null;
        try {
            engine = new GtpEngine(exe, eargs, new File("/tmp/gtptest-review-setup.log"));
            engine.send("name", 180_000);
            // 让子/题谱：HA[2] 摆子、白先（App 里 firstColor 就是这么定的）
            Sgf.Parsed p = Sgf.parse("(;GM[1]FF[4]SZ[9]KM[7.5]RU[Chinese]HA[2]AB[cc][gg];W[ee];B[ff])");
            checkEq(p.firstColor, Board.WHITE, "HA[2] 谱判白先");
            Board empty = new Board(9, 7.5);
            String start = Sgf.toSgf(empty, "AutoGo", "AutoGo", "", p.handicap, p.setup, p.firstColor);
            check(start.contains("PL[W]"), "起点快照写了 PL[W]：" + start);
            check(start.contains("AB[cc][gg]"), "起点快照含两个摆子：" + start);

            File tmp = new File("/tmp/autogo-jvmtest/truth.sgf");
            tmp.getParentFile().mkdirs();
            java.nio.file.Files.write(tmp.toPath(),
                    start.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            engine.loadsgf(tmp.getAbsolutePath());
            int stones = 0;
            String sb = engine.send("showboard", 30_000);
            for (String line : sb.split("\n")) {
                if (!line.matches("^\\s*\\d+\\s+.*")) continue;
                String body = line.replaceFirst("^\\s*\\d+\\s+", "");
                if (body.length() != 17) continue;
                for (int c = 0; c < 9; c++) {
                    char ch = body.charAt(2 * c);
                    if (ch == 'X' || ch == 'O') stones++;
                }
            }
            checkEq(stones, 2, "引擎盘上就是那 2 个摆子（不是空盘）");
            // 数完子再问胜率：kata-genmove_analyze 会在引擎盘上真的落一手
            double truth = Analysis.parse(engine.send("kata-genmove_analyze W 1000", 120_000)).rootWinrate();
            System.out.println("  起点真值 = " + truth);

            // 复盘屏的盘面（D1）：起点摆子 + 手顺 → 第 2 手时 4 子
            Sgf.Parsed sp = Sgf.parse(start);
            Board shown = GameReview.viewBoard(9, 7.5, sp.board.grid, p.board.moves, 2);
            int stonesShown = 0;
            for (int v : shown.grid) if (v == Board.BLACK || v == Board.WHITE) stonesShown++;
            checkEq(stonesShown, 4, "复盘屏第2手时应 4 子（2 摆子+2 手），不是只有手顺的 2 子");
            checkEq(GameReview.viewBoard(9, 7.5, null, p.board.moves, 2).grid[Board.sgfToPoint("cc", 9)],
                    Board.EMPTY, "反证：不传摆子时 cc 是空的（旧行为）");
            final File reviewTmp = new File("/tmp/autogo-jvmtest/review.sgf");
            GameReview r = GameReview.analyze(engine, p.board.moves, 9, 7.5, 30, p.firstColor, start,
                    reviewTmp, null);
            check(r.startLoaded, "复盘用上了起点快照（不是空盘重放）");
            check(!reviewTmp.exists(), "复盘用的临时快照跑完就被删掉（不留垃圾）");
            checkEq(r.firstColor, Board.WHITE, "复盘认到白先");
            checkEq(GameReview.sideToMoveOf(p.board.moves, p.firstColor, 0), Board.WHITE,
                    "复盘第 0 点该白走");
            double d = Math.abs(r.wrToMove[0] - truth);
            check(d < 0.05, "复盘第 0 点 = 起点真值（差 " + String.format(java.util.Locale.US, "%.4f", d)
                    + "，复盘 " + r.wrToMove[0] + "，真值 " + truth + "）");
            System.out.println("  复盘 wrToMove[0] = " + r.wrToMove[0] + "，黑方视角 [0] = "
                    + r.blackWinrates()[0]);
        } finally {
            if (engine != null) engine.close();
        }
    }

    private static void check(boolean cond, String what) {
        if (cond) passed++;
        else {
            failures.add(what);
            System.out.println("  FAIL: " + what);
        }
    }

    private static void checkEq(Object actual, Object expected, String what) {
        boolean ok = (actual == null) ? expected == null : actual.equals(expected);
        if (!ok) {
            failures.add(what + "（期望 " + expected + "，实际 " + actual + "）");
            System.out.println("  FAIL: " + what + "（期望 " + expected + "，实际 " + actual + "）");
        } else {
            passed++;
        }
    }

    public static void main(String[] args) throws Exception {
        String exe = args.length > 0 ? args[0] : "/usr/local/bin/katago";
        String model = args.length > 1 ? args[1] : "/sdcard/改/陪练棋盘/档案/autogo/models/kata1-b10c128-s1141046784-d204142634.txt.gz";
        String cfg = args.length > 2 ? args[2] : "/tmp/gtptest.cfg";

        if (!new File(exe).canExecute()) {
            // 第 34 轮审计踩到的坑：权重/引擎路径写错时这里只打印「跳过」、退出码 0 ⇒
            // 一次**根本没跑**的真引擎验证看起来像通过。改成硬失败（退出码 2），run_tests.sh 也会拦住。
            System.out.println("错误：找不到可执行的 katago（" + exe + "）");
            System.exit(2);
            return;
        }
        if (!new File(model).isFile()) {
            System.out.println("错误：找不到权重（" + model + "）");
            System.exit(2);
            return;
        }
        java.nio.file.Files.write(java.nio.file.Paths.get(cfg), Arrays.asList(
                "logDir = /tmp/gtptest-logs",
                "logAllGTPCommunication = false",
                "logSearchInfo = false",
                "logToStderr = false",
                "rules = chinese",
                "ponderingEnabled = false",
                "allowResignation = false",
                "resignThreshold = -0.95",
                "resignConsecTurns = 4",
                "numSearchThreads = 2",
                "numNNServerThreadsPerModel = 2",
                "nnMaxBatchSize = 1",
                "nnCacheSizePowerOfTwo = 20",
                "maxVisits = 30",
                "reportAnalysisWinratesAs = SIDETOMOVE"));
        new File("/tmp/gtptest-logs").mkdirs();

        List<String> eargs = Arrays.asList("gtp", "-model", model, "-config", cfg,
                "-override-config", "maxVisits=30");

        GtpEngine engine = null;
        try {
            System.out.println("启动引擎并握手（模型加载需要几秒）…");
            long t0 = System.currentTimeMillis();
            engine = new GtpEngine(exe, eargs, new File("/tmp/gtptest-engine.log"));
            String name = engine.send("name", 180_000);
            System.out.println("  引擎应答: " + name + "（" + (System.currentTimeMillis() - t0) + " ms）");
            check(name.contains("KataGo"), "name 返回 KataGo 标识：" + name);
            check(engine.isAlive(), "握手后进程仍然存活");

            System.out.println("基本 GTP 命令");
            engine.boardsize(9);
            engine.komi(7.5);
            engine.clearBoard();
            passed += 3;

            String multi = engine.send("showboard", 30_000);
            check(multi.contains("\n"), "showboard 多行响应被完整读回（" + multi.split("\n").length + " 行）");
            check(multi.contains("9"), "showboard 内容含盘面尺寸");

            System.out.println("落子与生成着法");
            engine.play(Board.BLACK, "E5");
            engine.play(Board.WHITE, "E6");
            passed += 2;
            String mv = engine.genmove(Board.BLACK, 120_000);
            System.out.println("  genmove B -> " + mv);
            check(!mv.isEmpty(), "genmove 有返回值");
            int p = Analysis.toPoint(mv, 9);
            check(p == Board.PASS || (p >= 0 && p < 81), "genmove 返回 9 路合法坐标：" + mv);
            // 引擎盘面同步后，再让它走一手也应当合法
            check(!mv.equalsIgnoreCase("resign"), "未开启认输时不会返回 resign");

            System.out.println("undo");
            engine.undo();
            engine.play(Board.WHITE, "C3");
            passed++;

            System.out.println("kata-genmove_analyze 全链路");
            String resp = engine.send("kata-genmove_analyze B 1000", 180_000);
            Analysis.Result ar = Analysis.parse(resp);
            check(ar.moves.size() > 0, "解析出候选点（" + ar.moves.size() + " 个）");
            check(ar.playedMove != null && !ar.playedMove.isEmpty(),
                    "末尾落子被识别：" + ar.playedMove);
            Analysis.MoveInfo top = ar.top();
            check(top != null && top.order == 0, "首选点 order=0");
            check(top != null && top.winrate > 0 && top.winrate < 1,
                    "根胜率在 (0,1)：" + (top == null ? "null" : top.winrate));
            check(top != null && !Double.isNaN(top.scoreLead), "scoreLead 可解析");
            check(top != null && top.pv != null && !top.pv.isEmpty(), "PV 非空：" + (top == null ? "" : top.pv));
            // 关键回归：playedMove 必须是落子而不是任何杂音行
            check(!ar.playedMove.startsWith("="), "playedMove 不是杂音行（回归：曾误取到 '='）");

            System.out.println("错误响应");
            boolean threw = false;
            String errMsg = "";
            try {
                engine.send("this_is_not_a_command", 30_000);
            } catch (GtpEngine.GtpError e) {
                threw = true;
                errMsg = e.getMessage();
            }
            check(threw, "未知命令抛 GtpError（KataGo 用 '?' 开头表示错误）");
            System.out.println("  错误文本: " + errMsg);

            System.out.println("非法落子");
            threw = false;
            try {
                engine.play(Board.BLACK, "A1");   // E5/C3 已占，但 A1 空 —— 用重复落子更稳
                engine.play(Board.WHITE, "A1");
            } catch (GtpEngine.GtpError e) {
                threw = true;
            }
            check(threw, "在已有子的点落子会得到错误响应");

            System.out.println("超时");
            threw = false;
            long t1 = System.currentTimeMillis();
            try {
                engine.send("kata-genmove_analyze W 1000", 50);   // 50ms 必超时
            } catch (GtpEngine.GtpTimeout e) {
                threw = true;
            }
            long dt = System.currentTimeMillis() - t1;
            check(threw, "极短超时抛 GtpTimeout");
            check(dt < 30_000, "超时在合理时间内触发（" + dt + " ms）");

            // 超时后引擎仍在搜索，它的响应稍后才会到达，会污染下一次读取。
            // 正确行为是把引擎标记为不可用，让调用方重启 —— 而不是硬着头皮继续读，
            // 那样会把上一条命令的残留当成这一条的响应，静默产生错位结果。
            System.out.println("超时后必须拒绝继续使用（而不是读出错位数据）");
            boolean refused = false;
            String refuseMsg = "";
            try {
                engine.send("clear_board", 60_000);
            } catch (GtpEngine.GtpError e) {
                refused = true;
                refuseMsg = e.getMessage();
            } catch (GtpEngine.GtpTimeout e) {
                refused = true;
                refuseMsg = "（又超时了）" + e.getMessage();
            }
            check(refused, "超时后的引擎被标记为不可用并拒绝新命令");
            System.out.println("  拒绝原因: " + refuseMsg);
            check(refuseMsg.contains("重启") || refuseMsg.contains("错位"),
                    "拒绝原因里说清了要重启：" + refuseMsg);

        } finally {
            if (engine != null) engine.close();
        }

        testGameReviewE2E(exe, model, cfg);
        testReviewWithSetup(exe, model, cfg);
        testNextPlayerAgrees(exe, model, cfg);
        testVerdictStartGap(exe, model, cfg);

        System.out.println();
        System.out.println("通过 " + passed + " 项，失败 " + failures.size() + " 项");
        if (!failures.isEmpty()) {
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }
}
