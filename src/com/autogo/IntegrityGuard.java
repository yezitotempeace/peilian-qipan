/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

/**
 * 完整性标记的**延迟判定器**（纯 Java，可单测）。
 *
 * <p><b>为什么延迟</b>：标记被改动时<b>不立刻</b>报错，而是先照常运行，等过了宽限期
 * （{@link #GRACE_MIN_HOURS}～{@link #GRACE_MAX_HOURS} 小时，具体值由固定种子算出、
 * 每台机器略有差异）再提示「不是正版」。这样有两个好处：</p>
 * <ul>
 *   <li>正常用户永远不会碰到（标记完整 ⇒ 永远 {@link #OK}）；</li>
 *   <li>改标记的人当场看不出问题，不容易立刻定位到是哪一处触发的。</li>
 * </ul>
 *
 * <p><b>如实说明边界</b>：这是「提高门槛」，不是不可破解的防护。本项目是开源的，
 * 谁都能改代码；这套机制保证的是「顺手删掉标记 ⇒ 编不过 / 过几天跑不起来」，
 * 挡不住铁了心重写校验逻辑的人。相关取舍见本地交接文档 HANDOVER.md。</p>
 *
 * <p>判定所需的四个时间点全部由调用方传入，本类不碰时钟、不碰存储，所以能在 JVM 上直接测。</p>
 */
public final class IntegrityGuard {

    /** 宽限期下限（小时）。 */
    public static final int GRACE_MIN_HOURS = 3;
    /** 宽限期上限（小时）。 */
    public static final int GRACE_MAX_HOURS = 72;

    /** 判定结果：一切正常。 */
    public static final int OK = 0;
    /** 判定结果：标记有问题，但还在宽限期内 —— 照常使用，不提示。 */
    public static final int GRACE = 1;
    /** 判定结果：宽限期已过 —— 提示「不是正版」并退出。 */
    public static final int EXPIRED = 2;

    /**
     * 宽限期长度（毫秒）。用固定种子做确定性伪随机，范围
     * [{@link #GRACE_MIN_HOURS}, {@link #GRACE_MAX_HOURS}] 小时。
     *
     * <p>种子取自 {@link BuildInfo} 的常量，所以同一版本每次算出来都一样；
     * 不同版本之间会变，避免被一眼看穿是个固定值。</p>
     */
    public static long graceMillis() {
        int seed = (BuildInfo.tag() + BuildInfo.rev() + BuildInfo.sig() + BuildInfo.BUILD_TAG).hashCode();
        int span = GRACE_MAX_HOURS - GRACE_MIN_HOURS + 1;          // 含两端
        int hours = GRACE_MIN_HOURS + Math.floorMod(seed, span);
        return hours * 3600_000L;
    }

    /**
     * 判定当前该怎么做。
     *
     * @param nowMs        当前时间
     * @param firstSeenMs  第一次启动的时间（0 = 还没记过）
     * @param badSinceMs   第一次发现标记异常的时间（0 = 从没发现过）
     * @param dueMs        预定报错的时间（0 = 还没排期）
     * @param markOk       本次标记校验是否通过
     * @return {@link #OK} / {@link #GRACE} / {@link #EXPIRED}
     */
    public static int decide(long nowMs, long firstSeenMs, long badSinceMs, long dueMs, boolean markOk) {
        if (markOk) {
            // 标记完整：不管之前记过什么异常，一律恢复正常（用户换回正版就该照常用）
            return OK;
        }
        if (badSinceMs <= 0 || dueMs <= 0) {
            // 第一次发现异常：还没排期，先当宽限期（调用方负责把 badSince/due 落盘）
            return GRACE;
        }
        return nowMs >= dueMs ? EXPIRED : GRACE;
    }

    /**
     * 第一次发现异常时，算出「什么时候该报错」。
     *
     * <p>额外加一条保护：如果设备时钟被往回拨（now 比 badSince 还早），
     * 就把起点当成 now，避免把到期时间算到很久以后、导致永远不报错。</p>
     */
    public static long dueAt(long nowMs, long badSinceMs) {
        long base = badSinceMs > 0 ? Math.min(badSinceMs, nowMs) : nowMs;
        return base + graceMillis();
    }

    /**
     * 第一次启动时间。用于「装了很久才发现异常」的情况：
     * 到期时间不会早于「首次启动 + 宽限期」，避免装完当天就弹。
     */
    public static long dueAt(long nowMs, long firstSeenMs, long badSinceMs) {
        long due = dueAt(nowMs, badSinceMs);
        if (firstSeenMs > 0) {
            long earliest = firstSeenMs + graceMillis();
            if (due < earliest) {
                due = earliest;
            }
        }
        return due;
    }

    private IntegrityGuard() {
    }
}
