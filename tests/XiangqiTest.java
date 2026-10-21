/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

import com.autogo.XiangqiBoard;
import java.util.ArrayList;
import java.util.List;

/** 象棋规则引擎的 JVM 断言（不依赖 Android）。 */
public class XiangqiTest {
    static int checks = 0, fails = 0;

    static void eq(String what, Object exp, Object act) {
        checks++;
        if (!String.valueOf(exp).equals(String.valueOf(act))) {
            fails++;
            System.out.println("FAIL " + what + "（期望 " + exp + "，实际 " + act + "）");
        }
    }

    static void ok(String what, boolean cond) {
        checks++;
        if (!cond) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    /** 空盘夹具：两将都在（引擎在缺将时判「被将军」），中间一个红兵挡住将帅照面。 */
    static XiangqiBoard empty() {
        XiangqiBoard b = new XiangqiBoard(true);
        b.put(9, 4, XiangqiBoard.RED, XiangqiBoard.K);
        b.put(0, 4, XiangqiBoard.BLACK, XiangqiBoard.K);
        b.put(5, 4, XiangqiBoard.RED, XiangqiBoard.P);
        return b;
    }

    static int count(XiangqiBoard b) {
        int n = 0;
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                if (b.at(r, c) != 0) {
                    n++;
                }
            }
        }
        return n;
    }

    static List<int[]> from(XiangqiBoard b, int color, int r, int c) {
        List<int[]> out = new ArrayList<int[]>();
        for (int[] m : b.legalMoves(color)) {
            if (m[0] == r && m[1] == c) {
                out.add(m);
            }
        }
        return out;
    }

    static boolean has(List<int[]> ms, int tr, int tc) {
        for (int[] m : ms) {
            if (m[2] == tr && m[3] == tc) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        // ① 初始局面
        XiangqiBoard b = new XiangqiBoard();
        eq("初始棋子数", 32, count(b));
        eq("初始轮到红方", XiangqiBoard.RED, b.sideToMove());
        eq("初始手数", 0, b.moveCount());
        eq("编码 round-trip（红帅 = (RED<<4)|K）", 17, XiangqiBoard.RED * 16 + XiangqiBoard.K);
        eq("初始局面红方着法数", 44, b.legalMoves(XiangqiBoard.RED).size());
        eq("colName(0)", "A", XiangqiBoard.colName(0));
        eq("colName(8)", "I", XiangqiBoard.colName(8));

        // ② 车
        XiangqiBoard r = empty();
        r.put(8, 0, XiangqiBoard.BLACK, XiangqiBoard.R);
        ok("车能走到 (8,3)", has(from(r, XiangqiBoard.BLACK, 8, 0), 8, 3));
        ok("车能走到 (3,0)", has(from(r, XiangqiBoard.BLACK, 8, 0), 3, 0));
        r.put(8, 2, XiangqiBoard.BLACK, XiangqiBoard.P);
        ok("车不能跳子（有己方子挡）", !has(from(r, XiangqiBoard.BLACK, 8, 0), 8, 3));
        ok("被挡之前仍可走 (8,1)", has(from(r, XiangqiBoard.BLACK, 8, 0), 8, 1));

        XiangqiBoard r2 = empty();
        r2.put(8, 0, XiangqiBoard.BLACK, XiangqiBoard.R);
        r2.put(8, 2, XiangqiBoard.RED, XiangqiBoard.P);
        ok("车可以吃对方子（含吃子那一格）", has(from(r2, XiangqiBoard.BLACK, 8, 0), 8, 2));
        ok("吃子只到吃子那格，不能顺势越过去", !has(from(r2, XiangqiBoard.BLACK, 8, 0), 8, 3));

        // ③ 马与蹩马腿
        XiangqiBoard n = empty();
        n.put(9, 1, XiangqiBoard.RED, XiangqiBoard.N);
        eq("空盘马 3 步", 3, from(n, XiangqiBoard.RED, 9, 1).size());
        ok("马能走 (7,0)", has(from(n, XiangqiBoard.RED, 9, 1), 7, 0));
        n.put(8, 1, XiangqiBoard.RED, XiangqiBoard.P);
        eq("马腿被塞住后只剩 1 步", 1, from(n, XiangqiBoard.RED, 9, 1).size());
        ok("蹩马腿：去 (7,0) 不合法", !has(from(n, XiangqiBoard.RED, 9, 1), 7, 0));
        ok("蹩马腿：去 (7,2) 不合法", !has(from(n, XiangqiBoard.RED, 9, 1), 7, 2));
        ok("不蹩腿方向仍可走 (8,3)", has(from(n, XiangqiBoard.RED, 9, 1), 8, 3));

        // ④ 相与塞象眼、不过河
        XiangqiBoard e = empty();
        e.put(9, 2, XiangqiBoard.RED, XiangqiBoard.B);
        eq("空盘相 2 步", 2, from(e, XiangqiBoard.RED, 9, 2).size());
        e.put(8, 3, XiangqiBoard.RED, XiangqiBoard.P);
        eq("塞象眼后只剩 1 步", 1, from(e, XiangqiBoard.RED, 9, 2).size());
        ok("塞象眼：去 (7,4) 不合法", !has(from(e, XiangqiBoard.RED, 9, 2), 7, 4));

        XiangqiBoard e2 = empty();
        e2.put(5, 2, XiangqiBoard.RED, XiangqiBoard.B);
        ok("相不能过河：去 (3,4) 不合法", !has(from(e2, XiangqiBoard.RED, 5, 2), 3, 4));
        ok("相在河这边可走 (7,4)", has(from(e2, XiangqiBoard.RED, 5, 2), 7, 4));

        // ⑤ 士与帅的九宫限制
        XiangqiBoard a = empty();
        a.put(9, 3, XiangqiBoard.RED, XiangqiBoard.A);
        eq("仕在宫角只有 1 步", 1, from(a, XiangqiBoard.RED, 9, 3).size());
        ok("仕能走 (8,4)", has(from(a, XiangqiBoard.RED, 9, 3), 8, 4));

        XiangqiBoard a2 = empty();
        a2.put(7, 3, XiangqiBoard.RED, XiangqiBoard.A);
        ok("仕不能走出九宫（(7,3)->(6,2)）", !has(from(a2, XiangqiBoard.RED, 7, 3), 6, 2));

        XiangqiBoard k = empty();
        k.put(9, 4, XiangqiBoard.RED, XiangqiBoard.K);
        eq("帅在底线中间 3 步", 3, from(k, XiangqiBoard.RED, 9, 4).size());
        ok("帅不能走出九宫（(9,4)->(9,2) 越界）", !has(from(k, XiangqiBoard.RED, 9, 4), 9, 2));

        // ⑥ 炮：不吃子同车，吃子须一个炮架
        XiangqiBoard c = empty();
        c.put(5, 0, XiangqiBoard.RED, XiangqiBoard.C);
        c.put(5, 4, XiangqiBoard.BLACK, XiangqiBoard.P);
        ok("炮能平移到 (5,1)", has(from(c, XiangqiBoard.RED, 5, 0), 5, 1));
        ok("炮也能沿竖线走 (4,0)", has(from(c, XiangqiBoard.RED, 5, 0), 4, 0));
        ok("炮不能无架吃子", !has(from(c, XiangqiBoard.RED, 5, 0), 5, 4));
        c.put(5, 2, XiangqiBoard.RED, XiangqiBoard.P);
        ok("炮隔一个炮架可以吃子", has(from(c, XiangqiBoard.RED, 5, 0), 5, 4));
        c.put(5, 3, XiangqiBoard.RED, XiangqiBoard.P);
        ok("炮隔两个子不能吃子", !has(from(c, XiangqiBoard.RED, 5, 0), 5, 4));

        // ⑦ 兵/卒
        XiangqiBoard p = empty();
        p.put(6, 0, XiangqiBoard.RED, XiangqiBoard.P);
        eq("红兵过河前只有 1 步", 1, from(p, XiangqiBoard.RED, 6, 0).size());
        ok("红兵过河前不能横走", !has(from(p, XiangqiBoard.RED, 6, 0), 6, 1));
        p.put(4, 0, XiangqiBoard.RED, XiangqiBoard.P);
        eq("红兵过河后可前进 + 横走 = 2 步", 2, from(p, XiangqiBoard.RED, 4, 0).size());
        ok("红兵过河后能横走 (4,1)", has(from(p, XiangqiBoard.RED, 4, 0), 4, 1));
        ok("红兵不能后退", !has(from(p, XiangqiBoard.RED, 4, 0), 5, 0));

        XiangqiBoard pb = empty();
        pb.put(3, 0, XiangqiBoard.BLACK, XiangqiBoard.P);
        eq("黑卒过河前 1 步", 1, from(pb, XiangqiBoard.BLACK, 3, 0).size());
        pb.put(5, 0, XiangqiBoard.BLACK, XiangqiBoard.P);
        eq("黑卒过河后 2 步", 2, from(pb, XiangqiBoard.BLACK, 5, 0).size());

        // ⑧ 将帅照面
        XiangqiBoard f = new XiangqiBoard(true);
        f.put(9, 4, XiangqiBoard.RED, XiangqiBoard.K);
        f.put(0, 4, XiangqiBoard.BLACK, XiangqiBoard.K);
        ok("两边将帅同列且中间无子 ⇒ 照面", f.kingsFace());
        f.put(5, 4, XiangqiBoard.RED, XiangqiBoard.R);
        ok("中间有子 ⇒ 不照面", !f.kingsFace());
        ok("移开中间的子会照面 ⇒ 该着不合法", !has(from(f, XiangqiBoard.RED, 5, 4), 5, 3));

        // ⑨ 将军与将死
        XiangqiBoard ch = empty();
        ch.put(9, 4, XiangqiBoard.RED, XiangqiBoard.K);
        ch.put(0, 4, XiangqiBoard.BLACK, XiangqiBoard.K);
        ch.put(3, 4, XiangqiBoard.RED, XiangqiBoard.R);
        ok("红车直照黑将 ⇒ 黑方被将军", ch.inCheck(XiangqiBoard.BLACK));
        ok("红方未被将军", !ch.inCheck(XiangqiBoard.RED));

        XiangqiBoard mate = empty();
        mate.put(0, 4, XiangqiBoard.BLACK, XiangqiBoard.K);
        mate.put(0, 0, XiangqiBoard.RED, XiangqiBoard.R);
        mate.put(1, 0, XiangqiBoard.RED, XiangqiBoard.R);
        ok("双车闷杀：黑方被将军", mate.inCheck(XiangqiBoard.BLACK));
        ok("双车闷杀：黑方被将死", mate.isCheckmate(XiangqiBoard.BLACK));
        mate.play(1, 0, 1, 1);   // 红方走一步闲棋，把行棋权交给黑方
        ok("将死即终局", mate.gameOver());
        eq("将死的赢家是红方", XiangqiBoard.RED, mate.winner());
        ok("终局文案提到红胜", mate.resultString().contains("红"));

        // ⑩ 困毙（未被将军但无着可走）
        XiangqiBoard stale = empty();
        stale.put(0, 4, XiangqiBoard.BLACK, XiangqiBoard.K);
        stale.put(3, 3, XiangqiBoard.RED, XiangqiBoard.R);
        stale.put(3, 5, XiangqiBoard.RED, XiangqiBoard.R);
        stale.put(1, 0, XiangqiBoard.RED, XiangqiBoard.R);
        ok("困毙局面：黑方未被将军", !stale.inCheck(XiangqiBoard.BLACK));
        ok("困毙局面：黑方无着可走", stale.legalMoves(XiangqiBoard.BLACK).isEmpty());
        ok("困毙也判负（象棋规则）", stale.isStalemate(XiangqiBoard.BLACK));
        stale.play(1, 0, 1, 1);   // 同上
        ok("困毙即终局", stale.gameOver());
        eq("困毙的赢家是红方", XiangqiBoard.RED, stale.winner());

        // ⑪ 非法着法不改变局面
        XiangqiBoard ill = empty();
        ill.put(9, 0, XiangqiBoard.RED, XiangqiBoard.R);
        ill.put(7, 0, XiangqiBoard.RED, XiangqiBoard.P);
        ok("不能跳过己方子", !ill.play(9, 0, 7, 0));
        eq("非法着法不增加手数", 0, ill.moveCount());
        eq("非法着法不换手", XiangqiBoard.RED, ill.sideToMove());
        ok("合法着法返回 true", ill.play(9, 0, 8, 0));
        eq("合法着法手数 +1", 1, ill.moveCount());
        eq("合法着法后换手", XiangqiBoard.BLACK, ill.sideToMove());

        // ⑫ 不能吃将（终局应由将死判定）
        ok("吃将不是合法着法", !has(from(ch, XiangqiBoard.RED, 3, 4), 0, 4));

        // ⑬ copy 独立
        XiangqiBoard src = new XiangqiBoard();
        XiangqiBoard cp = src.copy();
        eq("copy 后手数一致", src.moveCount(), cp.moveCount());
        cp.play(9, 0, 8, 0);
        eq("改动副本不影响原局手数", 0, src.moveCount());
        eq("改动副本后副本手数 +1", 1, cp.moveCount());
        eq("原局仍是红先", XiangqiBoard.RED, src.sideToMove());

        System.out.println("通过 " + (checks - fails) + " 项，失败 " + fails + " 项");
        if (fails > 0) {
            System.exit(1);
        }
    }
}
