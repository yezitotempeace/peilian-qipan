/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

import com.autogo.JunqiAI;
import com.autogo.JunqiBoard;
import com.autogo.JunqiLearn;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

/** 军棋学习库：特征、加权、记整盘、持久化、淘汰。 */
public class JunqiLearnTest {

    static int pass, fail;

    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got);
        }
    }

    static JunqiLearn empty() {
        return JunqiLearn.load(new File("/tmp/junqi-learn-empty-" + System.nanoTime() + ".dat"));
    }

    public static void main(String[] args) {
        JunqiLearn learn = empty();
        chk("空库 0 局", 0, learn.games());
        chk("空库 0 特征", 0, learn.keys());
        chk("空库没有加权", 0, learn.bonus("a9tEz1dF"));

        String key = "a9tEz1dF";
        learn.recordGame(Collections.singletonList(key), JunqiLearn.WIN);
        learn.recordGame(Collections.singletonList(key), JunqiLearn.WIN);
        chk("样本不足（2 次）不加权", 0, learn.bonus(key));
        learn.recordGame(Collections.singletonList(key), JunqiLearn.WIN);
        chk("3 次全胜得到正加权", true, learn.bonus(key) > 0);
        chk("局数累加", 3, learn.games());
        chk("全胜记 3 个 wins", 3, learn.wins());
        chk("没有 losses", 0, learn.losses());

        for (int i = 0; i < 6; i++) {
            learn.recordGame(Collections.singletonList(key), JunqiLearn.LOSS);
        }
        chk("输多了变成负加权", true, learn.bonus(key) < 0);
        chk("又记 6 个 losses", 6, learn.losses());
        chk("总场数 9", 9, learn.games());

        // 持久化
        File f = new File("/tmp/junqi-learn-test-" + System.nanoTime() + ".dat");
        JunqiLearn save = empty();
        save.recordGame(Arrays.asList("k1", "k2"), JunqiLearn.WIN);
        save.recordGame(Arrays.asList("k1"), JunqiLearn.DRAW);
        save.save(f);
        JunqiLearn loaded = JunqiLearn.load(f);
        chk("存档后局数一致", save.games(), loaded.games());
        chk("存档后特征数一致", save.keys(), loaded.keys());
        chk("存档后加权一致", save.bonus("k1"), loaded.bonus("k1"));
        chk("存档后 wins 一致", save.wins(), loaded.wins());
        chk("存档后 losses 一致", save.losses(), loaded.losses());
        chk("存档后 draws 一致", save.draws(), loaded.draws());
        f.delete();

        // 容量淘汰
        JunqiLearn small = empty();
        small.setMaxKeys(2);
        small.recordGame(Arrays.asList("a", "b", "c"), JunqiLearn.WIN);
        chk("超过容量只留 2 个特征", 2, small.keys());

        // 特征：同一步棋稳定，进攻子不同则不同
        JunqiBoard b = new JunqiBoard(true);
        b.put(6, 1, JunqiBoard.RED, JunqiBoard.COMMANDER);
        b.put(6, 3, JunqiBoard.RED, JunqiBoard.ENGINEER);
        String k1 = JunqiLearn.keyFor(b, JunqiBoard.RED, new int[]{6, 1, 5, 1});
        String k2 = JunqiLearn.keyFor(b, JunqiBoard.RED, new int[]{6, 1, 5, 1});
        String k3 = JunqiLearn.keyFor(b, JunqiBoard.RED, new int[]{6, 3, 5, 3});
        chk("同一步特征稳定", k1, k2);
        chk("不同兵种特征不同", true, k1 != null && !k1.equals(k3));

        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
