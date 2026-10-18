import com.autogo.JunqiAI;
import com.autogo.JunqiBoard;
import com.autogo.JunqiReveal;

/**
 * 军棋三档难度的**强度回归**：固定种子打若干局，确认「快 &lt; 普通 &lt; 强」没有被改回同级。
 * 这里只断言方向与最低差距（阈值留了余量），不追求精确 Elo。
 */
public class JunqiStrengthTest {

    static int pass, fail;

    static void chk(String what, boolean ok) {
        if (ok) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what);
        }
    }

    /** A/B 两档轮流执红打 games 局，返回 A 的得分率（和棋算 0.5）。 */
    static double scoreRate(int levelA, int levelB, int games, long baseSeed) {
        int aWins = 0, bWins = 0, draws = 0;
        for (int g = 0; g < games; g++) {
            long seed = baseSeed + g;
            JunqiBoard board = new JunqiBoard();
            board.setupStandard(new java.util.Random(seed));
            boolean[] revealed = new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
            JunqiAI a = new JunqiAI(levelA);
            JunqiAI b = new JunqiAI(levelB);
            int aColor = (g % 2 == 0) ? JunqiBoard.RED : JunqiBoard.BLACK;
            int moves = 0;
            while (!board.gameOver() && moves < 400) {
                int color = board.sideToMove();
                JunqiAI ai = (color == aColor) ? a : b;
                int[] mv = ai.genmove(board, color, revealed);
                if (mv == null) {
                    break;
                }
                JunqiBoard before = board.copy();
                if (!board.play(mv[0], mv[1], mv[2], mv[3])) {
                    break;
                }
                JunqiReveal.resolve(revealed, before,
                        new int[]{JunqiBoard.idx(mv[0], mv[1]), JunqiBoard.idx(mv[2], mv[3])});
                moves++;
            }
            int w = board.winner();
            if (w == 0) {
                draws++;
            } else if (w == aColor) {
                aWins++;
            } else {
                bWins++;
            }
        }
        return (aWins + 0.5 * draws) / games;
    }

    public static void main(String[] args) {
        double normalVsStrong = scoreRate(JunqiAI.NORMAL, JunqiAI.STRONG, 200, 5000L);
        double fastVsNormal = scoreRate(JunqiAI.FAST, JunqiAI.NORMAL, 80, 6000L);
        double fastVsStrong = scoreRate(JunqiAI.FAST, JunqiAI.STRONG, 40, 8100L);
        System.out.printf("军棋难度强度：普通 vs 强 = %.1f%%；快 vs 普通 = %.1f%%；快 vs 强 = %.1f%%%n",
                normalVsStrong * 100, fastVsNormal * 100, fastVsStrong * 100);
        chk("强档对普通档要有明显优势（普通得分率 <= 45%）", normalVsStrong <= 0.45);
        chk("普通档对快档要有明显优势（快得分率 <= 45%）", fastVsNormal <= 0.45);
        chk("强档对快档要有明显优势（快得分率 <= 35%）", fastVsStrong <= 0.35);
        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
