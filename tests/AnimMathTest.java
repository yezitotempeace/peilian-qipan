import com.autogo.AnimMath;

/** 动画纯数学：端点、钳制、缓动、插值、进度。 */
public class AnimMathTest {
    static int pass, fail;
    static void chk(String what, boolean ok) {
        if (ok) pass++; else { fail++; System.out.println("FAIL " + what); }
    }
    static boolean near(float a, float b) { return Math.abs(a - b) < 0.0001f; }

    public static void main(String[] args) {
        chk("clamp 下界", near(0f, AnimMath.clamp01(-0.5f)));
        chk("clamp 上界", near(1f, AnimMath.clamp01(2f)));
        chk("ease 起点 0", near(0f, AnimMath.easeOut(0f)));
        chk("ease 终点 1", near(1f, AnimMath.easeOut(1f)));
        chk("ease 中点大于线性（起步快）", AnimMath.easeOut(0.5f) > 0.5f);
        chk("lerp 中点", near(5f, AnimMath.lerp(0f, 10f, 0.5f)));
        chk("progress 未开始", near(0f, AnimMath.progress(1000L, 1000L, 200L)));
        chk("progress 走完", near(1f, AnimMath.progress(1000L, 1300L, 200L)));
        chk("progress 中途", near(0.5f, AnimMath.progress(1000L, 1100L, 200L)));
        chk("时长为 0 直接完成", near(1f, AnimMath.progress(1000L, 1000L, 0L)));
        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}
