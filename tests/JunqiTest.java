import com.autogo.JunqiBoard;
import java.util.List;

public class JunqiTest {
    static int pass, fail;
    static void chk(String w, Object e, Object a) {
        if (e == null ? a == null : e.equals(a)) pass++;
        else { fail++; System.out.println("FAIL " + w + " 期望 " + e + "，实际 " + a); }
    }
    static boolean has(List<int[]> ms, int fr, int fc, int tr, int tc) {
        int from = JunqiBoard.idx(fr, fc), to = JunqiBoard.idx(tr, tc);
        for (int[] m : ms) if (m[0] == from && m[1] == to) return true;
        return false;
    }
    static JunqiBoard b() { return new JunqiBoard(true); }

    public static void main(String[] args) {
        // 几何
        chk("大本营 (0,1)", true, JunqiBoard.isHq(0, 1));
        chk("(5,1) 非大本营", false, JunqiBoard.isHq(5, 1));
        chk("行营 (3,2)", true, JunqiBoard.isCamp(3, 2));
        chk("铁路 (7,0)", true, JunqiBoard.isRail(7, 0));
        chk("(2,2) 非铁路", false, JunqiBoard.isRail(2, 2));
        chk("坐标名", "C8", JunqiBoard.pointName(4, 2));
        // 摆子
        JunqiBoard full = new JunqiBoard();
        chk("红 25 子", 25, full.count(JunqiBoard.RED));
        chk("黑 25 子", 25, full.count(JunqiBoard.BLACK));
        // 走子
        JunqiBoard m = b();
        m.put(6, 0, JunqiBoard.RED, JunqiBoard.COMPANY);
        List<int[]> ms = m.legalMoves(JunqiBoard.RED);
        chk("一步向上", true, has(ms, 6, 0, 5, 0));
        chk("一步向右", true, has(ms, 6, 0, 6, 1));
        m.put(6, 1, JunqiBoard.RED, JunqiBoard.PLATOON);
        chk("不能吃自己的子", false, has(m.legalMoves(JunqiBoard.RED), 6, 0, 6, 1));
        // 铁路
        JunqiBoard r = b();
        r.put(5, 0, JunqiBoard.RED, JunqiBoard.PLATOON);
        chk("普通子铁路直行", true, has(r.legalMoves(JunqiBoard.RED), 5, 0, 5, 4));
        chk("普通子直行到 (1,0)（列 0 也是铁路）", true, has(r.legalMoves(JunqiBoard.RED), 5, 0, 1, 0));
        JunqiBoard t = b();
        t.put(5, 1, JunqiBoard.RED, JunqiBoard.PLATOON);
        chk("普通子不能拐弯（5,1 → 1,1 要拐两次）", false, has(t.legalMoves(JunqiBoard.RED), 5, 1, 1, 1));
        JunqiBoard e = b();
        e.put(5, 0, JunqiBoard.RED, JunqiBoard.ENGINEER);
        chk("工兵拐弯到 (1,0)", true, has(e.legalMoves(JunqiBoard.RED), 5, 0, 1, 0));
        chk("工兵拐到 (1,4)", true, has(e.legalMoves(JunqiBoard.RED), 5, 0, 1, 4));
        JunqiBoard eb = b();
        eb.put(5, 0, JunqiBoard.RED, JunqiBoard.ENGINEER);
        eb.put(5, 2, JunqiBoard.RED, JunqiBoard.PLATOON); // 己方棋子挡在铁路上
        List<int[]> ebm = eb.legalMoves(JunqiBoard.RED);
        chk("工兵不能落在自己的子上", false, has(ebm, 5, 0, 5, 2));
        JunqiBoard blk = b();
        blk.put(5, 0, JunqiBoard.RED, JunqiBoard.ENGINEER);
        blk.put(5, 1, JunqiBoard.RED, JunqiBoard.PLATOON);   // 铁路上横着己方棋子
        blk.put(4, 0, JunqiBoard.RED, JunqiBoard.PLATOON);   // 上下也堵住（都是铁路）
        blk.put(6, 0, JunqiBoard.RED, JunqiBoard.PLATOON);
        List<int[]> blkm = blk.legalMoves(JunqiBoard.RED);
        chk("工兵被己方棋子堵在铁路上（5,2 不可达）", false, has(blkm, 5, 0, 5, 2));
        chk("地雷与空地不是同一个值", true, JunqiBoard.MINE != JunqiBoard.EMPTY);

        // 踩雷：只死攻击者，地雷原地不动（且不能和空地混为一谈）
        JunqiBoard mine = b();
        try {
            java.lang.reflect.Field f = JunqiBoard.class.getDeclaredField("sideToMove");
            f.setAccessible(true);
            f.setInt(mine, JunqiBoard.BLACK);   // 直接轮到黑方打这一手（本测试只想验踩雷）
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        mine.put(0, 0, JunqiBoard.RED, JunqiBoard.MINE);
        mine.put(1, 0, JunqiBoard.BLACK, JunqiBoard.PLATOON);
        mine.play(1, 0, 0, 0);
        chk("踩雷后攻击者没了", JunqiBoard.EMPTY, mine.ownerAt(1, 0));
        chk("踩雷后地雷还在（owner 仍是红）", JunqiBoard.RED, mine.ownerAt(0, 0));
        chk("踩雷后地雷兵种仍是地雷", JunqiBoard.MINE, mine.at(0, 0));

        // 特殊格
        JunqiBoard h = b();
        h.put(11, 1, JunqiBoard.RED, JunqiBoard.COMMANDER);
        chk("大本营不能动", false, h.canMovePiece(JunqiBoard.idx(11, 1)));
        JunqiBoard mf = b();
        mf.put(6, 2, JunqiBoard.RED, JunqiBoard.MINE);
        mf.put(6, 3, JunqiBoard.RED, JunqiBoard.FLAG);
        chk("地雷不能动", false, mf.canMovePiece(JunqiBoard.idx(6, 2)));
        chk("军旗不能动", false, mf.canMovePiece(JunqiBoard.idx(6, 3)));
        JunqiBoard cp = b();
        cp.put(3, 1, JunqiBoard.RED, JunqiBoard.COMMANDER);
        cp.put(4, 1, JunqiBoard.BLACK, JunqiBoard.PLATOON); // (4,1) 是行营，与 (3,1) 相邻
        chk("行营里的敌子吃不到", false, has(cp.legalMoves(JunqiBoard.RED), 3, 1, 4, 1));
        // 战斗
        JunqiBoard win = b();
        win.put(6, 0, JunqiBoard.RED, JunqiBoard.COMMANDER);
        win.put(5, 0, JunqiBoard.BLACK, JunqiBoard.PLATOON);
        win.play(6, 0, 5, 0);
        chk("大吃小：占位", JunqiBoard.COMMANDER, win.at(5, 0));
        JunqiBoard lose = b();
        lose.put(6, 0, JunqiBoard.RED, JunqiBoard.PLATOON);
        lose.put(5, 0, JunqiBoard.BLACK, JunqiBoard.COMMANDER);
        lose.play(6, 0, 5, 0);
        chk("小吃大：进攻方死", JunqiBoard.EMPTY, lose.at(6, 0));
        JunqiBoard eq = b();
        eq.put(6, 0, JunqiBoard.RED, JunqiBoard.PLATOON);
        eq.put(5, 0, JunqiBoard.BLACK, JunqiBoard.PLATOON);
        eq.play(6, 0, 5, 0);
        chk("同级双亡", JunqiBoard.EMPTY, eq.at(5, 0));
        JunqiBoard bm = b();
        bm.put(6, 0, JunqiBoard.RED, JunqiBoard.BOMB);
        bm.put(5, 0, JunqiBoard.BLACK, JunqiBoard.COMMANDER);
        bm.play(6, 0, 5, 0);
        chk("炸弹同归于尽", JunqiBoard.EMPTY, bm.at(5, 0));
        JunqiBoard mg = b();
        mg.put(6, 0, JunqiBoard.RED, JunqiBoard.ENGINEER);
        mg.put(5, 0, JunqiBoard.BLACK, JunqiBoard.MINE);
        mg.play(6, 0, 5, 0);
        chk("工兵挖雷占位", JunqiBoard.ENGINEER, mg.at(5, 0));
        JunqiBoard mg2 = b();
        mg2.put(6, 0, JunqiBoard.RED, JunqiBoard.PLATOON);
        mg2.put(5, 0, JunqiBoard.BLACK, JunqiBoard.MINE);
        mg2.play(6, 0, 5, 0);
        chk("普通子踩雷阵亡", JunqiBoard.EMPTY, mg2.at(6, 0));
        chk("地雷还在", JunqiBoard.MINE, mg2.at(5, 0));
        JunqiBoard fl = b();
        fl.put(6, 0, JunqiBoard.RED, JunqiBoard.COMMANDER);
        fl.put(5, 0, JunqiBoard.BLACK, JunqiBoard.FLAG);
        fl.play(6, 0, 5, 0);
        chk("夺旗判胜", JunqiBoard.RED, fl.winner());
        chk("终局说明", "军旗被夺", fl.endReason());
        // 无子可走
        JunqiBoard nm = b();
        nm.put(6, 0, JunqiBoard.RED, JunqiBoard.COMMANDER);
        nm.put(5, 0, JunqiBoard.BLACK, JunqiBoard.COMMANDER);
        nm.play(6, 0, 5, 0);
        chk("对方无子可走判胜", JunqiBoard.RED, nm.winner());
        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}
