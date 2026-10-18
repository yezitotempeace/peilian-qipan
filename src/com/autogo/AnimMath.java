package com.autogo;

/** 动画用的纯数学：缓动 + 插值。不依赖 Android，方便 JVM 单测。 */
public final class AnimMath {

    /** 普通走子动画时长（毫秒）。 */
    public static final long MOVE_MS = 220;
    /** 吃子闪光时长。 */
    public static final long FLASH_MS = 320;
    /** 彩蛋弹出时长。 */
    public static final long EGG_MS = 280;

    public static float clamp01(float p) {
        return p < 0f ? 0f : (p > 1f ? 1f : p);
    }

    /** ease-out cubic：起步快、收尾稳。 */
    public static float easeOut(float p) {
        float t = clamp01(p);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    public static float lerp(float a, float b, float p) {
        return a + (b - a) * clamp01(p);
    }

    /** 0→1 的进度（毫秒时间戳）。 */
    public static float progress(long startMs, long nowMs, long durationMs) {
        if (durationMs <= 0) {
            return 1f;
        }
        return clamp01((nowMs - startMs) / (float) durationMs);
    }

    private AnimMath() {
    }
}
