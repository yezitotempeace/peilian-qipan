/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

import com.autogo.Analysis;
import com.autogo.Board;
import com.autogo.GameReview;
import com.autogo.LayoutMath;
import com.autogo.Sgf;
import com.autogo.Theme;
import com.autogo.Palette;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Board.java / Sgf.java 的 JVM 单元测试（不需要 Android 运行时，直接 javac + java 跑）。
 *
 * 用例与桌面版 Python 的 tests/test_board.py 一一对应，用来保证 Java 移植没有改变语义。
 * 运行：bash run_tests.sh
 */
public class BoardTest {

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    /** 零数据（中途取消）时结论不能编成「从头领先到尾」。 */
    private static void testVerdictNoData() {
        List<int[]> mv = new ArrayList<>();
        mv.add(new int[]{Board.BLACK, Board.sgfToPoint("ee", 9)});
        mv.add(new int[]{Board.WHITE, Board.sgfToPoint("ff", 9)});

        double[] nothing = new double[]{Double.NaN, Double.NaN, Double.NaN};
        GameReview.Verdict v = GameReview.summarize(nothing, new double[]{Double.NaN, Double.NaN},
                new String[3], mv, 9, Board.BLACK, "黑胜 2.5 目");
        check(!v.reason.contains("领先"), "零数据时不能声称「从头领先到尾」（实际：" + v.reason + "）");
        check(v.reason.contains("没有算到"), "零数据时如实说明（实际：" + v.reason + "）");

        // 一个手都没算到（第一手之前就取消，或每手解析都失败）：换手数组被顺延过、看着是满的，
        // 只有调用方给的「有几个点没算到」能说明真相。
        GameReview.Verdict v3 = GameReview.summarize(new double[]{0.4, 0.4, 0.4},
                new double[]{Double.NaN, Double.NaN}, new String[3], mv, 9, Board.BLACK,
                "黑胜 2.5 目", 2);
        check(!v3.reason.contains("领先") && !v3.reason.contains("没有明显失误"),
                "只算到起点时不能说「没有明显失误」（实际：" + v3.reason + "）");
        check(v3.reason.contains("没有算到"), "只算到起点时如实说明（实际：" + v3.reason + "）");

        // 半路取消（第 2 个点没算到）：最大失误照给，但走势必须有保留，不能说「全程…」「领先」
        GameReview.Verdict v4 = GameReview.summarize(new double[]{0.36, 0.70, 0.50},
                new double[]{0.07, Double.NaN}, new String[3], mv, 9, Board.BLACK,
                "黑胜 2.5 目", 1);
        check(v4.reason.contains("没算到"), "半路取消时走势要有保留（实际：" + v4.reason + "）");
        check(!v4.reason.contains("全程") && !v4.reason.contains("领先"),
                "半路取消时不能说「全程/领先」（实际：" + v4.reason + "）");

        // 中间有洞（第 2 个点没解析出来、第 3 个点算出来了）：只能说「有 1 个点没算到」，
        // 不能按个数瞎推成「算到第 2 手」（第 21 轮审计的 GAP 用例）。
        GameReview.Verdict v5 = GameReview.summarize(new double[]{0.36, 0.70, 0.70, 0.90},
                new double[]{0.07, 0.02, 0.01}, new String[4], mv, 9, Board.BLACK,
                "黑胜 2.5 目", 1);
        check(v5.reason.contains("还有 1 个点没算到"), "中间有洞时按个数说（实际：" + v5.reason + "）");
        check(!v5.reason.contains("算到第"), "中间有洞时不能说「算到第 N 手」（实际：" + v5.reason + "）");

        // 只有起点没算到（后面每手都算出来了）：不能说「从头」，也不能说「全程没有明显失误」
        // —— 第 1 手亏多少本来就要看起点胜率。（第 22 轮审计的 {NaN,0.6} / {NaN,0.5} 用例。）
        GameReview.Verdict v6 = GameReview.summarize(new double[]{Double.NaN, 0.5},
                new double[]{Double.NaN}, new String[]{null, null}, mv, 9, Board.BLACK,
                "黑胜 2.5 目");
        check(!v6.reason.contains("从头"), "起点没算到时不能说「从头」（实际：" + v6.reason + "）");
        check(!v6.reason.contains("全程"), "起点没算到时不能说「全程」（实际：" + v6.reason + "）");
        check(v6.reason.contains("第 1 手起再未落后"), "起点没算到时退一步说（实际：" + v6.reason + "）");
        // 起点未知 + 黑方全程不落后（尾句走 lastBehind<0 那条分支）：UI 形状下要说
        // 「从第 1 手起再未落后」，不能说「从头领先到尾」。
        GameReview.Verdict v8 = GameReview.summarize(new double[]{0.5, 0.6, 0.7},
                new double[]{Double.NaN, Double.NaN}, new String[3], mv, 9, Board.BLACK,
                "黑胜 2.5 目", 0, false);
        check(!v8.reason.contains("从头"), "起点未知时尾句不能说「从头」（实际：" + v8.reason + "）");
        check(v8.reason.contains("从第 1 手起再未落后"), "起点未知时尾句退一步说（实际：" + v8.reason + "）");

        // 界面路径的**接线本身**也要被压住：verdictOf 同时拿到原始与顺延两个数组。
        // 若有人改回「从 toBlackWinrates 的结果反推起点」，下面这两条会红
        // （第 24 轮审计实测：套件原先抓不住这种回归）。
        GameReview.Verdict v9 = GameReview.verdictOf(new double[]{Double.NaN, 0.4, 0.55},
                new double[]{Double.NaN, Double.NaN}, new String[3], mv, 9, Board.BLACK, "B+2.5");
        check(!v9.reason.contains("从头") && !v9.reason.contains("全程"),
                "界面路径接线：起点没算到不能说「从头/全程」（实际：" + v9.reason + "）");
        check(v9.reason.contains("算到的部分"),
                "界面路径接线：起点没算到要有保留（实际：" + v9.reason + "）");

        // 界面路径真正要跑的那一行（verdict() 的委托）用合成对象压：真引擎用例对这条回归只有约一半
        // 检出率（第 27 轮审计实测 D 3/8、A 5/8，前缀回归 0 检出），所以确定性覆盖必须落在合成数组上。
        GameReview fx = GameReview.forTest(new double[]{Double.NaN, 0.74, 0.47},
                new double[]{Double.NaN, Double.NaN}, new String[3], mv, 9, Board.BLACK);
        // 先钉 forTest 自己的契约（第 28 轮审计实测：不钉的话，「forTest 被改错」的四个变体
        // —— n 多 1 / n 少 1 / 丢掉手顺 / 构造器两个 int 写反 —— 都能让下面两条断言静默全绿，
        // 用例会悄悄滑到别的分支去）。诊断信息里不要直接索引 [2]：n 少 1 那种改错会自己先抛越界
        // （审计员踩过），所以用长度判断短路掉。
        checkEq(fx.wrToMove.length, mv.size() + 1, "forTest：wrToMove 长度 = 手数 + 1");
        checkEq(fx.loss.length, mv.size(), "forTest：loss 长度 = 手数");
        checkEq(fx.size, 9, "forTest：size 传进去了");
        checkEq(fx.moves.size(), mv.size(), "forTest：手顺传进去了");
        check(Double.isNaN(fx.wrToMove[0]) && fx.wrToMove.length > 2
                        && fx.wrToMove[1] == 0.74 && fx.wrToMove[2] == 0.47,
                "forTest：起点留着 NaN、后面两个点照抄传入数组");
        // 用对象**自己**的手顺调 verdict()（产品路径也是这么用的：review.verdict(result, review.moves)）——
        // 传外部那份 mv 的话，forTest 手顺被改错（例如反序）就完全不可观察（第 29 轮审计的盲区之一）。
        GameReview.Verdict v10 = fx.verdict("W+2.5", fx.moves);
        // 上面那个 fixture 上 bestMove / cancelled / firstColor / 手顺都不可观察（bestMove 全 null、
        // loss 全 NaN、起点是 NaN ⇒ 一律 0.5）。第 29 轮审计实测：于是「bestMove 填错 / 拷贝偏移 1 /
        // cancelled=true / firstColor 写反 / 手顺反序 / 去掉 bestMove 的 null 护栏」六种改错全都能
        // 380/0 静默全绿 ⇒ 再补两个小 fixture 把这些字段照出来。
        GameReview fx2 = GameReview.forTest(new double[]{Double.NaN, 0.6, 0.4},
                new double[]{0.3, Double.NaN}, new String[]{"E5", "", ""}, mv, 9, Board.BLACK);
        check(!fx2.cancelled, "forTest：cancelled 默认 false");
        checkEq(fx2.bestMove[0], "E5", "forTest：bestMove 传进去了");
        // 这个 fixture 里第 1 手是黑（败方）的败着、带推荐手 ⇒ 结论必须出现「（应下 E5）」与「第 1 手」
        GameReview.Verdict v11 = fx2.verdict("W+2.5", fx2.moves);
        check(v11.reason.contains("（应下 E5）"),
                "verdict() 说败方败着时要带上传进来的推荐手（实际：" + v11.reason + "）");
        check(v11.reason.contains("第 1 手"), "手顺传进去后手数才数得对（实际：" + v11.reason + "）");

        // firstColor 只在「手顺为空 + 起点有数」时可观察（手顺非空时它只影响 index 0，而那里是 NaN）
        GameReview fx3 = GameReview.forTest(new double[]{0.8, 0.5}, new double[]{Double.NaN},
                null, null, 9, Board.BLACK);
        checkEq(fx3.blackWinrates()[0], 0.8,
                "forTest：firstColor 生效（先手黑 ⇒ 起点 0.8 就是黑方胜率），且 bestMove/moves 传 null 不崩");
        // 第 30 轮审计又压出 4 个「forTest 改错但全绿」的格：手顺的点、moves 的 null 存储、
        // bestMove 尾巴、loss 拷贝的 Math.min（最后一格是真盲区：wrToMove 那处 min 恒等，loss 这处不是
        // —— 传一个比 n 长的 loss 就会越界，而现有 fixture 全都不传长 loss）。
        checkEq(fx2.moves.get(0)[1], mv.get(0)[1], "forTest：手顺的点也要照抄");
        check(fx3.moves != null && fx3.moves.isEmpty(), "forTest：moves=null 要存成空列表");
        checkEq(fx2.bestMove[2], "", "forTest：bestMove 尾巴也要照抄");
        boolean lossTruncated;   // 用 try/catch 把「越界」变成干净的 FAIL，别让套件中断
        try {
            lossTruncated = GameReview.forTest(new double[]{0.8, 0.6, 0.4}, new double[]{0.1, 0.2, 0.3},
                    null, mv, 9, Board.BLACK).loss.length == 2;
        } catch (RuntimeException ex) {
            lossTruncated = false;
        }
        check(lossTruncated, "forTest：loss 传长了要按 n 截断（不能越界）");
        // 第 31 轮审计把 forTest 的可观察面穷尽了一遍，压出 7 个「改了还能全绿」的位
        // （bestPv 那格没有入口、属学术性，不补），这里把剩下 6 个钉住：fx2 起点后的两格、
        // loss 的**值**、bestMove 的中间槽、手顺每一手的颜色与点、以及一个**白先**的 fixture
        // （fx2/fx3 恰好都是黑先 ⇒ 把 firstColor 写死成 BLACK 也看不出来）。
        checkEq(fx2.wrToMove[1], 0.6, "forTest：fx2 起点后的第一个点照抄");
        checkEq(fx2.wrToMove[2], 0.4, "forTest：fx2 起点后的第二个点照抄");
        checkEq(fx2.loss[0], 0.3, "forTest：loss 的值也要照抄（不能整体缩放）");
        checkEq(fx2.bestMove[1], "", "forTest：bestMove 的中间槽也要照抄");
        for (int i = 0; i < fx2.moves.size(); i++) {
            checkEq(fx2.moves.get(i)[0], mv.get(i)[0], "forTest：手顺第 " + (i + 1) + " 手颜色照抄");
            checkEq(fx2.moves.get(i)[1], mv.get(i)[1], "forTest：手顺第 " + (i + 1) + " 手点照抄");
        }
        GameReview fx4 = GameReview.forTest(new double[]{0.8, 0.5}, new double[]{Double.NaN},
                null, null, 9, Board.WHITE);
        // 注意别写死 0.2：1 - 0.8 是 0.19999999999999996（我第一版写等值断言，正确树上就红了 —— 套件自己抓的）
        check(Math.abs(fx4.blackWinrates()[0] - 0.2) < 1e-9,
                "forTest：白先时起点 0.8 是白方胜率 ⇒ 黑方视角 0.2（实际 " + fx4.blackWinrates()[0] + "）");
        check(!v10.reason.contains("从头") && !v10.reason.contains("全程"),
                "verdict() 委托：起点没算到不能说「从头/全程」（实际：" + v10.reason + "）");
        check(v10.reason.contains("算到的部分"),
                "verdict() 委托：起点没算到要有保留（实际：" + v10.reason + "）");

        check(!GameReview.startComputed(new double[]{Double.NaN, 0.5}),
                "startComputed：原始数组起点是 NaN ⇒ 不知道");
        check(GameReview.startComputed(new double[]{0.4, 0.5}), "startComputed：起点有数 ⇒ 知道");

        // 界面路径的形状：起点是 NaN，先经 toBlackWinrates（它把 index 0 填成 0.5）再进 summarize。
        // 这条就是第 23 轮审计端到端实测出的坑：v64 从这里仍会说出「从头领先到尾」/「全程没有明显失误」。
        double[] raw = {Double.NaN, 0.7, 0.3};
        double[] bwUi = GameReview.toBlackWinrates(raw, mv, Board.BLACK);
        GameReview.Verdict v7 = GameReview.summarize(bwUi, new double[]{Double.NaN, Double.NaN},
                new String[3], mv, 9, Board.BLACK, "黑胜 2.5 目", 0,
                GameReview.startComputed(raw));
        check(!v7.reason.contains("从头"), "界面路径：起点没算到不能说「从头」（实际：" + v7.reason + "）");
        check(!v7.reason.contains("全程"), "界面路径：起点没算到不能说「全程」（实际：" + v7.reason + "）");
        // 尾句走哪个分支要看手顺/胜率（不能写死是「从第 1 手起再未落后」）：
        // 起点未知时该说的是「算到的部分…」这句有保留的前缀。
        check(v7.reason.contains("算到的部分"), "界面路径：起点没算到时前缀要有保留（实际：" + v7.reason + "）");

        // 反证：有数据时照旧能说出「第几手起再未落后」——兜底没有把正常结论吃掉
        GameReview.Verdict v2 = GameReview.summarize(new double[]{0.2, 0.3, 0.6},
                new double[]{0.4, 0.1}, new String[3], mv, 9, Board.BLACK, "黑胜 2.5 目");
        check(v2.reason.contains("再未落后") || v2.reason.contains("领先"),
                "有数据时结论照常（实际：" + v2.reason + "）");
    }

