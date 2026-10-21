/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

import com.autogo.GomokuBoard;
import com.autogo.GomokuLearn;
import com.autogo.LocalGomokuAI;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * GomokuLearn（经验库）的 JVM 单元测试（纯逻辑，不需要 Android）。
 *
 * 分六组：A 哈希 / B 记战绩 / C 胜率与挑着法 / D 你的失误与「引你入局」 / E 淘汰·存取·容错 / F 候选接口 topMoves。
 * 每条断言都有对应的「已知会失败的改法」记在 README 的反证清单里，跑法同其它测试：bash run_tests.sh。
 */
public class GomokuLearnTest {

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

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

    private static void checkNear(double actual, double expected, double tol, String what) {
        if (Math.abs(actual - expected) <= tol) {
            passed++;
        } else {
            failures.add(what + "（期望 " + expected + "，实际 " + actual + "）");
            System.out.println("  FAIL: " + what + "（期望 " + expected + "，实际 " + actual + "）");
        }
    }

    private static final int SZ = 9;

    private static List<int[]> moves(int... rowColColor) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i + 2 < rowColColor.length + 1; i += 3) {
            out.add(new int[]{rowColColor[i + 2], rowColColor[i] * SZ + rowColColor[i + 1]});
        }
        return out;
    }

    /** 黑五连的一盘（9 手）：黑 (7,3)~(7,7)，白 (0,0)~(0,3)。 */
    private static List<int[]> blackWin() {
        List<int[]> m = new ArrayList<>();
        int[][] seq = {{1, 7, 3}, {2, 0, 0}, {1, 7, 4}, {2, 0, 1}, {1, 7, 5}, {2, 0, 2},
                {1, 7, 6}, {2, 0, 3}, {1, 7, 7}};
        for (int[] s : seq) m.add(new int[]{s[0], s[1] * SZ + s[2]});
        return m;
    }

    private static List<int[]> whiteWin() {
        List<int[]> m = new ArrayList<>();
        int[][] seq = {{1, 7, 3}, {2, 0, 0}, {1, 8, 3}, {2, 0, 1}, {1, 8, 4}, {2, 0, 2},
                {1, 7, 4}, {2, 0, 3}, {1, 7, 5}, {2, 0, 4}};
        for (int[] s : seq) m.add(new int[]{s[0], s[1] * SZ + s[2]});
        return m;
    }

    private static GomokuBoard boardAfter(List<int[]> mv, int upto) {
        GomokuBoard b = new GomokuBoard(SZ, false);
        for (int i = 0; i < upto; i++) b.play(mv.get(i)[0], mv.get(i)[1]);
        return b;
    }

    private static int p(int row, int col) {
        return row * SZ + col;
    }

    // ---------- A 哈希 ----------

    private static void testHash() {
        List<int[]> m = blackWin();
        GomokuBoard b1 = boardAfter(m, 3);
        GomokuBoard b2 = boardAfter(m, 3);
        checkEq(GomokuLearn.hash(b1, GomokuBoard.BLACK), GomokuLearn.hash(b2, GomokuBoard.BLACK),
                "A1 同一局面同一轮次 ⇒ 同一个哈希");
        check(!GomokuLearn.hash(b1, GomokuBoard.BLACK).equals(GomokuLearn.hash(b1, GomokuBoard.WHITE)),
                "A2 同一局面、轮次不同 ⇒ 哈希不同（否则会把「我该走」和「对手该走」混成一回事）");
        GomokuBoard b3 = boardAfter(m, 4);
        check(!GomokuLearn.hash(b1, GomokuBoard.BLACK).equals(GomokuLearn.hash(b3, GomokuBoard.BLACK)),
                "A3 多一颗子 ⇒ 哈希不同");
        GomokuBoard big = new GomokuBoard(15, false);
        big.play(GomokuBoard.BLACK, 7 * 15 + 7);
        check(!GomokuLearn.hash(b1, GomokuBoard.BLACK).equals(GomokuLearn.hash(big, GomokuBoard.BLACK)),
                "A4 路数不同 ⇒ 哈希不同");
    }

    // ---------- B 记战绩 ----------

    private static void testRecordGame() {
        GomokuLearn g = new GomokuLearn();
        List<int[]> m = blackWin();
        check(g.recordGame(m, SZ, GomokuBoard.BLACK), "B0 正常手顺 ⇒ 记录成功");
        checkEq(g.games(), 1, "B1 累计盘数 = 1");

        String h0 = GomokuLearn.hash(boardAfter(m, 0), GomokuBoard.BLACK);
        GomokuLearn.MoveStats black1 = g.statsAt(h0, m.get(0)[1]);
        check(black1 != null, "B2 黑第一手的局面有记录");
        checkEq(black1 == null ? -1 : black1.wins, 1, "B3 赢家那一手记 win");
        checkEq(black1 == null ? -1 : black1.losses, 0, "B4 赢家那一手 loss = 0");

        String h1 = GomokuLearn.hash(boardAfter(m, 1), GomokuBoard.WHITE);
        GomokuLearn.MoveStats white1 = g.statsAt(h1, m.get(1)[1]);
        checkEq(white1 == null ? -1 : white1.losses, 1, "B5 输家那一手记 loss");

        String sameBoardAsBlack = GomokuLearn.hash(boardAfter(m, 1), GomokuBoard.BLACK);
        checkEq(g.statsAt(sameBoardAsBlack, m.get(1)[1]), null,
                "B6 只记「轮到自己走」的那个局面（同一盘面、轮到黑走时没有黑的下过记录）");

        GomokuLearn g2 = new GomokuLearn();
        g2.recordGame(blackWin(), SZ, GomokuBoard.EMPTY);
        GomokuLearn.MoveStats s = g2.statsAt(GomokuLearn.hash(boardAfter(blackWin(), 0), GomokuBoard.BLACK),
                blackWin().get(0)[1]);
        checkEq(s == null ? -1 : s.draws, 1, "B7 和棋记 draw");

        GomokuLearn g3 = new GomokuLearn();
        List<int[]> bad = new ArrayList<>();
        bad.add(new int[]{GomokuBoard.BLACK, p(7, 3)});
        bad.add(new int[]{GomokuBoard.WHITE, p(7, 3)});   // 下在已有子的点上
        check(!g3.recordGame(bad, SZ, GomokuBoard.BLACK), "B8 手顺非法 ⇒ 返回 false");
        checkEq(g3.games(), 0, "B9 手顺非法的盘不计入盘数");
        checkEq(g3.positions(), 0, "B10 手顺非法不留下半个局面");
    }

    // ---------- C 胜率与挑着法 ----------

    private static void testPick() {
        List<int[]> m = blackWin();
        GomokuBoard b = boardAfter(m, 3);
        int top = p(1, 1), alt = p(2, 2), third = p(3, 3);
        int[] ranked = {top, alt, third};
        GomokuLearn g = new GomokuLearn();

        checkEq(g.pick(b, GomokuBoard.BLACK, ranked, top), top, "C1 没有历史 ⇒ 听引擎的");
        checkEq(g.pick(b, GomokuBoard.BLACK, null, top), top, "C2 候选为空 ⇒ 听引擎的");
        checkEq(g.pick(b, GomokuBoard.BLACK, ranked, -1), -1, "C3 引擎说无处可下 ⇒ -1");

        // 造历史：在 b 这个局面上，alt 下过 3 次全赢
        String h = GomokuLearn.hash(b, GomokuBoard.BLACK);
        for (int i = 0; i < 3; i++) {
            List<int[]> mm = new ArrayList<>(m.subList(0, 3));
            mm.add(new int[]{GomokuBoard.BLACK, alt});
            mm.add(new int[]{GomokuBoard.WHITE, p(0, 5)});
            g.recordGame(mm, SZ, GomokuBoard.BLACK);
        }
        checkEq(g.statsAt(h, alt) == null ? -1 : g.statsAt(h, alt).plays, 3, "C4 样本数记到了 3");
        checkEq(g.pick(b, GomokuBoard.BLACK, ranked, top), alt,
                "C5 候选里有 3 战 3 胜的着法 ⇒ 优先用它（而不是引擎首选）");

        // 历史不好（3 战 1 胜）⇒ 回到引擎首选
        GomokuLearn g2 = new GomokuLearn();
        for (int i = 0; i < 3; i++) {
            List<int[]> mm = new ArrayList<>(m.subList(0, 3));
            mm.add(new int[]{GomokuBoard.BLACK, alt});
            mm.add(new int[]{GomokuBoard.WHITE, p(0, 5)});
            g2.recordGame(mm, SZ, i == 0 ? GomokuBoard.BLACK : GomokuBoard.WHITE);
        }
        checkEq(g2.pick(b, GomokuBoard.BLACK, ranked, top), top,
                "C6 历史胜率不到 60% ⇒ 不照经验走");

        // 样本不足（2 次）⇒ 也不走
        GomokuLearn g3 = new GomokuLearn();
        for (int i = 0; i < 2; i++) {
            List<int[]> mm = new ArrayList<>(m.subList(0, 3));
            mm.add(new int[]{GomokuBoard.BLACK, alt});
            mm.add(new int[]{GomokuBoard.WHITE, p(0, 5)});
            g3.recordGame(mm, SZ, GomokuBoard.BLACK);
        }
        checkEq(g3.pick(b, GomokuBoard.BLACK, ranked, top), top, "C7 样本只有 2 次 ⇒ 不照经验走");

        checkNear(new GomokuLearn.MoveStats().rate(), 0.5, 1e-9, "C8 没下过的落点胜率记 0.5");
        GomokuLearn.MoveStats st = new GomokuLearn.MoveStats();
        for (int i = 0; i < 3; i++) st.add(GomokuBoard.BLACK, GomokuBoard.BLACK);
        st.add(GomokuBoard.WHITE, GomokuBoard.BLACK);
        checkNear(st.rate(), 0.75, 1e-9, "C9 3 胜 1 负 ⇒ 胜率 0.75");
    }

    // ---------- D 你的失误与「引你入局」 ----------

    private static void testBlunders() {
        List<int[]> m = blackWin();
        GomokuLearn g = new GomokuLearn();
        double[] loss = {0.30, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
        checkEq(g.recordBlunders(m, SZ, GomokuBoard.BLACK, loss, 0.05), 1, "D1 只记第一手（只有它亏损超阈值）");
        String h0 = GomokuLearn.hash(boardAfter(m, 0), GomokuBoard.BLACK);
        checkEq(g.blunderCountAt(h0, m.get(0)[1]), 1, "D2 记在「走之前」的局面上、落点正确");
        checkEq(g.blunderCountAt(h0, m.get(0)[1] + 1), 0, "D3 别的落点没有记录");

        g.recordBlunders(m, SZ, GomokuBoard.BLACK, loss, 0.05);
        checkEq(g.blunderCountAt(h0, m.get(0)[1]), 2, "D4 同一局面同一落点再次失误 ⇒ 累计到 2");

        GomokuLearn g2 = new GomokuLearn();
        checkEq(g2.recordBlunders(m, SZ, GomokuBoard.WHITE, loss, 0.05), 0, "D5 亏损大的那一手是黑棋下的 ⇒ 只看人（白）时一条都不记");
        double[] small = {0.01, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
        checkEq(g2.recordBlunders(m, SZ, GomokuBoard.BLACK, small, 0.05), 0, "D6 亏损不到阈值 ⇒ 不记");
        double[] nan = {Double.NaN, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
        checkEq(g2.recordBlunders(m, SZ, GomokuBoard.BLACK, nan, 0.05), 0, "D7 亏损是 NaN ⇒ 不记");

        // 引你入局：候选 alt 走完之后，落进「你曾失误 2 次」的局面
        GomokuLearn g3 = new GomokuLearn();
        GomokuBoard b = boardAfter(m, 3);
        GomokuBoard child = boardAfter(m, 3);
        child.play(GomokuBoard.BLACK, p(2, 2));
        String childHash = GomokuLearn.hash(child, GomokuBoard.WHITE);
        List<int[]> trapSeq = new ArrayList<>(m.subList(0, 3));
        trapSeq.add(new int[]{GomokuBoard.BLACK, p(2, 2)});
        trapSeq.add(new int[]{GomokuBoard.WHITE, p(8, 8)});
        double[] trapLoss = {0.0, 0.0, 0.0, 0.0, 0.4};   // 第 5 手是白（人）下的，亏损 0.4
        for (int i = 0; i < 2; i++) g3.recordBlunders(trapSeq, SZ, GomokuBoard.WHITE, trapLoss, 0.05);
        checkEq(g3.blunderCountAt(childHash, p(8, 8)), 2, "D8 子局面里已有 2 次「你在这里失误」的底账");
        int[] ranked = {p(1, 1), p(2, 2), p(3, 3)};
        checkEq(g3.pick(b, GomokuBoard.BLACK, ranked, p(1, 1)), p(2, 2),
                "D9 候选里有一手能把你引到你常犯错的局面 ⇒ 优先走它（陪练）");

        GomokuLearn g4 = new GomokuLearn();
        List<int[]> bad2 = new ArrayList<>();
        bad2.add(new int[]{GomokuBoard.WHITE, p(0, 0)});
        bad2.add(new int[]{GomokuBoard.BLACK, p(0, 0)});   // 非法：下在已有子上
        double[] l2 = {0.9, 0.9};
        checkEq(g4.recordBlunders(bad2, SZ, GomokuBoard.WHITE, l2, 0.05), 0,
                "D10 手顺非法 ⇒ 失误一条都不记（不留半份数据）");
        checkEq(g4.blunderPositions(), 0, "D11 手顺非法后失误表仍是空的");
    }

    // ---------- E 淘汰 / 存取 / 容错 ----------

    private static void testStorage() throws Exception {
        GomokuLearn g = new GomokuLearn();
        g.setMaxPositions(3);
        for (int i = 0; i < 5; i++) {
            List<int[]> mm = new ArrayList<>();
            mm.add(new int[]{GomokuBoard.BLACK, p(1, i)});
            mm.add(new int[]{GomokuBoard.WHITE, p(3, i)});
            mm.add(new int[]{GomokuBoard.BLACK, p(2, i)});
            g.recordGame(mm, SZ, GomokuBoard.BLACK);
        }
        check(g.positions() <= 3, "E1 超过容量（3）后按「下得最少」淘汰（实际 " + g.positions() + " 个）");

        GomokuLearn src = new GomokuLearn();
        src.recordGame(blackWin(), SZ, GomokuBoard.BLACK);
        src.recordGame(whiteWin(), SZ, GomokuBoard.WHITE);
        double[] loss = {0.5};
        src.recordBlunders(blackWin().subList(0, 1), SZ, GomokuBoard.BLACK, loss, 0.05);
        File f = new File(System.getProperty("java.io.tmpdir"), "learn-test-" + System.nanoTime() + ".dat");
        src.save(f);
        check(f.isFile() && f.length() > 0, "E2 存盘写出文件（" + f.length() + " 字节）");
        GomokuLearn back = GomokuLearn.load(f);
        checkEq(back.games(), src.games(), "E3 读回来盘数一致");
        checkEq(back.positions(), src.positions(), "E4 读回来局面数一致");
        checkEq(back.totalRecords(), src.totalRecords(), "E5 读回来战绩条数一致");
        checkEq(back.blunderPositions(), src.blunderPositions(), "E6 读回来失误局面数一致");
        String h = GomokuLearn.hash(boardAfter(blackWin(), 0), GomokuBoard.BLACK);
        checkEq(back.statsAt(h, blackWin().get(0)[1]) == null ? -1 : back.statsAt(h, blackWin().get(0)[1]).wins,
                1, "E7 读回来单条战绩也对");
        f.delete();

        File bad = new File(System.getProperty("java.io.tmpdir"), "learn-bad-" + System.nanoTime() + ".dat");
        java.io.FileOutputStream out = new java.io.FileOutputStream(bad);
        out.write(("V1\ngames 7\n这不是一行\npos 1234567890abcdef 12:3:2:1:0,junk\n"
                + "pos abcdef0123456789 42:4:3:1:0\n").getBytes("UTF-8"));
        out.close();
        GomokuLearn toler = GomokuLearn.load(bad);
        checkEq(toler.games(), 7, "E8 坏行不影响好行（盘数照读）");
        checkEq(toler.positions(), 1, "E9 只有自洽的那一行被收下");
        bad.delete();

        GomokuLearn missing = GomokuLearn.load(new File("/tmp/definitely-not-here-" + System.nanoTime()));
        checkEq(missing.games(), 0, "E10 文件不存在 ⇒ 当空库，不抛异常");

        src.clear();
        checkEq(src.positions(), 0, "E11 clear() 清空局面");
        checkEq(src.blunderPositions(), 0, "E12 clear() 清空失误记录");
        checkEq(src.games(), 0, "E13 clear() 归零盘数");
    }

    // ---------- F 候选接口 ----------

    private static void testTopMoves() {
        LocalGomokuAI ai = new LocalGomokuAI();
        GomokuBoard empty = new GomokuBoard(SZ, false);
        int[] t0 = ai.topMoves(empty, GomokuBoard.BLACK, 5);
        checkEq(t0.length, 1, "F1 空盘只给一手（天元）");
        checkEq(t0[0], p(SZ / 2, SZ / 2), "F2 空盘给的是天元");

        List<int[]> four = new ArrayList<>();
        int[][] seq = {{1, 7, 3}, {2, 0, 0}, {1, 7, 4}, {2, 0, 1}, {1, 7, 5}, {2, 0, 2}, {1, 7, 6}};
        for (int[] s : seq) four.add(new int[]{s[0], s[1] * SZ + s[2]});
        GomokuBoard b4 = boardAfter(four, four.size());   // 黑有 4 连，轮到白
        int[] t1 = ai.topMoves(b4, GomokuBoard.WHITE, 5);
        checkEq(t1.length, 1, "F3 对手有四连 ⇒ 只给堵点（硬着法独占）");
        checkEq(t1[0], p(7, 2), "F4 堵的就是延长线上那一手");
        int[] t2 = ai.topMoves(b4, GomokuBoard.BLACK, 5);
        checkEq(t2.length, 1, "F5 自己有四连 ⇒ 只给成五点");
        check(t2[0] == p(7, 7) || t2[0] == p(7, 2), "F6 成五点就是这条连线的某一端（实际 " + t2[0] + "）");

        List<int[]> some = new ArrayList<>();
        some.add(new int[]{GomokuBoard.BLACK, p(4, 4)});
        some.add(new int[]{GomokuBoard.WHITE, p(4, 5)});
        GomokuBoard mid = boardAfter(some, some.size());
        int[] t3 = ai.topMoves(mid, GomokuBoard.BLACK, 5);
        checkEq(t3.length, 5, "F7 普通局面给 5 个候选");
        boolean allEmpty = true, distinct = true;
        for (int i = 0; i < t3.length; i++) {
            if (mid.grid[t3[i]] != GomokuBoard.EMPTY) allEmpty = false;
            for (int j = i + 1; j < t3.length; j++) if (t3[i] == t3[j]) distinct = false;
        }
        check(allEmpty, "F8 候选都是空点");
        check(distinct, "F9 候选互不重复");

        GomokuBoard over = boardAfter(blackWin(), blackWin().size());
        checkEq(ai.topMoves(over, GomokuBoard.WHITE, 3).length, 0, "F10 终局后不给候选");
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();
        System.out.println("GomokuLearn 单元测试");
        testHash();
        testRecordGame();
        testPick();
        testBlunders();
        testStorage();
        testTopMoves();
        System.out.println("通过 " + passed + " 项，失败 " + failures.size() + " 项（"
                + (System.currentTimeMillis() - t0) + " ms）");
        if (!failures.isEmpty()) System.exit(1);
    }
}
