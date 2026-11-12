/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

import com.autogo.Eula;

/**
 * 用户协议（学习版声明）：条款必须真的写明「学习用」和「禁止商业」，
 * 以及「没同意过 / 同意的是旧版本 ⇒ 必须再弹一次」。
 */
public class EulaTest {

    static int pass, fail;

    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got);
        }
    }

    /** 正文里必须出现的关键词：少一个这条协议就不算说到位。 */
    static final String[] MUST_HAVE = {
        "学习", "研究", "商业", "出售", "出租", "有偿分发",
        "应用商店", "版权声明", "AGPL-3.0", "现状", "担保", "同意", "退出",
    };

    public static void main(String[] args) {
        String text = Eula.text();

        // ① 正文非空、行数够（不是一句话糊过去）
        chk("正文非空", true, text.length() > 200);
        chk("条款行数 >= 8", true, Eula.LINES.length >= 8);

        // ② 关键词必须都在
        for (String k : MUST_HAVE) {
            chk("正文包含「" + k + "」", true, text.contains(k));
        }

        // ③ 「只是学习版、禁止商业」这句核心声明要明确
        chk("明确写了学习版/仅供学习", true,
                text.contains("仅供个人学习") || text.contains("仅供学习"));
        chk("明确写了禁止商业", true, text.contains("禁止") && text.contains("商业"));

        // ④ 同意门槛：没同意过必须弹
        chk("从未同意 ⇒ 显示协议", true, Eula.shouldShow(0));
        chk("同意当前版本 ⇒ 不再显示", false, Eula.shouldShow(Eula.VERSION));
        // ⑤ 同意过旧版本 ⇒ 必须再弹（改条款就要重新征求同意）
        if (Eula.VERSION > 1) {
            chk("同意的是旧版本 ⇒ 重新显示", true, Eula.shouldShow(Eula.VERSION - 1));
        }
        // ⑥ 存的值比当前版本还新（回滚安装等异常）⇒ 视为已同意，别卡死
        chk("存值比当前版本新 ⇒ 不显示", false, Eula.shouldShow(Eula.VERSION + 5));

        // ⑦ 版本号是正数（0 会被当成「从未同意」）
        chk("协议版本号 >= 1", true, Eula.VERSION >= 1);

        // ⑧ 有同意/拒绝两条明确出路，且拒绝要真退出
        chk("写了点同意才进", true, text.contains("我同意"));
        chk("写了不同意就退出", true, text.contains("不同意") && text.contains("退出"));

        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