    private static void check(boolean cond, String what) {
        if (cond) {
            passed++;
        } else {
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

    /** 直接摆子，跳过规则校验（只用于构造测试局面）。 */
    private static void place(Board b, String spec, int color) {
        for (String tok : spec.split("\\s+")) {
            if (tok.isEmpty()) continue;
            b.grid[Board.gtpToPoint(tok, b.size)] = color;
        }
    }

    private static int gp(String v, int size) {
        return Board.gtpToPoint(v, size);
    }

    // ------------------------------------------------------------------

    static void testCoordinates() {
        System.out.println("坐标转换");
        boolean ok = true;
        for (int r = 0; r < 19; r++) {
            for (int c = 0; c < 19; c++) {
                int p = r * 19 + c;
                if (Board.gtpToPoint(Board.pointToGtp(p, 19), 19) != p) ok = false;
            }
        }
        check(ok, "19 路 GTP 坐标往返");

        checkEq(Board.pointToGtp(0 * 19 + 8, 19), "J19", "GTP 跳过 I：第 9 列是 J");
        checkEq(gp("J19", 19), 8, "J19 解析");
        checkEq(Board.gtpToPoint("pass", 19), Board.PASS, "pass 解析");
        checkEq(Board.pointToGtp(Board.PASS, 19), "pass", "pass 输出");

        ok = true;
        for (int size : new int[]{9, 13, 19}) {
            for (int p = 0; p < size * size; p++) {
                if (Board.sgfToPoint(Board.pointToSgf(p, size), size) != p) ok = false;
            }
        }
        check(ok, "SGF 坐标往返");

        checkEq(Board.pointToGtp(Board.sgfToPoint("aa", 19), 19), "A19", "SGF aa = 左上角");
        checkEq(Board.pointToGtp(Board.sgfToPoint("ss", 19), 19), "T1", "SGF ss = 右下角");

        boolean threw = false;
        try {
            gp("A20", 19);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "A20 越界应报错");

        threw = false;
        try {
            gp("I9", 19);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "I9 应被拒绝（GTP 无 I 列）");
    }

    static void testCapture() {
        System.out.println("提子");
        {
            Board b = new Board(9, 7.5);
            b.play(Board.BLACK, gp("A1", 9));
            b.play(Board.WHITE, gp("A2", 9));
            b.play(Board.BLACK, gp("E5", 9));
            b.play(Board.WHITE, gp("B1", 9));
            checkEq(b.grid[8 * 9 + 0], Board.EMPTY, "角上黑子被提");
            checkEq(b.captures[Board.WHITE], 1, "白方提子数 = 1");
        }
        {
            Board b = new Board(9, 7.5);
            place(b, "D4 E4 F4", Board.BLACK);
            place(b, "C4 G4 D5 E5 D3 E3 F3", Board.WHITE);
            checkEq(b.group(gp("D4", 9)).liberties.length, 1, "构造局面：黑三子只剩一气");
            b.play(Board.WHITE, gp("F5", 9));
            checkEq(b.grid[5 * 9 + 3], Board.EMPTY, "提掉 D4");
            checkEq(b.grid[5 * 9 + 4], Board.EMPTY, "提掉 E4");
            checkEq(b.grid[5 * 9 + 5], Board.EMPTY, "提掉 F4");
            checkEq(b.captures[Board.WHITE], 3, "白方提子数 = 3");
        }
        {
            Board b = new Board(9, 7.5);
            place(b, "A2 B1", Board.BLACK);
            String reason = b.illegalReason(Board.WHITE, gp("A1", 9));
            check(reason != null && reason.contains("自杀"), "真自杀手被拒绝");
        }
        {
            Board b = new Board(9, 7.5);
            place(b, "A2 B1", Board.BLACK);
            place(b, "A3 B2 C1", Board.WHITE);
            String reason = b.illegalReason(Board.WHITE, gp("A1", 9));
            check(reason == null, "能提子的入气手合法：" + reason);
            int[] captured = b.play(Board.WHITE, gp("A1", 9));
            int[] sorted = captured.clone();
            Arrays.sort(sorted);
            int[] want = {gp("A2", 9), gp("B1", 9)};
            Arrays.sort(want);
            check(Arrays.equals(sorted, want), "提到 A2 与 B1 两子");
        }
        {
            Board b = new Board(9, 7.5);
            b.play(Board.BLACK, gp("E5", 9));
            String reason = b.illegalReason(Board.WHITE, gp("E5", 9));
            check(reason != null && reason.contains("已有子"), "已有子的点被拒绝");
        }
    }

    /** 劫：内部坐标 黑 (0,1)(1,0)(2,1)，白 (1,1)(0,2)(2,2)(1,3)，空 (1,2)。 */
    private static Board koBoard() {
        Board b = new Board(9, 7.5);
        place(b, "B9 A8 B7", Board.BLACK);
        place(b, "B8 C9 C7 D8", Board.WHITE);
        return b;
    }

    static void testKo() {
        System.out.println("劫");
        {
            Board b = koBoard();
            b.play(Board.BLACK, gp("C8", 9));
            checkEq(b.grid[1 * 9 + 1], Board.EMPTY, "黑提白 (1,1)");
            checkEq(b.captures[Board.BLACK], 1, "黑方提子数 = 1");
            checkEq(b.koPoint, gp("B8", 9), "劫点落在 B8");
            String reason = b.illegalReason(Board.WHITE, gp("B8", 9));
            check(reason != null && reason.contains("劫"), "白不能立即回提");
        }
        {
            Board b = koBoard();
            b.play(Board.BLACK, gp("C8", 9));
            b.play(Board.WHITE, gp("G5", 9));
            b.play(Board.BLACK, gp("G4", 9));
            check(b.illegalReason(Board.WHITE, gp("B8", 9)) == null, "找劫材后可以回提");
        }
        {
            Board b = koBoard();
            int[] snapshot = b.grid.clone();
            b.illegalReason(Board.WHITE, gp("C8", 9));
            check(Arrays.equals(b.grid, snapshot), "illegalReason 必须完整还原棋盘");
        }
        {
            Board b = new Board(9, 7.5);
            place(b, "A2 B1", Board.BLACK);
            boolean threw = false;
            try {
                b.play(Board.WHITE, gp("A1", 9));
            } catch (Board.IllegalMove e) {
                threw = true;
            }
            check(threw, "play 遇到非法手抛 IllegalMove");
        }
    }

    static void testScoring() {
        System.out.println("数子");
        {
            Board b = new Board(9, 7.5);
            double[] s = b.scoreArea();
            checkEq(s[0], 0.0, "空盘黑 0");
            checkEq(s[1], 7.5, "空盘白 = 贴目");
            checkEq(b.resultString(), "W+7.5", "空盘结果 W+7.5");
        }
        {
            Board b = new Board(19, 0.0);
            for (int r = 0; r < 19; r++) {
                b.grid[r * 19 + 0] = Board.BLACK;
                b.grid[r * 19 + 18] = Board.WHITE;
            }
            double[] s = b.scoreArea();
            checkEq(s[0], 19.0, "黑 19 子");
            checkEq(s[1], 19.0, "白 19 子");
        }
        {
            Board b = new Board(9, 0.0);
            place(b, "A9 B9 C9 D9 A6 B6 C6 D6 A8 A7 D8 D7", Board.BLACK);
            place(b, "H1", Board.WHITE);
            double[] s = b.scoreArea();
            checkEq(s[0], 16.0, "黑 12 子 + 4 目围空");
            checkEq(s[1], 1.0, "白只有 H1 一子");
        }
    }

    static void testSgf() {
        System.out.println("SGF");
        {
            Board b = new Board(9, 6.5);
            int[][] seq = {{Board.BLACK, gp("E5", 9)}, {Board.WHITE, gp("E6", 9)},
                    {Board.BLACK, gp("D5", 9)}, {Board.WHITE, Board.PASS}};
            for (int[] m : seq) b.play(m[0], m[1]);
            String text = Sgf.toSgf(b, "A", "B", "B+2.5");
            check(text.startsWith("(;GM[1]FF[4]"), "SGF 头部");
            check(text.contains("SZ[9]"), "SGF 含盘面大小");
            check(text.contains("KM[6.5]"), "SGF 含贴目");
            check(text.contains("RE[B+2.5]"), "SGF 含结果");
            // E5 -> ee、E6 -> de、D5 -> ed（SGF 的 aa 在左上角，行号是倒过来的）
            check(text.contains(";B[ee];W[de];B[ed];W[]"), "SGF 着法序列与虚手编码正确");
            check(text.endsWith(")"), "SGF 以 ) 收尾");
        }
        {
            Board b = new Board(9, 7.5);
            String text = Sgf.toSgf(b, "a]b\\c", "w", null);
            check(text.contains("PB[a\\]b\\\\c]"), "SGF 属性值转义正确");
        }
    }

    /** SGF 读取（读入棋谱功能的核心）。用例与 Python 版 sgf.py 的语义一一对应。 */
    static void testSgfParse() {
        System.out.println("SGF 读取");
        {
            // 往返：写出去再读回来，盘面与着法必须完全一致
            Board b = new Board(9, 6.5);
            int[][] seq = {{Board.BLACK, gp("E5", 9)}, {Board.WHITE, gp("E6", 9)},
                    {Board.BLACK, gp("D5", 9)}, {Board.WHITE, Board.PASS},
                    {Board.BLACK, gp("C3", 9)}};
            for (int[] m : seq) b.play(m[0], m[1]);
            Sgf.Parsed p = Sgf.parse(Sgf.toSgf(b, "A", "B", "B+2.5"));
            checkEq(p.board.size, 9, "往返：盘面大小");
            checkEq(p.board.komi, 6.5, "往返：贴目");
            checkEq(p.board.moves.size(), b.moves.size(), "往返：手数一致");
            check(Arrays.equals(p.board.grid, b.grid), "往返：盘面完全一致");
            checkEq(p.result(), "B+2.5", "往返：结果");
            checkEq(p.blackName(), "A", "往返：黑方名");
            boolean sameMoves = true;
            for (int i = 0; i < b.moves.size(); i++) {
                if (p.board.moves.get(i)[0] != b.moves.get(i)[0]
                        || p.board.moves.get(i)[1] != b.moves.get(i)[1]) sameMoves = false;
            }
            check(sameMoves, "往返：每手颜色与坐标一致");
        }
        {
            // SGF 里 ']' 和 '\' 必须转义，所以原值 a]b\c 要写成 a\]b\\c
            Sgf.Parsed p = Sgf.parse("(;GM[1]FF[4]SZ[9]KM[7.5]PB[a\\]b\\\\c]PW[w])");
            checkEq(p.blackName(), "a]b\\c", "转义属性值被正确还原");
        }
        {
            // 分支图不能混进主线：读到 '(' 就停。
            // 注意 SGF 坐标是「左上为 aa」，与 GTP 的行号方向相反：
            // ee = E5，ff = F4（不是 F6）。这里统一用 sgfToPoint 表述，避免手算出错。
            Sgf.Parsed p = Sgf.parse("(;GM[1]FF[4]SZ[9]KM[7.5];B[ee];W[ff](;B[dd])(;B[cc]))");
            checkEq(p.board.moves.size(), 2, "遇到分支点即停，主线只保留 2 手");
            checkEq(p.board.moves.get(0)[1], Board.sgfToPoint("ee", 9), "分支前第 1 手正确");
            checkEq(p.board.moves.get(1)[1], Board.sgfToPoint("ff", 9), "分支前第 2 手正确");
        }
        {
            // 白落在已有黑子的点上：strict 必须报错，非 strict 跳过该手
            String bad = "(;GM[1]FF[4]SZ[9];B[ee];W[ee])";
            boolean threw = false;
            String msg = "";
            try {
                Sgf.parse(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
                msg = e.getMessage();
            }
            check(threw, "strict 模式拒绝非法手");
            check(msg.contains("非法") && msg.contains("第 2 手"), "错误信息指出了第几手：" + msg);
            Sgf.Parsed lenient = Sgf.parse(bad, false);
            checkEq(lenient.board.moves.size(), 1, "非 strict 模式跳过非法手");
        }
        {
            // AB/AW 摆子（让子/死活题常见）。cc=C7、dd=D6、ee=E5
            Sgf.Parsed p = Sgf.parse("(;GM[1]FF[4]SZ[9]KM[0.5]AB[cc][dd]AW[ee])");
            checkEq(p.board.grid[Board.sgfToPoint("cc", 9)], Board.BLACK, "AB 摆黑子");
            checkEq(p.board.grid[Board.sgfToPoint("dd", 9)], Board.BLACK, "AB 多个摆子");
            checkEq(p.board.grid[Board.sgfToPoint("ee", 9)], Board.WHITE, "AW 摆白子");
            checkEq(p.board.moves.size(), 0, "摆子不计入手数");
        }
        {
            Sgf.Parsed p = Sgf.parse("(;GM[1]FF[4]SZ[13:13]KM[0])");
            checkEq(p.board.size, 13, "SZ 带冒号时取前一个数");
            checkEq(p.board.komi, 0.0, "KM[0] 解析");
        }
        {
            // 摆子/让子必须能原样写回 —— 只写手数的话，导入死活题/让子谱再保存会
            // 静默丢掉摆子（实测过 5 子写回只剩 2 子、HA[2]AB[dd][pp] 只剩 1 子）。
            String text = "(;GM[1]FF[4]SZ[9]KM[7.5]HA[2]AB[cc][dd]AW[ee];B[gg])";
            Sgf.Parsed p = Sgf.parse(text);
            checkEq(p.handicap, "2", "读到 HA 让子数");
            check(p.setup.contains("AB[cc][dd]"), "读到 AB 原文：" + p.setup);
            check(p.setup.contains("AW[ee]"), "读到 AW 原文：" + p.setup);
            Sgf.Parsed back = Sgf.parse(Sgf.toSgf(p.board, "黑", "白", "",
                    p.handicap, p.setup));
            checkEq(back.board.moves.size(), 1, "摆子往返：手数不变");
            check(Arrays.equals(back.board.grid, p.board.grid), "摆子往返：盘面逐点一致");
            checkEq(back.handicap, "2", "摆子往返：HA 保留");
            // 矩形简写也要保留（不能展开成 9 个点写回去）
            Sgf.Parsed rect = Sgf.parse("(;GM[1]FF[4]SZ[9]KM[7.5]AB[aa:cc])");
            Sgf.Parsed rectBack = Sgf.parse(Sgf.toSgf(rect.board, "黑", "白", "",
                    rect.handicap, rect.setup));
            check(Arrays.equals(rectBack.board.grid, rect.board.grid), "摆子往返：AB[aa:cc] 矩形一致");
        }
        {
            // 护栏（结构信号）：值未闭合 / 块未收尾 / 根下变化图 —— 一只手都读不出来
            // 时必须报错，而不是静默给一个与棋谱不符的盘面。
            // 关键：不能因为「盘上有摆子」就跳过值未闭合/块未收尾这两个信号
            // （审计实测过同一份坏谱「无摆子报错、有摆子静默给残缺盘面」）。
            checkThrowsSgf("(;GM[1]SZ[9]C[abc", "值未闭合、盘上无子 -> 报错");
            checkThrowsSgf("(;GM[1]SZ[9]AB[aa]C[abc", "值未闭合、盘上有摆子 -> 也要报错");
            checkThrowsSgf("(;GM[1]SZ[9](;B[cc])(;B[ee]))", "变化图挂在根节点下 -> 报错");
            checkThrowsSgf("(;GM[1]SZ[9]C[x]) ;B [cc] ;W [dd] )", "块后残留节点（丢手）-> 报错");
            checkThrowsSgf("(;GM[1]SZ[9];B[cc]);W[dd];B[ee])", "块后残留主线（只读 1/3 手）-> 报错");
            // 这三条是**合法**棋谱，一条都不许拒
            Sgf.Parsed tsumego = Sgf.parse(
                    "(;GM[1]FF[4]SZ[19]AB[dd][pd][dp][pp]C[正解：;B[cc]（见变化图）])");
            checkEq(tsumego.board.moves.size(), 0, "注释里写了 ;B[ 的合法题谱不该被拒");
            Sgf.Parsed withVar = Sgf.parse("(;GM[1]SZ[9]AB[aa][bb](;B[cc])(;B[ee]))");
            checkEq(withVar.board.moves.size(), 0, "题谱 + 解答变化图不该被拒");
            checkEq(withVar.board.grid[Board.sgfToPoint("aa", 9)], Board.BLACK, "该题谱的摆子读到了");
            // 多局棋谱：合法写法，只读第 1 局但要标出来
            Sgf.Parsed multi = Sgf.parse("(;GM[1]SZ[9]KM[7.5];B[cc];W[dd])(;GM[1]SZ[9];B[ee])");
            checkEq(multi.board.moves.size(), 2, "多局棋谱只读第 1 局");
            check(multi.multiGame, "多局棋谱标记 multiGame");
            // 摆子坐标写坏/越界必须报错，口径与落子一致（原来静默丢弃，
            // 实测 AB[pp](9路) 盘上 0 子、AB[aa][pp] 只落 1 子）
            checkThrowsSgf("(;GM[1]SZ[9]AB[pp])", "越界摆子 -> 报错");
            checkThrowsSgf("(;GM[1]SZ[9]AB[aa][pp])", "一好一坏的摆子 -> 报错（不许只落一半）");
            checkThrowsSgf("(;GM[1]SZ[9]AB[aa:zz])", "越界矩形摆子 -> 报错");
            checkEq(Sgf.parse("(;GM[1]SZ[9]AB[aa][pp])", false).board.grid[
                    Board.sgfToPoint("aa", 9)], Board.BLACK, "非 strict 仍读得下好点");
            // 主线在变化图之后继续：合法 SGF，本解析器会少读，但必须标出 hasVariations
            Sgf.Parsed a31 = Sgf.parse("(;GM[1]SZ[9];B[cc];W[dd](;B[ee])(;B[ff]);B[gg])");
            checkEq(a31.board.moves.size(), 2, "变化图之后的主线读不到（已知限制）");
            check(a31.hasVariations, "标出 hasVariations，界面据此提示用户");
        }
        {
            // setupStones：悔棋/进程回收/引擎同步都要靠它把摆子补回盘上。
            // 顺序必须是「先摆子再落子」—— 手顺可能吃掉摆子。
            java.util.List<int[]> st = Sgf.setupStones("AB[cc][dd]AW[ee]AE[ff]", 9);
            checkEq(st.size(), 4, "setupStones 保留 AE（AE 是「擦掉该点」）");
            checkEq(st.get(0)[0], Board.BLACK, "setupStones 第 1 项是黑");
            checkEq(st.get(0)[1], Board.sgfToPoint("cc", 9), "setupStones 第 1 点是 cc");
            checkEq(st.get(2)[0], Board.WHITE, "setupStones 第 3 项是白");
            checkEq(st.get(2)[1], Board.sgfToPoint("ee", 9), "setupStones 第 3 点是 ee");
            if (st.size() == 4) {
                checkEq(st.get(3)[0], Board.EMPTY, "setupStones 第 4 项 AE 记为 EMPTY");
            } else {
                check(false, "setupStones 应返回 4 项（实际 " + st.size() + "）");
            }
            checkEq(Sgf.setupStones("", 9).size(), 0, "空摆子原样返回空");
            // 端到端：AB[aa] 白 ba、ab 两子提掉 aa。
            // 重建顺序必须是「先摆子再手顺」—— 反过来 aa 会留在盘上（区分力就在这里）。
            Sgf.Parsed cap = Sgf.parse("(;GM[1]FF[4]SZ[9]KM[7.5]AB[aa];W[ba];B[cc];W[ab])");
            checkEq(cap.board.grid[Board.sgfToPoint("aa", 9)], Board.EMPTY, "解析：摆子 aa 被提掉");
            Board copy = new Board(9, 7.5);
            for (int[] q : Sgf.setupStones(cap.setup, 9)) copy.grid[q[1]] = q[0];
            for (int[] m : cap.board.moves) copy.play(m[0], m[1]);
            check(Arrays.equals(copy.grid, cap.board.grid),
                    "摆子 -> 手顺 重建盘面与解析结果逐点一致（含提子）");
            checkEq(copy.grid[Board.sgfToPoint("aa", 9)], Board.EMPTY,
                    "重建：aa 也被提掉（顺序不能颠倒）");
        }
        {
            // 宽松模式不许被护栏影响（strict=false 仍按旧口径静默跳过）
            Sgf.Parsed lenient = Sgf.parse("(;GM[1]SZ[9]C[abc", false);
            checkEq(lenient.board.moves.size(), 0, "非 strict 模式不抛护栏");
        }
        {
            boolean threw = false;
            try {
                Sgf.parse("no parens here");
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(threw, "缺少 '(' 报错");
        }
    }

    /**
     * 屏幕自适应尺寸计算。用一批真实设备分辨率跑，验证不变量：
     * 棋盘放得下、不会小到没法下、控件区不会没地方。
     */

    /**
     * GameReview 的「为什么赢」判定。纯函数，不需要引擎 —— 但这是最容易出错的一环，
     * 所以每种措辞分支都单独钉住。
     */
    static void testGameReview() {
        System.out.println("\n== 胜负结论（GameReview）==");

        // 结果串 -> 胜方 / 人话
        checkEq(GameReview.winnerOf("B+3.5"), Board.BLACK, "B+3.5 胜方是黑");
        checkEq(GameReview.winnerOf("W+R"), Board.WHITE, "W+R 胜方是白");
        checkEq(GameReview.winnerOf("Draw"), Board.EMPTY, "Draw 没有胜方");
        checkEq(GameReview.headlineOf("B+3.5"), "黑胜 3.5 目", "headline: B+3.5");
        checkEq(GameReview.headlineOf("W+0.5"), "白胜 0.5 目", "headline: W+0.5");
        checkEq(GameReview.headlineOf("W+R"), "白中盘胜（对方认输）", "headline: W+R");
        checkEq(GameReview.headlineOf("B+R"), "黑中盘胜（对方认输）", "headline: B+R");
        checkEq(GameReview.headlineOf("Draw"), "和棋", "headline: Draw");

        // 视角换算：第 0 手黑行棋直接用，第 1 手白行棋要取反
        List<int[]> alt = new ArrayList<>();
        alt.add(new int[]{Board.BLACK, 0});
        alt.add(new int[]{Board.WHITE, 0});
        checkEq(GameReview.blackView(0.25, Board.WHITE), 0.75, "黑方视角：白方 25% ⇒ 黑 75%");
        checkEq(GameReview.blackView(0.25, Board.BLACK), 0.25, "黑方视角：黑方 25% 原样");
        double[] bw = GameReview.toBlackWinrates(new double[]{0.75, 0.25, 0.5}, alt, Board.BLACK);
        checkEq(bw[0], 0.75, "第 0 手（黑行棋）直接用");
        checkEq(bw[1], 0.75, "第 1 手（白行棋）取反 1-0.25");
        checkEq(bw[2], 0.5, "第 2 手（又是黑行棋）直接用");
        double[] bwNan = GameReview.toBlackWinrates(new double[]{Double.NaN, Double.NaN}, alt, Board.BLACK);
        checkEq(bwNan[0], 0.5, "缺数据时第 0 手按 50%");
        checkEq(bwNan[1], 0.5, "缺数据时沿用前一手");
        // 白先（让子谱/题谱）：同一组数，黑方视角必须整体镜像。
        // 回归：这里原来写死 (i%2==0)?黑:白，白先的谱整条曲线是反的。
        List<int[]> altW = new ArrayList<>();
        altW.add(new int[]{Board.WHITE, 0});
        altW.add(new int[]{Board.BLACK, 0});
        double[] bwW = GameReview.toBlackWinrates(new double[]{0.75, 0.25, 0.5}, altW, Board.WHITE);
        checkEq(bwW[0], 0.25, "白先时第 0 点（白行棋）取反 1-0.75");
        checkEq(bwW[1], 0.25, "白先时第 1 点（黑行棋）直接用 0.25");
        checkEq(bwW[2], 0.5, "白先时第 2 点（又轮到白）取反 1-0.5");
        // 没有手顺可依据（null / 空表）时的契约：按「先手方起交替」算，不是「每点都算先手方」。
        // 回归：换成后者会让整条曲线镜像（旧 3 参版给 [0.75,0.75,0.5]）。
        double[] bwNo = GameReview.toBlackWinrates(new double[]{0.75, 0.25, 0.5}, null, Board.BLACK);
        checkEq(bwNo[0], 0.75, "无手顺：第0点先手方直接用");
        checkEq(bwNo[1], 0.75, "无手顺：第1点按交替算对手，取反");
        checkEq(bwNo[2], 0.5, "无手顺：第2点又轮先手方");
        // 非交替手顺（同一方连下两手）：下标奇偶不等于行棋方。
        // 数据是用真引擎实测来的（审计员第 12 轮 D2）：手顺 B,B,W 时引擎给的
        // wrToMove = [0.3557, 0.6926, 0.0304, 0.9733]。按下标奇偶算，i=2 会被当成
        // 「轮到黑」直接取 0.0304；实际轮到白，黑方视角应是 1-0.0304 = 0.9696。
        List<int[]> bbw = new ArrayList<>();
        bbw.add(new int[]{Board.BLACK, 0});
        bbw.add(new int[]{Board.BLACK, 0});
        bbw.add(new int[]{Board.WHITE, 0});
        double[] bwBBW = GameReview.toBlackWinrates(
                new double[]{0.3557, 0.6926, 0.0304, 0.9733}, bbw, Board.BLACK);
        checkEq(Math.round(bwBBW[0] * 10000), 3557L, "B,B,W：第0点轮到黑，直接用");
        checkEq(Math.round(bwBBW[1] * 10000), 3074L, "B,B,W：第1点轮到白，取反");
        checkEq(Math.round(bwBBW[2] * 10000), 9696L, "B,B,W：第2点轮到白（不是下标偶=黑），取反");
        checkEq(Math.round(bwBBW[3] * 10000), 9733L, "B,B,W：第3点轮到黑，直接用");

        // 造一盘 4 手的棋：黑 D4 / 白 Q16 / 黑 D16 / 白 Q4
        List<int[]> moves = new ArrayList<>();
        moves.add(new int[]{Board.BLACK, Board.sgfToPoint("dp", 19)});
        moves.add(new int[]{Board.WHITE, Board.sgfToPoint("pp", 19)});
        moves.add(new int[]{Board.BLACK, Board.sgfToPoint("dd", 19)});
        moves.add(new int[]{Board.WHITE, Board.sgfToPoint("pd", 19)});
        String expPoint = Board.pointToGtp(moves.get(1)[1], 19);

        // 白第 2 手（下标 1）亏 28%，是全局最大失误；黑第 1 手曾经落后
        double[] loss = {0.01, 0.28, 0.02, 0.03};
        double[] curve = {0.5, 0.45, 0.85, 0.88, 0.90};
        String[] best = {"", "Q16", "", ""};

        GameReview.Verdict v = GameReview.summarize(
                curve, loss, best, moves, 19, Board.BLACK, "黑胜 3.5 目");
        check(v.reason.contains("白第 2 手"), "指出是白棋第 2 手的问题：" + v.reason);
        check(v.reason.contains(expPoint), "点出了那一手的位置 " + expPoint + "：" + v.reason);
        check(v.reason.contains("亏 28%"), "给出亏损幅度：" + v.reason);
        check(v.reason.contains("应下 Q16"), "给出引擎推荐：" + v.reason);
        check(v.reason.contains("黑棋第 2 手起再未落后"), "说明胜方从哪手起没再落后：" + v.reason);
        checkEq(v.worstIndex, 1, "最大失误下标");
        check(v.oneLine().equals("黑胜 3.5 目｜" + v.reason), "一行结论拼接：" + v.oneLine());

        // 细棋：所有亏损都低于阈值 -> 不该硬编一个「败着」出来
        GameReview.Verdict v2 = GameReview.summarize(
                curve, new double[]{0.01, 0.02, 0.03, 0.04}, best, moves, 19, Board.BLACK, "黑胜 0.5 目");
        check(v2.reason.startsWith("全程没有明显失误"), "细棋局不编败着：" + v2.reason);
        checkEq(v2.worstIndex, -1, "无失误时 worstIndex = -1");

        // 最大失误是胜方自己下的
        GameReview.Verdict v3 = GameReview.summarize(
                curve, new double[]{0.01, 0.02, 0.30, 0.03}, best, moves, 19, Board.BLACK, "黑胜 3.5 目");
        check(v3.reason.contains("最大失误其实是黑"), "胜方自身失误的措辞：" + v3.reason);
        check(v3.reason.contains("没影响胜负"), "说明未影响胜负：" + v3.reason);

        // 和棋
        GameReview.Verdict v4 = GameReview.summarize(
                curve, new double[]{0.01, 0.02, 0.03, 0.04}, best, moves, 19, Board.EMPTY, "和棋");
        check(v4.reason.contains("双方始终接近"), "和棋的说法：" + v4.reason);

        // 白棋一路领先
        GameReview.Verdict v5 = GameReview.summarize(
                new double[]{0.5, 0.4, 0.35, 0.30, 0.25},
                new double[]{0.01, 0.02, 0.03, 0.04}, best, moves, 19, Board.WHITE, "白胜 10.0 目");
        check(v5.reason.contains("白棋从头领先到尾"), "一路领先的说法：" + v5.reason);

        // 赢的一方到最后仍落后（对方认输的那种）—— 不能硬说「领先」
        GameReview.Verdict v6 = GameReview.summarize(
                new double[]{0.5, 0.6, 0.7, 0.8, 0.9},
                new double[]{0.01, 0.02, 0.03, 0.04}, best, moves, 19, Board.WHITE,
                "白中盘胜（对方认输）");
        check(v6.reason.contains("胜负到最后才见分晓"), "胜方终局胜率仍不足五成时的措辞：" + v6.reason);
        check(!v6.reason.contains("领先"), "此时不能说「领先」（会和目数结果自相矛盾）：" + v6.reason);

        // 停一手也要能说清楚，不能崩
        List<int[]> passMoves = new ArrayList<>();
        passMoves.add(new int[]{Board.BLACK, Board.PASS});
        GameReview.Verdict v7 = GameReview.summarize(
                new double[]{0.5, 0.5}, new double[]{0.30}, new String[]{"", ""}, passMoves, 19,
                Board.WHITE, "白胜 1.0 目");
        check(v7.reason.contains("停一手"), "虚手说成「停一手」：" + v7.reason);
    }

    static void testLayoutMath() {
        System.out.println("屏幕自适应");

        // {宽dp, 高dp, 说明} —— 混了 String，所以用 Object[]
        Object[][] screens = {
                {320, 480, "老小屏 竖屏"},
                {360, 640, "常见小屏 竖屏"},
                {411, 891, "主流手机 竖屏"},
                {480, 1000, "大屏手机 竖屏"},
                {800, 1280, "平板 竖屏"},
                {480, 320, "老小屏 横屏"},
                {640, 360, "小屏 横屏"},
                {891, 411, "主流手机 横屏"},
                {1280, 800, "平板 横屏"},
        };
        int margin = 8;
        boolean okSize = true, okMin = true, okLandscape = true;
        for (Object[] sc : screens) {
            int w = (Integer) sc[0], h = (Integer) sc[1];
            String name = (String) sc[2];
            boolean landscape = w > h;
            int board = LayoutMath.boardSizeDp(w, h, margin, landscape);
            if (board > w - margin * 2 || board > h) {
                okSize = false;
                System.out.println("  " + name + " 棋盘超出屏幕: " + board);
            }
            if (board < LayoutMath.MIN_BOARD_DP) {
                okMin = false;
                System.out.println("  " + name + " 棋盘小于下限: " + board);
            }
            if (landscape && board > h - margin * 2) {
                okLandscape = false;
                System.out.println("  " + name + " 横屏棋盘高度溢出: " + board);
            }
            System.out.println(String.format("  %-16s %4dx%-5d 棋盘 %3d dp", name, w, h, board));
        }
        check(okSize, "所有屏幕下棋盘都不超出可用空间");
        check(okMin, "所有屏幕下棋盘都不小于下限");
        check(okLandscape, "横屏下棋盘不超过屏高");

        // 竖屏：棋盘 + 控件预留 不应超出屏高（除非屏幕实在太矮）
        boolean okReserve = true;
        for (Object[] sc : screens) {
            int w = (Integer) sc[0], h = (Integer) sc[1];
            String name = (String) sc[2];
            if (w > h) continue;
            int board = LayoutMath.boardSizeDp(w, h, margin, false);
            int reserve = Math.max(LayoutMath.MIN_CONTROLS_RESERVE_DP, (int) (h * 0.40));
            if (board + reserve > h && board > LayoutMath.MIN_BOARD_DP) {
                okReserve = false;
                System.out.println("  " + name + " 棋盘挤占了控件空间");
            }
        }
        check(okReserve, "竖屏下棋盘给控件留出了空间");

        // 竖屏棋盘不该比横屏还大（同尺寸设备）
        check(LayoutMath.boardSizeDp(891, 411, margin, true)
                        <= LayoutMath.boardSizeDp(411, 891, margin, false),
                "同一设备横屏棋盘不大于竖屏");

        // 曲线高度夹在 80~160
        boolean okChart = true;
        for (int h = 300; h <= 2000; h += 50) {
            int c = LayoutMath.chartHeightDp(h);
            if (c < 80 || c > 160) okChart = false;
        }
        check(okChart, "曲线高度始终在 80~160dp");
        checkEq(LayoutMath.chartHeightDp(300), 80, "矮屏取曲线下限");
        checkEq(LayoutMath.chartHeightDp(2000), 160, "高屏取曲线上限");

        // 按钮字号
        checkEq(LayoutMath.buttonTextSp(320), 12, "窄屏按钮字号收一号");
        checkEq(LayoutMath.buttonTextSp(359), 12, "359dp 仍算窄屏");
        checkEq(LayoutMath.buttonTextSp(360), 14, "360dp 起用正常字号");
        checkEq(LayoutMath.buttonTextSp(411), 14, "主流宽度用正常字号");
    }

    /**
     * 棋盘绘制几何。重点验证全屏（视图非正方形）时棋盘不会被切出屏幕 ——
     * 这正是之前「全屏横屏棋盘下半截看不见」的成因。
     */
    static void testBoardGeometry() {
        System.out.println("棋盘几何");

        Object[][] viewports = {
                {1080, 1080, 0, "普通正方形"},
                {1080, 2340, 0, "竖屏全屏"},
                {2340, 1080, 0, "横屏全屏"},
                {1080, 2340, 137, "竖屏全屏(留按钮)"},
                {2340, 1080, 137, "横屏全屏(留按钮)"},
                {600, 900, 90, "小屏全屏(留按钮)"},
        };
        boolean okInside = true, okSquare = true, okPositive = true;
        for (Object[] v : viewports) {
            int w = (Integer) v[0], h = (Integer) v[1];
            float inset = (Integer) v[2];
            String name = (String) v[3];
            LayoutMath.BoardGeometry g = LayoutMath.boardGeometry(w, h, 19, inset);
            // 19 路盘的网格必须落在视图内
            if (g.right(19) > w + 0.5f || g.bottom(19) > h + 0.5f
                    || g.padX < -0.5f || g.padY < inset - 0.5f) {
                okInside = false;
                System.out.println("  " + name + " 越界: right=" + g.right(19)
                        + " bottom=" + g.bottom(19) + " (view " + w + "x" + h + ")");
            }
            // 横竖间距必须相等（正方形棋盘）
            if (Math.abs((g.right(19) - g.padX) - (g.bottom(19) - g.padY)) > 0.5f) {
                okSquare = false;
                System.out.println("  " + name + " 不是正方形");
            }
            if (g.spacing <= 0 || g.stoneRadius <= 0) {
                okPositive = false;
                System.out.println("  " + name + " 间距/半径非正");
            }
            System.out.println(String.format("  %-22s %4dx%-5d inset=%-4.0f spacing=%.1f",
                    name, w, h, inset, g.spacing));
        }
        check(okInside, "各种视口下棋盘网格都在视图内");
        check(okSquare, "棋盘始终是正方形");
        check(okPositive, "间距与半径都为正");

        // 非全屏（正方形视图 + 无 inset）必须与旧行为等价：原点 = 边距
        LayoutMath.BoardGeometry sq = LayoutMath.boardGeometry(1080, 1080, 19, 0);
        float labelRoom = Math.max(18f, 1080 * 0.045f);
        float expectMargin = labelRoom + 4f;
        check(Math.abs(sq.padX - expectMargin) < 1f,
                "正方形视图下 padX 等于边距（与旧版一致）");
        check(Math.abs(sq.padY - expectMargin) < 1f,
                "正方形视图下 padY 等于边距（与旧版一致）");

        // 横屏全屏：旧实现只用宽度算，会让棋盘高度超出视图 —— 这里确认现在不会
        LayoutMath.BoardGeometry land = LayoutMath.boardGeometry(2340, 1080, 19, 137);
        check(land.bottom(19) <= 1080f, "横屏全屏棋盘底部不超出屏幕（旧版会超出）");

        // 顶部 inset 必须被尊重（棋盘不能压到返回按钮上）
        check(land.padY >= 137f, "横屏全屏棋盘给返回按钮留出了顶部空间");
    }

    /** Sgf.parse 必须抛 IllegalArgumentException（护栏用）。 */
    static void checkThrowsSgf(String text, String msg) {
        try {
            Sgf.parse(text);
            check(false, msg + "（实际没报错）");
        } catch (IllegalArgumentException e) {
            check(true, msg);
        }
    }

    /**
     * 第 10/11 轮的 SGF 行为：尾部残留检查（不误报 / 该报的必须报）、
     * `AB[pass]`、`mainLineTruncated`、`firstColor`（让子谱白先）。
     *
     * 这一批是审计员第 10 轮指出的覆盖缺口：原来这几条规则**只在他的探针里有断言**，
     * 把规则单点回退成旧写法后 `BoardTest` 仍然全过（等于项目测试没兜住）。
     */
    static void testSgfGuards() {
        String base = "(;GM[1]FF[4]SZ[9]KM[7.5];B[cc];W[dd])";
        // 尾部这些内容**不该**被判成「手顺没读完」（旧判据只找子串 ';'，全被误拒）
        String[] tailsOk = {
            "",
            "\n&nbsp;",
            "\n说明：见附注；或联系作者",
            "\nhttps://example.org/game;id=123",
            "\n说明: 见附注; 联系作者",
            "\n[]",
            "\n/* 注释 */",
            "\n" + repeat("说明", 5000),
            "\n" + repeat("说明；", 3300),
        };
        for (String tail : tailsOk) {
            try {
                Sgf.Parsed p = Sgf.parse(base + tail);
                checkEq(p.board.moves.size(), 2, "尾部'" + brief(tail) + "'照常读入 2 手");
                check(!p.multiGame, "尾部'" + brief(tail) + "'不算多局");
            } catch (RuntimeException e) {
                check(false, "尾部'" + brief(tail) + "'不该被拒（" + e.getMessage() + "）");
            }
        }
        // 尾部确实是下一局：不报错，但必须标出来
        String[] tailsMulti = {
            "\n(;GM[1]SZ[9];B[ee])",
            "\r\n(;GM[1]SZ[9];B[ee])",
            "\u3000(;GM[1]SZ[9];B[ee])",
            "\uFEFF(;GM[1]SZ[9];B[ee])",
        };
        for (String tail : tailsMulti) {
            try {
                Sgf.Parsed p = Sgf.parse(base + tail);
                checkEq(p.board.moves.size(), 2, "尾部'下一局'只读第 1 局");
                check(p.multiGame, "尾部'下一局'标为 multiGame");
            } catch (RuntimeException e) {
                check(false, "尾部'下一局'不该被拒（" + e.getMessage() + "）");
            }
        }

        // AB[pass]/resign 不是坐标：当 0 子（旧写法会按「打包坐标」切成 pa+ss 落 2 子）
        checkEq(Sgf.setupStones(Sgf.parse("(;GM[1]SZ[19]AB[pass];W[ee])").setup, 19).size(),
                0, "AB[pass] 当 0 子");
        checkEq(Sgf.setupStones(Sgf.parse("(;GM[1]SZ[19]AB[resign];W[ee])").setup, 19).size(),
                0, "AB[resign] 当 0 子");
        checkEq(Sgf.setupStones(Sgf.parse("(;GM[1]SZ[19]AB[PASS];W[ee])").setup, 19).size(),
                0, "AB[PASS] 当 0 子");
        checkEq(Sgf.setupStones(Sgf.parse("(;GM[1]SZ[19]AB[pass][aa];W[ee])").setup, 19).size(),
                1, "AB[pass][aa] 只落 aa");

        // 主线在变化图之后还有手：必须标出来（含属性名与 '[' 之间有空格、摆子节点两种写法）
        check(Sgf.parse("(;GM[1]SZ[9];B[cc](;B[dd]);W[ee])").mainLineTruncated,
                "变化图之后还有手 → mainLineTruncated");
        check(Sgf.parse("(;GM[1]SZ[9];B[cc](;B[dd]);W [ee])").mainLineTruncated,
                "「;W [ee]」带空格也要算主线还有手");
        check(Sgf.parse("(;GM[1]SZ[9];B[cc](;B[dd]);AB[ff])").mainLineTruncated,
                "变化图之后跟摆子节点也算主线还有手");
        check(!Sgf.parse("(;GM[1]SZ[9];B[cc];W[dd](;B[dd])(;B[cc]))").mainLineTruncated,
                "变化图挂在末尾 → 没丢手，不许说「还有手」");
        check(!Sgf.parse("(;GM[1]SZ[9]AB[aa];W[bb](;B[cc])(;B[dd]))").mainLineTruncated
                && Sgf.parse("(;GM[1]SZ[9]AB[aa];W[bb](;B[cc])(;B[dd]))").hasVariations,
                "题谱型（带解答变化图、无后续）→ hasVariations 但不 truncated");

        // firstColor：让子谱是白先，不能按「手数奇偶」当黑先
        checkEq(Sgf.parse("(;GM[1]SZ[9];B[cc];W[dd])").firstColor, Board.BLACK, "普通谱黑先");
        checkEq(Sgf.parse("(;GM[1]SZ[9]HA[2]AB[cc][gg])").firstColor, Board.WHITE,
                "HA[2] 0 手 → 白先");
        checkEq(Sgf.parse("(;GM[1]SZ[9]HA[2]AB[cc][gg];W[ee])").firstColor, Board.WHITE,
                "HA[2] 首手是白 → 白先");
        checkEq(Sgf.parse("(;GM[1]SZ[9]PL[W])").firstColor, Board.WHITE, "显式 PL[W] → 白先");
        checkEq(Sgf.parse("(;GM[1]SZ[9]PL[B])").firstColor, Board.BLACK, "显式 PL[B] → 黑先");
        // 只有黑摆子 = 让子/题谱惯例白先。实测：真 KataGo 对 `AB[cc]` 0 手快照也报白先。
        checkEq(Sgf.parse("(;GM[1]SZ[9]AB[cc])").firstColor, Board.WHITE,
                "只有 AB 摆子、无 HA → 白先（与引擎实测一致）");
        checkEq(Sgf.parse("(;GM[1]SZ[9]AB[cc][gg]AW[ee])").firstColor, Board.BLACK,
                "AB+AW 都有 → 不是让子，黑先（与引擎实测一致）");
        // 该谁走 = GameReview.sideToMoveOf（MainActivity.sideToMove 走的也是它，同一判据）。
        // 审计员第 14 轮 F1：这里原来留了个私有的 sideAfter()，是纯奇偶的第二套写法，
        // 注释还写着「与 MainActivity 同一口径」——已经失真，且它对同一输入 [B,B] 会给出
        // 与正式判据相反的答案。删掉它，直接用正式判据，免得下一个人照它写非交替用例。
        Sgf.Parsed haAfter = Sgf.parse("(;GM[1]SZ[9]HA[2]AB[cc][gg];W[ee])");
        checkEq(GameReview.sideToMoveOf(haAfter.board.moves, haAfter.firstColor, 1),
                Board.BLACK, "HA[2] 白走一手后轮到黑");
        Sgf.Parsed haZero = Sgf.parse("(;GM[1]SZ[9]HA[2]AB[cc][gg])");
        checkEq(GameReview.sideToMoveOf(haZero.board.moves, haZero.firstColor, 0),
                Board.WHITE, "HA[2] 0 手该白走（旧写法给黑，白方白送一手）");
    }

    private static String repeat(String unit, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(unit);
        return sb.toString();
    }

    private static String brief(String s) {
        String t = s.replace("\n", "").replace("\r", "");
        return t.length() <= 12 ? t : t.substring(0, 12) + "…";
    }

    /** 用真实棋谱做一次端到端读取（有的话）。 */
    static void testRealGame(String path) throws Exception {
        java.io.File f = new java.io.File(path);
        if (!f.isFile()) {
            System.out.println("（跳过真实棋谱：" + path + " 不存在）");
            return;
        }
        System.out.println("真实棋谱读取");
        String text = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
        Sgf.Parsed p = Sgf.parse(text);
        System.out.println("  " + path + " -> " + p.board.size + " 路，" + p.board.moves.size()
                + " 手，结果 " + p.result());
        check(p.board.size == 19, "真实棋谱盘面 19 路");
        check(p.board.moves.size() > 100, "真实棋谱手数合理（" + p.board.moves.size() + "）");
        // 全部手顺都能原样重放，说明解析没有吞手或错位
        Board replay = new Board(p.board.size, p.board.komi);
        for (int[] m : p.board.moves) replay.play(m[0], m[1]);
        check(Arrays.equals(replay.grid, p.board.grid), "重放棋谱得到同一盘面");
    }

    static void testBoardBasics() {
        System.out.println("棋盘基础");
        {
            Board b = new Board(9, 7.5);
            b.play(Board.BLACK, Board.PASS);
            checkEq(b.moves.size(), 1, "虚手记入历史");
            checkEq(b.moves.get(0)[1], Board.PASS, "虚手用 PASS 标记");
        }
        {
            Board b = new Board(9, 7.5);
            b.play(Board.BLACK, gp("E5", 9));
            Board c = b.copy();
            c.play(Board.WHITE, gp("E6", 9));
            checkEq(b.moves.size(), 1, "copy() 是深拷贝（原盘不受影响）");
            checkEq(c.moves.size(), 2, "copy() 可独立落子");
        }
        {
            Board b = new Board(9, 7.5);
            boolean threw = false;
            try {
                new Board(1, 7.5);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(threw, "非法盘面尺寸被拒绝");
        }
    }

    /**
     * 解析器测试：用真机跑出来的 kata-genmove_analyze 原始输出当 fixture。
     * fixture 是 GtpEngine.send() 的返回值形态（去掉首行 '=' 与结尾空行）。
     */
    static void testAnalysis(String fixturePath) throws Exception {
        System.out.println("分析输出解析");
        String body = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(fixturePath)), java.nio.charset.StandardCharsets.UTF_8);

        Analysis.Result r = Analysis.parse(body);
        check(r.moves.size() > 0, "解析出候选点，实际 " + r.moves.size() + " 个");

        // 去重：kata-genmove_analyze 会把同一候选点反复上报（流式输出），
        // 不去重的话一次搜索能堆出上千条、每条还带完整 PV —— 纯浪费内存。
        int rawChunks = 0;
        int from = 0;
        while ((from = body.indexOf("info move ", from)) >= 0) { rawChunks++; from += 10; }
        java.util.Set<String> seen = new java.util.HashSet<>();
        boolean dup = false;
        for (Analysis.MoveInfo m : r.moves) if (!seen.add(m.move)) dup = true;
        check(!dup, "同一候选点只保留一条（去重生效）");
        check(rawChunks > r.moves.size(),
                "去重确实减少了条目：原始 info " + rawChunks + " 条 -> 唯一 " + r.moves.size() + " 条");
        checkEq(r.moves.size(), seen.size(), "moves 里没有重复 move");
        checkEq(r.playedMove, "F5", "末尾 play F5 被识别为实际落子");

        Analysis.MoveInfo top = r.top();
        check(top != null, "存在首选点");
        checkEq(top.order, 0, "首选点 order = 0");
        check(top.winrate > 0 && top.winrate < 1,
                "根胜率在 (0,1) 内，实际 " + top.winrate);
        check(top.winrate == r.rootWinrate(), "rootWinrate 取的是首选点胜率");
        check(!Double.isNaN(top.scoreLead), "scoreLead 解析成功");
        check(top.visits > 0, "首选点 visits > 0，实际 " + top.visits);
        check(top.pv != null && top.pv.length() > 0, "PV 非空");

        // 末行 info 是最完整的，其首选点访问量应不低于更早的行
        List<Analysis.MoveInfo> byVisits = r.byVisits();
        check(byVisits.get(0).visits >= byVisits.get(byVisits.size() - 1).visits,
                "byVisits 按访问量降序");

        // 每个候选点都应能转成合法坐标（9 路）
        int okCount = 0;
        for (Analysis.MoveInfo m : r.moves) {
            int p = Analysis.toPoint(m.move, 9);
            if (p == Board.PASS || (p >= 0 && p < 81)) okCount++;
        }
        checkEq(okCount, r.moves.size(), "所有候选点坐标可转换");

        checkEq(Analysis.toPoint("pass", 9), Board.PASS, "pass 转 PASS");
        checkEq(Analysis.toPoint("resign", 9), Board.PASS, "resign 转 PASS");
        checkEq(Analysis.toPoint("Q16", 19), Board.gtpToPoint("Q16", 19), "Q16 正常转换");

        // 空响应不应崩
        Analysis.Result empty = Analysis.parse("");
        check(empty.moves.isEmpty() && empty.playedMove == null, "空响应安全");
    }


    /** 明暗判定的边界：最容易被写反的就是 19:00 和 07:00 这两个端点。 */
    private static void testTheme() {
        // 强制模式与系统设置无关
        check(!Theme.isDark(Theme.LIGHT, true, 23), "强制白天时系统是深色也不该变深");
        check(Theme.isDark(Theme.DARK, false, 12), "强制黑夜时系统是浅色也该是深色");
        // 跟随系统
        check(Theme.isDark(Theme.SYSTEM, true, 12), "跟随系统·系统深色");
        check(!Theme.isDark(Theme.SYSTEM, false, 23), "跟随系统·系统浅色");
        // 按时间自动：夜间是 [19:00, 07:00)
        check(Theme.isNightHour(19), "19:00 起算夜间");
        check(Theme.isNightHour(23), "23:00 是夜间");
        check(Theme.isNightHour(0), "0:00 是夜间");
        check(Theme.isNightHour(6), "6:00 是夜间");
        check(!Theme.isNightHour(7), "7:00 已经算白天");
        check(!Theme.isNightHour(18), "18:00 还是白天");
        for (int h = 0; h < 24; h++) {
            check(Theme.isDark(Theme.AUTO, false, h) == Theme.isNightHour(h), "自动模式逐时判定 h=" + h);
        }
        // 「按时间自动」不能被系统设置带偏，否则这个模式就没意义了
        check(!Theme.isDark(Theme.AUTO, true, 12), "自动模式下中午即使系统深色也应是白天");
        check(Theme.isDark(Theme.AUTO, false, 22), "自动模式下夜里即使系统浅色也该是深色");
        // 夜间一共 12 个小时（19..23 五个 + 0..6 七个）
        int night = 0;
        for (int h = 0; h < 24; h++) if (Theme.isNightHour(h)) night++;
        checkEq(night, 12, "夜间小时数");
        // 坏值兜底
        checkEq(Theme.clamp(-1), Theme.SYSTEM, "负数模式回退跟随系统");
        checkEq(Theme.clamp(99), Theme.SYSTEM, "越界模式回退跟随系统");
        checkEq(Theme.clamp(Theme.AUTO), Theme.AUTO, "合法模式不被改动");
        checkEq(Theme.label(Theme.DARK), "黑夜", "模式名");
        checkEq(Theme.LABELS.length, Theme.MODE_COUNT, "标签数和模式数一致");
    }


    /**
     * 回归：Analysis.top() 不能在候选集收缩时返回「过期首选」。
     *
     * kata-genmove_analyze 会分多轮上报候选。某一轮里是 order 0 的点，
     * 下一轮不再被上报，它会带着过期的 order 0 留在表里；等新一轮的首选也是
     * order 0 时，只比 order、并列取先插入者的实现就会把那个旧点返回出去。
     */
    private static void testAnalysisTopNotStale() {
        Analysis.Result r = Analysis.parse(
                "info move A1 visits 10 winrate 0.50 order 0 pv A1\n"
                + "info move B1 visits 90 winrate 0.80 order 0 pv B1\n"
                + "play B1");
        checkEq(r.moves.size(), 2, "两个候选都保留");
        checkEq(r.top().move, "B1", "order 并列时取最近上报的首选，而不是过期的那条");
        check(Math.abs(r.rootWinrate() - 0.80) < 1e-9, "根胜率跟着取最近上报的首选");
        checkEq(r.playedMove, "B1", "实际落子仍取自 play 行");

        // order 仍然优先，和上报先后无关
        Analysis.Result r2 = Analysis.parse(
                "info move A1 visits 10 winrate 0.50 order 1\n"
                + "info move B1 visits 90 winrate 0.80 order 0");
        checkEq(r2.top().move, "B1", "order 小的优先于上报更晚的");

        // 同一个点重复上报：只留一条，且取最新
        Analysis.Result r3 = Analysis.parse(
                "info move A1 visits 10 winrate 0.50 order 0\n"
                + "info move A1 visits 50 winrate 0.60 order 0");
        checkEq(r3.moves.size(), 1, "同一个点只保留一条");
        check(Math.abs(r3.rootWinrate() - 0.60) < 1e-9, "重复上报同一候选时取最新一条");

        // 候选变多时，最大访问量的那条仍能通过 byVisits 拿到（top 不该影响它）
        Analysis.Result r4 = Analysis.parse(
                "info move A1 visits 10 winrate 0.50 order 0\n"
                + "info move C1 visits 500 winrate 0.70 order 1");
        checkEq(r4.top().move, "A1", "order 0 仍然是首选");
        checkEq(r4.byVisits().get(0).move, "C1", "byVisits 仍按访问量排序");
    }

    /** 半透明色合成到底色上（描边是带 alpha 的）。 */
    private static int over(int argb, int bg) {
        int a = (argb >>> 24) & 0xFF;
        int r = (((argb >> 16) & 0xFF) * a + ((bg >> 16) & 0xFF) * (255 - a)) / 255;
        int g = (((argb >> 8) & 0xFF) * a + ((bg >> 8) & 0xFF) * (255 - a)) / 255;
        int b = ((argb & 0xFF) * a + (bg & 0xFF) * (255 - a)) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * 配色不能只靠「看着还行」。这里把几条硬要求钉住：
     * 文字对比度够、深色棋盘上一黑一白两色棋子都分得出来、胜率曲线的线在底色上看得见。
     */
    private static void testPalette() {
        Palette light = Palette.of(false);
        Palette dark = Palette.of(true);
        check(!light.dark && dark.dark, "两套配色的 dark 标记");
        check(light.background != dark.background && light.text != dark.text, "两套配色确实不同");

        // 深色就是要更暗、字要更亮
        check(Palette.luminance(dark.background) < Palette.luminance(light.background), "深色底色更暗");
        check(Palette.luminance(dark.text) > Palette.luminance(light.text), "深色模式文字更亮");
        check(Palette.luminance(dark.boardWood) < Palette.luminance(light.boardWood), "深色棋盘更暗");

        // 正文对比度（WCAG AA 要求 4.5:1）
        check(Palette.contrast(light.text, light.background) >= 4.5, "浅色正文对比度");
        check(Palette.contrast(dark.text, dark.background) >= 4.5, "深色正文对比度");
        // 次要文字（AA 大字号 3:1）
        check(Palette.contrast(light.textDim, light.background) >= 3.0, "浅色次要文字对比度");
        check(Palette.contrast(dark.textDim, dark.background) >= 3.0, "深色次要文字对比度");
        // 按钮文字
        check(Palette.contrast(light.buttonText, light.buttonBg) >= 4.5, "浅色按钮文字对比度");
        check(Palette.contrast(dark.buttonText, dark.buttonBg) >= 4.5, "深色按钮文字对比度");

        // 棋盘上两色棋子都要分得清（黑子 #0A0A0A、白子 #FFFFFF）
        check(Palette.contrast(light.boardWood, 0xFF0A0A0A) >= 3.0, "浅色棋盘上黑子可见");
        check(Palette.contrast(light.boardWood, 0xFFFFFFFF) >= 1.2, "浅色棋盘上白子可见");
        check(Palette.contrast(dark.boardWood, 0xFF0A0A0A) >= 2.0, "深色棋盘上黑子可见");
        check(Palette.contrast(dark.boardWood, 0xFFFFFFFF) >= 1.2, "深色棋盘上白子可见");
        // 关键一条：深色模式下黑子和棋盘本来就接近，全靠描边，描边必须真的看得见
        check(Palette.contrast(over(dark.boardStoneEdge, dark.boardWood), dark.boardWood) >= 1.5,
                "深色模式棋子描边可见（否则黑子糊在棋盘里）");
        check(Palette.contrast(over(light.boardStoneEdge, light.boardWood), light.boardWood) >= 1.1,
                "浅色模式棋子描边可见");
        check(Palette.luminance(dark.boardStoneEdge) > Palette.luminance(light.boardStoneEdge),
                "深色模式的棋子描边要比浅色模式亮");

        // 复盘图：曲线/游标在底色上要看得见
        check(Palette.contrast(light.chartLine, light.chartBg) >= 3.0, "浅色胜率曲线可见");
        check(Palette.contrast(dark.chartLine, dark.chartBg) >= 3.0, "深色胜率曲线可见");
        check(Palette.contrast(light.chartCursor, light.chartBg) >= 3.0, "浅色游标可见");
        check(Palette.contrast(dark.chartCursor, dark.chartBg) >= 3.0, "深色游标可见");
        check(Palette.contrast(dark.chartGrid, dark.chartBg) >= 1.2, "深色网格线可见");
        check(Palette.contrast(light.chartGrid, light.chartBg) >= 1.2, "浅色网格线可见");

        // 胜率条旁边的说明文字、以及「等待数据」的灰条
        check(Palette.contrast(light.barLabel, light.background) >= 3.0, "浅色胜率说明文字对比度");
        check(Palette.contrast(dark.barLabel, dark.background) >= 3.0, "深色胜率说明文字对比度");
        check(Palette.luminance(dark.barEmpty) < Palette.luminance(light.barEmpty), "深色等待条更暗");

        // 状态面板底色和页面底色要能区分（否则「卡片」看不出来）
        check(Palette.contrast(light.surface, light.background) >= 1.05, "浅色面板可辨");
        check(Palette.contrast(dark.surface, dark.background) >= 1.05, "深色面板可辨");
        // 分割线在面板上要看得见
        check(Palette.contrast(light.divider, light.surface) >= 1.15, "浅色分割线可见");
        check(Palette.contrast(dark.divider, dark.surface) >= 1.15, "深色分割线可见");
    }

    /**
     * 复盘屏的盘面必须带上摆子（审计员第 12 轮 D1）。
     *
     * 真实现场：`HA[2]AB[cc][gg];W[ee];B[ff]`，第 2 手时盘上应 4 子，而复盘屏只用手顺重建
     * 就只有 2 子 —— 同一屏上曲线按「有摆子」的真值在说 4.8%，盘面却是空盘起，自相矛盾。
     */
    static void testViewBoard() {
        System.out.println("复盘屏的起点盘面");
        Sgf.Parsed sp = Sgf.parse("(;GM[1]FF[4]SZ[9]KM[7.5]RU[Chinese]HA[2]AB[cc][gg])");
        int[] grid = sp.board.grid;
        List<int[]> mv = new ArrayList<>();
        Sgf.Parsed full = Sgf.parse(
                "(;GM[1]FF[4]SZ[9]KM[7.5]RU[Chinese]HA[2]AB[cc][gg];W[ee];B[ff])");
        mv.addAll(full.board.moves);
        checkEq(mv.size(), 2, "棋谱 2 手");
        checkEq(countStones(GameReview.viewBoard(9, 7.5, grid, mv, 2)), 4, "第2手时盘上 4 子（2 摆子+2 手）");
        checkEq(countStones(GameReview.viewBoard(9, 7.5, grid, mv, 0)), 2, "第0手时盘上就是那 2 个摆子");
        checkEq(countStones(GameReview.viewBoard(9, 7.5, grid, mv, 1)), 3, "第1手时 3 子");
        // 反证：不传摆子（旧行为）→ 第0手是空盘、第2手只有 2 子
        checkEq(countStones(GameReview.viewBoard(9, 7.5, null, mv, 0)), 0, "不传摆子时第0手是空盘");
        checkEq(countStones(GameReview.viewBoard(9, 7.5, null, mv, 2)), 2, "不传摆子时只有手顺的 2 子");
        checkEq(GameReview.viewBoard(9, 7.5, grid, mv, 2).grid[Board.sgfToPoint("ee", 9)], Board.WHITE,
                "第1手白子在 ee");
        checkEq(GameReview.viewBoard(9, 7.5, grid, mv, 2).grid[Board.sgfToPoint("cc", 9)], Board.BLACK,
                "摆子在 cc");
        checkEq(countStones(GameReview.viewBoard(9, 7.5, null, null, 3)), 0, "空输入不炸");
        checkEq(countStones(GameReview.viewBoard(9, 7.5, grid, mv, 99)), 4, "越界只重放到最后一手");
    }

    private static int countStones(Board b) {
        int c = 0;
        for (int v : b.grid) if (v == Board.BLACK || v == Board.WHITE) c++;
        return c;
    }

    /** 复盘家族的行棋方/摆子解算（Lead 第 12 轮：整条家族都不许用 i%2 判先后手）。 */
    static void testReviewSides() {
        System.out.println("复盘的行棋方与摆子");
        List<int[]> normal = new ArrayList<>();
        normal.add(new int[]{Board.BLACK, gp("E5", 9)});
        normal.add(new int[]{Board.WHITE, gp("E6", 9)});
        normal.add(new int[]{Board.BLACK, gp("D5", 9)});
        checkEq(GameReview.firstColorOf(normal), Board.BLACK, "黑先谱：firstColor");
        checkEq(GameReview.sideToMoveOf(normal, Board.BLACK, 0), Board.BLACK, "黑先谱：第0点该黑");
        checkEq(GameReview.sideToMoveOf(normal, Board.BLACK, 1), Board.WHITE, "黑先谱：第1点该白");
        checkEq(GameReview.sideToMoveOf(normal, Board.BLACK, 3), Board.WHITE, "黑先谱：第3点该白");
        List<int[]> hd = new ArrayList<>();
        hd.add(new int[]{Board.WHITE, gp("E5", 9)});
        hd.add(new int[]{Board.BLACK, gp("E6", 9)});
        checkEq(GameReview.firstColorOf(hd), Board.WHITE, "白先谱：firstColor");
        checkEq(GameReview.sideToMoveOf(hd, Board.WHITE, 0), Board.WHITE, "白先谱：第0点该白");
        checkEq(GameReview.sideToMoveOf(hd, Board.WHITE, 1), Board.BLACK, "白先谱：第1点该黑");
        checkEq(GameReview.sideToMoveOf(hd, Board.WHITE, 2), Board.WHITE, "白先谱：第2点该白");
        // 空谱：只能靠外部告诉的先手方（题谱可能白先，0 手时 moves 里什么也没有）
        checkEq(GameReview.firstColorOf(new ArrayList<int[]>()), Board.BLACK, "空谱：firstColor 按黑");
        checkEq(GameReview.sideToMoveOf(new ArrayList<int[]>(), Board.WHITE, 0), Board.WHITE,
                "空谱：外部说白先就白先");
        checkEq(GameReview.worstMover(normal, 1), Board.WHITE, "最大失误归属：第1手是白下的");
        checkEq(GameReview.worstMover(hd, 0), Board.WHITE, "白先谱第0手是白下的");
        checkEq(GameReview.worstMover(normal, 9), Board.EMPTY, "越界返回 EMPTY");
        // 主对局的 sideToMove() 现在也走这条判据（E1）。下面这组是 [B,B]：最后一手是黑，
        // 接下来轮到白；旧的手数奇偶写法（size%2==0?先手:对手）在这里给黑 —— 正是审计员实测的差异。
        // 注意这条断言约束的是 GameReview.sideToMoveOf，**对 MainActivity 那一行没有约束力**
        // （编译单元里不含它）：E1 的接线只有代码走查，别把它当 E1 的回归测试。
        List<int[]> bb2 = new ArrayList<>();
        bb2.add(new int[]{Board.BLACK, 0});
        bb2.add(new int[]{Board.BLACK, 0});
        checkEq(GameReview.sideToMoveOf(bb2, Board.BLACK, bb2.size()), Board.WHITE,
                "非交替谱：B,B 之后轮到白（旧的奇偶写法在这里给黑 —— 审计员实测的差异）");
        // 分析点比手数多一格：不抛异常，按「最后一手之后该谁走」算
        // 越界不抛异常，答案是「最后一手之后该谁走」
        checkEq(GameReview.sideToMoveOf(normal, Board.BLACK, normal.size() + 1), Board.WHITE,
                "分析点越界不抛异常（B,W,B 之后轮到白）");
        checkEq(GameReview.sideToMoveOf(hd, Board.WHITE, hd.size() + 5), Board.WHITE,
                "白先谱：越界按最后一手之后算（W,B 之后轮到白）");

        // 摆子解算：AE 必须在写进快照前被吸收（否则真引擎整谱拒收，同步退回丢摆子）
        checkEq(Sgf.resolvedSetup("", 9), "", "空摆子原样");
        checkEq(Sgf.resolvedSetup("AB[cc][gg]", 9), "AB[cc][gg]", "没有 AE 就原样返回（保留简写）");
        checkEq(Sgf.resolvedSetup("AB[cc]AE[cc]", 9), "", "AB 被 AE 擦掉后什么都不剩");
        checkEq(Sgf.resolvedSetup("AB[cc][gg]AE[cc]", 9), "AB[gg]", "AE 只擦掉它指的那一子");
        checkEq(Sgf.resolvedSetup("AW[cc]AE[cc]AB[dd]", 9), "AB[dd]", "白的被擦、黑的留下");
        check(Sgf.resolvedSetup("AB[aabb]", 9).indexOf("AE") < 0, "打包坐标没有 AE 时原样返回");

        // 快照写 PL：引擎服从 PL，快照里的先后手才与 App 一致
        {
            Board b = new Board(9, 7.5);
            b.play(Board.BLACK, gp("E5", 9));
            String w = Sgf.toSgf(b, "A", "B", "", "", "", Board.BLACK);
            check(w.contains("PL[B]"), "显式黑先写 PL[B]");
            checkEq(Sgf.parse(w).firstColor, Board.BLACK, "带 PL[B] 的快照读回来还是黑先");
            // 注意：不会造「PL 说白先、首手却是黑」这种自相矛盾的快照 ——
            // 有手顺时首手颜色优先，PL 只在没有手顺时（题谱/让子谱 0 手）才起作用。
            String dflt = Sgf.toSgf(b, "A", "B", "");
            check(dflt.contains("PL[B]"), "默认按手顺推出 PL[B]");
            checkEq(Sgf.parse(dflt).firstColor, Board.BLACK, "默认快照读回来是黑先");
            // 只摆子没有手顺的白先题谱：必须靠 PL 传下去
            Board emptyBoard = new Board(9, 7.5);
            String setup = Sgf.toSgf(emptyBoard, "A", "B", "", "", "AB[cc]", Board.WHITE);
            check(setup.contains("PL[W]"), "题谱快照写 PL[W]");
            Sgf.Parsed back = Sgf.parse(setup);
            checkEq(back.firstColor, Board.WHITE, "题谱快照读回来是白先（PL 起作用）");
            checkEq(back.board.grid[Board.sgfToPoint("cc", 9)], Board.BLACK, "题谱快照的摆子读回来了");
            // AE 谱：写出去不许再有 AE，盘面必须还是「擦掉之后」的样子
            String ae = Sgf.toSgf(emptyBoard, "A", "B", "", "", "AB[cc]AE[cc]", Board.EMPTY);
            check(ae.indexOf("AE") < 0, "AE 不再写进快照：" + ae);
            Sgf.Parsed aeBack = Sgf.parse(ae);
            checkEq(aeBack.board.grid[Board.sgfToPoint("cc", 9)], Board.EMPTY,
                    "AE 的擦除效果保留（该点仍是空）");
            // 两边口径一致：toSgf 推 PL 用解算后的摆子，parse 判先后手也必须用同一份，
            // 否则「AB[cc]AE[cc]」（摆了又擦，最终无子）会出现 PL[B] 却读回白先的矛盾。
            // 直接解析「带 AE 的原文」才能测到 parse 侧的口径（写出的快照里 AE 已被解算掉）。
            // 不在 firstColorOf 里先解算的话，这里会把 AB[cc] 数成「只有黑摆子」而判白先，
            // 与 toSgf 推 PL 的口径（解算后无子 ⇒ 黑先）矛盾。
            checkEq(Sgf.parse("(;GM[1]SZ[9]AB[cc]AE[cc])").firstColor, Board.BLACK,
                    "摆了又擦的谱：两边都按黑先");
            checkEq(Sgf.parse("(;GM[1]SZ[9]AB[cc][gg]AE[cc])").firstColor, Board.WHITE,
                    "只剩一个黑摆子（gg）：仍按让子惯例判白先");
        }
    }

    public static void main(String[] args) throws Exception {
        testCoordinates();
        testCapture();
        testKo();
        testScoring();
        testSgf();
        testSgfParse();
        testSgfGuards();
        testReviewSides();
        testViewBoard();
        testBoardBasics();
        testGameReview();
        testLayoutMath();
        testBoardGeometry();
        testTheme();
        testPalette();
        testAnalysisTopNotStale();
        testVerdictNoData();
        if (args.length > 0) testAnalysis(args[0]);
        if (args.length > 1) testRealGame(args[1]);

        System.out.println();
        System.out.println("通过 " + passed + " 项，失败 " + failures.size() + " 项");
        if (!failures.isEmpty()) {
            System.out.println("失败清单：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }
}
