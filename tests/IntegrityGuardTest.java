/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

import com.autogo.BuildInfo;
import com.autogo.IntegrityGuard;

/**
 * 构建元数据 + 延迟报错判定器。
 *
 * <p>钉住三件事：① 编码字面量能正确还原成归属信息；② 标记被改时**不立刻**报错，
 * 而是进入宽限期，到期才报；③ 恢复正常后立刻回到 OK，且不会把到期时间算成负数/无限远。</p>
 */
public class IntegrityGuardTest {

    static int pass, fail;

    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got);
        }
    }

    public static void main(String[] args) {
        // ---------- ① 编码字面量能还原 ----------
        chk("作者还原", "yezitotempeace", BuildInfo.author());
        chk("年份还原", "2026", BuildInfo.year());
        chk("许可还原", "AGPL-3.0-only", BuildInfo.license());
        chk("仓库还原", "https://github.com/yezitotempeace/peilian-qipan", BuildInfo.repo());
        chk("所有者还原", "yezitotempeace", BuildInfo.owner());
        chk("仓库名还原", "peilian-qipan", BuildInfo.repoName());
        chk("标记段还原", "PQ", BuildInfo.tag());
        chk("版本段还原", "26", BuildInfo.rev());
        chk("签名段还原", "a3-r7", BuildInfo.sig());

        // ---------- ② 存档戳 ----------
        String stamp = BuildInfo.stamp();
        chk("戳含识别码", true, stamp.contains(BuildInfo.mark()));
        chk("戳含许可", true, stamp.contains("AGPL-3.0-only"));
        chk("头行以 # 开头", true, BuildInfo.header().startsWith("# "));
        chk("戳匹配自己", true, BuildInfo.Check.stamp(BuildInfo.header()));
        chk("戳不匹配别的", false, BuildInfo.Check.stamp("# OTHER/AGPL-3.0-only/2026/x"));
        chk("空行不匹配", false, BuildInfo.Check.stamp(null));

        // ---------- ③ 资源标记校验 ----------
        chk("四段全对才通过", true,
                BuildInfo.Check.mark("PQ", "26", "a3-r7", "github.com/yezitotempeace/peilian-qipan"));
        chk("tag 错即失败", false,
                BuildInfo.Check.mark("XX", "26", "a3-r7", "github.com/yezitotempeace/peilian-qipan"));
        chk("rev 错即失败", false,
                BuildInfo.Check.mark("PQ", "99", "a3-r7", "github.com/yezitotempeace/peilian-qipan"));
        chk("sig 错即失败", false,
                BuildInfo.Check.mark("PQ", "26", "zz-z9", "github.com/yezitotempeace/peilian-qipan"));
        chk("src 不含作者即失败", false, BuildInfo.Check.mark("PQ", "26", "a3-r7", "example.com"));
        chk("全空即失败", false, BuildInfo.Check.mark(null, null, null, null));

        // ---------- ④ 更新来源校验 ----------
        chk("本仓库 release 资产放行", true,
                BuildInfo.Check.fromOurRepo(
                        "https://github.com/yezitotempeace/peilian-qipan/releases/download/v198/a.apk"));
        chk("别人的仓库拒绝", false,
                BuildInfo.Check.fromOurRepo(
                        "https://github.com/someoneelse/peilian-qipan/releases/download/v198/a.apk"));
        chk("同名仓库不同主拒绝", false,
                BuildInfo.Check.fromOurRepo(
                        "https://github.com/yezitotempeace/fake-qipan/releases/download/v1/a.apk"));
        chk("非 release 路径拒绝", false,
                BuildInfo.Check.fromOurRepo("https://example.com/a.apk"));
        chk("null 拒绝", false, BuildInfo.Check.fromOurRepo(null));

        // ---------- ⑤ 延迟报错：标记正常永远 OK ----------
        long now = 1_000_000_000_000L;
        chk("标记正常 ⇒ OK", IntegrityGuard.OK,
                IntegrityGuard.decide(now, now - 86400_000L, 0, 0, true));
        chk("之前异常但已恢复 ⇒ OK", IntegrityGuard.OK,
                IntegrityGuard.decide(now, now - 86400_000L, now - 3600_000L, now + 3600_000L, true));

        // ---------- ⑥ 第一次发现异常：宽限期，不报错 ----------
        chk("首次异常 ⇒ GRACE（当场不报）", IntegrityGuard.GRACE,
                IntegrityGuard.decide(now, now - 86400_000L, 0, 0, false));

        // ---------- ⑦ 未到期仍宽限，到期才报 ----------
        long due = now + 3600_000L;
        chk("未到期 ⇒ GRACE", IntegrityGuard.GRACE,
                IntegrityGuard.decide(now, now - 86400_000L, now - 3600_000L, due, false));
        chk("已到期 ⇒ EXPIRED", IntegrityGuard.EXPIRED,
                IntegrityGuard.decide(due + 1, now - 86400_000L, now - 3600_000L, due, false));
        chk("整点到期 ⇒ EXPIRED", IntegrityGuard.EXPIRED,
                IntegrityGuard.decide(due, now - 86400_000L, now - 3600_000L, due, false));

        // ---------- ⑧ 宽限期长度合理（3 小时 ~ 72 小时） ----------
        long g = IntegrityGuard.graceMillis();
        chk("宽限期 >= 3 小时", true, g >= 3L * 3600_000L);
        chk("宽限期 <= 72 小时", true, g <= 72L * 3600_000L);
        chk("宽限期稳定（同版本每次都一样）", g, IntegrityGuard.graceMillis());

        // ---------- ⑨ 到期时间不会早于「首次启动 + 宽限期」 ----------
        long firstSeen = now - 1000L;                 // 刚装上 1 秒
        long d = IntegrityGuard.dueAt(now, firstSeen, now);
        chk("装完当天不弹", true, d >= firstSeen + IntegrityGuard.graceMillis());

        // ---------- ⑩ 时钟被往回拨也不会算到无限远 ----------
        long skewedNow = now - 10_000_000L;           // 用户把时间调回去了
        long d2 = IntegrityGuard.dueAt(skewedNow, 0, now /* badSince 比 now 晚 */);
        chk("时钟回拨后到期时间不失控", true, d2 <= skewedNow + IntegrityGuard.graceMillis() + 1);

        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
