/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

import com.autogo.XiangqiBoard;
import com.autogo.XiangqiEngine;
import com.autogo.XiangqiLearn;

import java.io.File;

/** 象棋学习库：特征/加权/记录/持久化，以及象棋引擎的坐标与 FEN 换算。 */
public class XiangqiLearnTest {

    static int pass, fail;

    /** 空学习库（走 load 拿新实例，构造器是私有的）。 */
    static XiangqiLearn empty() {
        return XiangqiLearn.load(new File("/tmp/xq-learn-empty-" + System.nanoTime() + ".dat"));
    }

    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got);
        }
    }

    public static void main(String[] args) throws Exception {
        XiangqiBoard b = new XiangqiBoard();

        // ① 特征：同一类走法产生同样的 key（跨局可复用）
        int[] mv1 = {7, 7, 7, 4};   // 红炮八平五
        String k1 = XiangqiLearn.keyFor(b, XiangqiBoard.BLACK, mv1);
        chk("特征非空", true, k1 != null && k1.length() > 0);
        XiangqiBoard b2 = new XiangqiBoard();
        String k2 = XiangqiLearn.keyFor(b2, XiangqiBoard.BLACK, mv1);
        chk("不同局的同类走法特征一致", k1, k2);

        // ② 区域：红方底线 = 0、过河到对方底线 = 4
        chk("红方底线区域", "0", XiangqiLearn.zoneOf(XiangqiBoard.RED, 9));
        chk("红方对方底线区域", "4", XiangqiLearn.zoneOf(XiangqiBoard.RED, 0));
        chk("黑方底线区域", "0", XiangqiLearn.zoneOf(XiangqiBoard.BLACK, 0));
        chk("黑方对方底线区域", "4", XiangqiLearn.zoneOf(XiangqiBoard.BLACK, 9));

        // ③ 过河判断
        chk("红方过河", true, XiangqiLearn.crossedRiver(XiangqiBoard.RED, 5, 4));
        chk("红方没过河", false, XiangqiLearn.crossedRiver(XiangqiBoard.RED, 4, 3));
        chk("黑方过河", true, XiangqiLearn.crossedRiver(XiangqiBoard.BLACK, 4, 5));

        // ④ 样本不足不给加权
        XiangqiLearn learn = empty();
        chk("无样本不加权", 0, learn.bonus(k1));

        // ⑤ 连赢 3 盘后给正加权
        java.util.List<String> keys = new java.util.ArrayList<String>();
        keys.add(k1);
        for (int i = 0; i < XiangqiLearn.MIN_SAMPLES; i++) {
            learn.recordGame(keys, XiangqiLearn.WIN);
        }
        chk("三连胜后有正加权", true, learn.bonus(k1) > 0);
        chk("局数统计", 3, learn.games());
        chk("胜场统计", 3, learn.wins());

        // ⑥ 连输会变成负加权
        XiangqiLearn lose = empty();
        for (int i = 0; i < XiangqiLearn.MIN_SAMPLES; i++) {
            lose.recordGame(keys, XiangqiLearn.LOSS);
        }
        chk("三连败后有负加权", true, lose.bonus(k1) < 0);

        // ⑦ 持久化往返
        File tmp = File.createTempFile("xqlearn", ".dat");
        tmp.deleteOnExit();
        learn.recordGame(keys, XiangqiLearn.DRAW);
        learn.save(tmp);
        XiangqiLearn loaded = XiangqiLearn.load(tmp);
        chk("存档后局数", learn.games(), loaded.games());
        chk("存档后特征数", learn.keys(), loaded.keys());
        chk("存档后加权一致", learn.bonus(k1), loaded.bonus(k1));

        // ⑧ 坏文件不崩
        File bad = File.createTempFile("xqlearn-bad", ".dat");
        bad.deleteOnExit();
        java.io.FileOutputStream fo = new java.io.FileOutputStream(bad);
        fo.write("garbage\nfoo\tbar\n".getBytes("UTF-8"));
        fo.close();
        XiangqiLearn badLoaded = XiangqiLearn.load(bad);
        chk("坏文件能加载", true, badLoaded != null);
        chk("缺文件能加载", true, XiangqiLearn.load(new File("/nonexistent/x.dat")) != null);

        // ⑨ 引擎坐标换算：h2e2 = 红炮八平五
        int[] mv = XiangqiEngine.parseMove(b, "h2e2");
        chk("h2e2 起点行", 7, mv[0]);
        chk("h2e2 起点列", 7, mv[1]);
        chk("h2e2 终点行", 7, mv[2]);
        chk("h2e2 终点列", 4, mv[3]);
        chk("h2e2 起点是红炮", XiangqiBoard.C, XiangqiBoard.typeOf(b.at(mv[0], mv[1])));
        chk("h2e2 起点是红方", XiangqiBoard.RED, XiangqiBoard.colorOf(b.at(mv[0], mv[1])));

        // ⑩ FEN：标准初始局面
        chk("初始 FEN", "fen rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1",
                XiangqiEngine.fenOf(b));

        // ⑪ 越界走法要报错（不能静默走错）
        boolean threw = false;
        try {
            XiangqiEngine.parseMove(b, "z9z9");
        } catch (RuntimeException e) {
            threw = true;
        }
        chk("越界走法抛异常", true, threw);

        
        // ---------- 体积上限：预算调小后，写出来的文件必须被裁到预算以内 ----------
        {
            XiangqiLearn l = empty();
            l.setMaxChars(600);
            for (int i = 0; i < 400; i++) {
                java.util.List<String> k = new java.util.ArrayList<String>();
                k.add("key" + i);
                l.recordGame(k, XiangqiLearn.WIN);
            }
            java.io.File capFile = java.io.File.createTempFile("cap", ".dat");
            capFile.deleteOnExit();
            l.save(capFile);
            chk("超预算会自动裁小", true, capFile.length() <= 600 * 3L);
            chk("裁完还剩数据（不是清空）", true, l.keys() > 0);
        }

System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
