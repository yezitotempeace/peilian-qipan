import com.autogo.XiangqiAI;
import com.autogo.XiangqiBoard;
import com.autogo.XiangqiReview;
import java.util.ArrayList;
import java.util.List;

/** 象棋复盘「走势」文案与未终局前缀的定点断言（第 96 轮审计 中-2 / 低-3 的回归钉）。 */
public class XiangqiTrendTest {

    static int pass, fail;

    static void chk(String what, Object want, Object got) {
        if (want.equals(got)) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got);
        }
    }

    static void chkTrue(String what, boolean got) {
        chk(what, Boolean.TRUE, Boolean.valueOf(got));
    }

    /** 只控制评估值、不控制着法的假引擎：红方视角恒 +S / 黑方视角恒 -S。 */
    static class Flat extends XiangqiAI {
        final int redScore;
        Flat(int redScore) {
            this.redScore = redScore;
        }
        @Override
        public int evaluate(XiangqiBoard b, int color) {
            return color == XiangqiBoard.RED ? redScore : -redScore;
        }
    }

    static List<int[]> opening4() {
        List<int[]> ms = new ArrayList<int[]>();
        ms.add(new int[]{7, 1, 7, 4});   // 红炮
        ms.add(new int[]{0, 1, 2, 2});   // 黑马
        ms.add(new int[]{9, 1, 7, 2});   // 红马
        ms.add(new int[]{0, 7, 2, 6});   // 黑马
        return ms;
    }

    public static void main(String[] args) {
        XiangqiReview.Review red = XiangqiReview.analyze(opening4(), new Flat(5000), XiangqiAI.FAST);
        chkTrue("红方一路领先 ⇒ 红方从头领先到尾", XiangqiReview.verdict(red).contains("红方从头领先到尾"));

        XiangqiReview.Review black = XiangqiReview.analyze(opening4(), new Flat(-5000), XiangqiAI.FAST);
        chkTrue("黑方一路领先 ⇒ 黑方从头领先到尾", XiangqiReview.verdict(black).contains("黑方从头领先到尾"));

        XiangqiReview.Review close = XiangqiReview.analyze(opening4(), new Flat(0), XiangqiAI.FAST);
        chkTrue("一直五五开 ⇒ 胜负到最后才见分晓", XiangqiReview.verdict(close).contains("胜负到最后才见分晓"));

        List<int[]> part = new ArrayList<int[]>();
        part.add(new int[]{7, 1, 7, 4});
        part.add(new int[]{0, 1, 2, 2});
        XiangqiReview.Review unfinished = XiangqiReview.analyze(part, new Flat(0), XiangqiAI.FAST);
        chkTrue("没下完的棋写「局面：」不写「终局：」", XiangqiReview.verdict(unfinished).startsWith("局面：对局进行中"));

        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
