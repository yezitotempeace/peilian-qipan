import com.autogo.GomokuBoard;
import com.autogo.GomokuEngine;
import com.autogo.LocalGomokuAI;
import com.autogo.RapfiGomokuEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * RapfiGomokuEngine 的测试：拿真实的 Rapfi 可执行文件跑（JVM 里通过 Gomocup 协议对话）。
 *
 * <p>跑法：{@code java -cp cls RapfiGomokuTest [rapfi 可执行文件]}；不给参数时用系统属性
 * {@code autogo.rapfi}，再不行用 /tmp/gomoku-strong/out/pbrain-rapfi-linux-aarch64。
 * 找不到可执行文件 ⇒ 打印「错误：…」并 exit(2)（不装作通过）。
 */
public class RapfiGomokuTest {

    static int passed = 0;
    static int failed = 0;

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL: " + what);
        }
    }

    static void checkEq(Object actual, Object expected, String what) {
        boolean ok = (actual == null) ? expected == null : actual.equals(expected);
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL: " + what + "（期望 " + expected + "，实际 " + actual + "）");
        }
    }

    static void place(GomokuBoard b, int color, String pts) {
        for (String s : pts.split("\\s+")) {
            if (s.isEmpty()) {
                continue;
            }
            String[] rc = s.split(",");
            int r = Integer.parseInt(rc[0]);
            int c = Integer.parseInt(rc[1]);
            b.grid[b.idx(r, c)] = color;
        }
    }

    /** 按顺序走子（会置手顺 / 终局状态）。 */
    static void playAll(GomokuBoard b, int color, String pts) {
        for (String s : pts.split("\\s+")) {
            if (s.isEmpty()) {
                continue;
            }
            String[] rc = s.split(",");
            b.play(color, b.idx(Integer.parseInt(rc[0]), Integer.parseInt(rc[1])));
        }
    }

    static int idx(int r, int c) {
        return r * 15 + c;
    }

    public static void main(String[] args) {
        String bin = args.length > 0 ? args[0]
                : System.getProperty("autogo.rapfi", "/tmp/gomoku-strong/out/pbrain-rapfi-linux-aarch64");
        if (!new File(bin).canExecute()) {
            System.out.println("错误：找不到可执行的 Rapfi：" + bin);
            System.exit(2);
        }

        testContract(bin);
        testMappingRoundTrip();
        testEvaluateDelegates(bin);
        testEmptyBoard(bin);
        testWinNow(bin);
        testBlockFiveWhiteToMove(bin);
        testWinNowWhite(bin);
        testReadOnly(bin);
        testNoMoveAtTerminal(bin);
        testRestartAfterClose(bin);
        testRowOrderWinRace(bin);
        testWireOrder(bin);
        testHardTimeout(bin);
        testHalfMultibyteTimeout(bin);
        testRestartAfterTimeout(bin);
        testCloseKillsEngine(bin);
        testCloseWithBacklog(bin);
        testForcedFiveOddHands(bin);
        testNonFifteenBoard(bin);

        System.out.println("通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------- 用例

    static void testContract(String bin) {
        RapfiGomokuEngine e = new RapfiGomokuEngine(bin);
        check(e.name() != null && !e.name().isEmpty(), "名字非空");
        checkEq(e.level(), 1, "默认档位是普通（1）");
        e.setLevel(9);
        checkEq(e.level(), 1, "越界难度（9）回到普通（1）");
        e.setLevel(-3);
        checkEq(e.level(), 1, "越界难度（-3）回到普通（1）");
        e.setLevel(0);
        checkEq(e.level(), 0, "能设成快（0）");
        e.setLevel(2);
        checkEq(e.level(), 2, "能设成强（2）");
        try {
            new RapfiGomokuEngine("  ");
            check(false, "空路径应该抛 IllegalArgumentException");
        } catch (IllegalArgumentException ok) {
            check(true, "空路径抛 IllegalArgumentException");
        }
        RapfiGomokuEngine e2 = new RapfiGomokuEngine(bin);
        e2.setLevel(1);
        checkEq(e2.evaluate(new GomokuBoard(15, false), GomokuBoard.BLACK),
                new LocalGomokuAI().evaluate(new GomokuBoard(15, false), GomokuBoard.BLACK),
                "空盘评估与内置 AI 一致（evaluate 委托内置）");
    }

    static void testMappingRoundTrip() {
        int size = 15;
        int p = idx(11, 4);
        int[] xy = RapfiGomokuEngine.toEngine(size, p);
        checkEq(xy[0], 4, "引擎坐标 x = 列");
        checkEq(xy[1], size - 1 - 11, "引擎坐标 y = 行翻转（从下往上）");
        checkEq(RapfiGomokuEngine.toPoint(size, xy[0], xy[1]), p, "坐标往返一致");
    }

    static void testEvaluateDelegates(String bin) {
        RapfiGomokuEngine e = new RapfiGomokuEngine(bin);
        GomokuBoard b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "7,4 7,5 7,6");
        playAll(b, GomokuBoard.WHITE, "6,4 6,5");
        double forBlack = e.evaluate(b, GomokuBoard.BLACK);
        double forWhite = e.evaluate(b, GomokuBoard.WHITE);
        check(Math.abs(forBlack + forWhite) < 1e-9, "评估两边互为相反数");
        check(forBlack > 0, "黑棋有三连，评估为正（" + forBlack + "）");
    }

    static void testEmptyBoard(String bin) {
        RapfiGomokuEngine e = new RapfiGomokuEngine(bin);
        e.setLevel(0);
        GomokuBoard b = new GomokuBoard(15, false);
        int p = e.genmove(b, GomokuBoard.BLACK);
        check(p >= 0 && p < 225, "空盘给一个盘内的点（" + p + "）");
        check(b.isLegal(GomokuBoard.BLACK, p), "空盘给的点合法");
        e.close();
    }

    static void testWinNow(String bin) {
        RapfiGomokuEngine e = new RapfiGomokuEngine(bin);
        e.setLevel(0);
        GomokuBoard b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "11,0 11,1 11,2 11,3");
        playAll(b, GomokuBoard.WHITE, "10,0 10,1 10,2");
        check(!b.gameOver, "构造的局面还没结束");
        int p = e.genmove(b, GomokuBoard.BLACK);
        checkEq(p, idx(11, 4), "黑棋有冲四，强引擎直接成五");
        e.close();
    }

    static void testBlockFiveWhiteToMove(String bin) {
        RapfiGomokuEngine e = new RapfiGomokuEngine(bin);
        e.setLevel(0);
        GomokuBoard b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "11,1 11,2 11,3 11,4");
        playAll(b, GomokuBoard.WHITE, "11,0 9,9 9,10");
        checkEq(b.moves.size(), 7, "轮到白棋（黑 4 子、白 3 子）");
        int p = e.genmove(b, GomokuBoard.WHITE);
        checkEq(p, idx(11, 5), "白棋必须堵黑的成五点（也验证白方坐标标法）");
        e.close();
    }

    static void testWinNowWhite(String bin) {
        RapfiGomokuEngine e = new RapfiGomokuEngine(bin);
        e.setLevel(0);
        GomokuBoard b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "4,0 4,2 4,4 4,6 4,8");
        playAll(b, GomokuBoard.WHITE, "11,5 11,6 11,7 11,8");
        checkEq(b.moves.size(), 9, "轮到白棋（黑 5 子、白 4 子）");
        int p = e.genmove(b, GomokuBoard.WHITE);
        check(p == idx(11, 4) || p == idx(11, 9), "白棋有活四，直接成五（" + p + "）");
        e.close();
    }

    static void testReadOnly(String bin) {
        RapfiGomokuEngine e = new RapfiGomokuEngine(bin);
        e.setLevel(0);
        GomokuBoard b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "7,7 7,8 6,6");
        playAll(b, GomokuBoard.WHITE, "8,8 8,9");
        int[] gridBefore = b.grid.clone();
        int movesBefore = b.moves.size();
        int winnerBefore = b.winner;
        boolean overBefore = b.gameOver;
        int p = e.genmove(b, GomokuBoard.BLACK);
        boolean same = true;
        for (int i = 0; i < gridBefore.length; i++) {
            if (gridBefore[i] != b.grid[i]) {
                same = false;
                break;
            }
        }
        check(same, "genmove 不动传入棋盘的格子");
        checkEq(b.moves.size(), movesBefore, "genmove 不动传入棋盘的手顺");
        checkEq(b.winner, winnerBefore, "genmove 不动传入棋盘的 winner");
        checkEq(b.gameOver, overBefore, "genmove 不动传入棋盘的 gameOver");
        check(p >= 0 && b.isLegal(GomokuBoard.BLACK, p), "返回的点在传入棋盘上合法（" + p + "）");
        e.close();
    }

    static void testNoMoveAtTerminal(String bin) {
        RapfiGomokuEngine e = new RapfiGomokuEngine(bin);
        e.setLevel(0);
        GomokuBoard over = new GomokuBoard(15, false);
        playAll(over, GomokuBoard.BLACK, "11,0 11,1 11,2 11,3 11,4");
        check(over.gameOver, "构造了一个已经结束的棋盘");
        checkEq(e.genmove(over, GomokuBoard.WHITE), -1, "终局局面返回 -1");

        GomokuBoard full = new GomokuBoard(5, false);
        int n = 0;
        boolean ok = true;
        for (int r = 0; r < 5 && ok; r++) {
            for (int c = 0; c < 5; c++) {
                int color = ((r + 2 * c) % 4 <= 1) ? GomokuBoard.BLACK : GomokuBoard.WHITE;
                try {
                    full.play(color, full.idx(r, c));
                    n++;
                } catch (RuntimeException ex) {
                    ok = false;
                    break;
                }
            }
        }
        checkEq(n, 25, "5×5 摆满（无反例图案）");
        check(full.gameOver && full.isFull(), "满盘算终局");
        checkEq(e.genmove(full, GomokuBoard.BLACK), -1, "满盘返回 -1");
        e.close();
    }

    static void testRestartAfterClose(String bin) {
        RapfiGomokuEngine e = new RapfiGomokuEngine(bin);
        e.setLevel(0);
        GomokuBoard b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "11,0 11,1 11,2 11,3");
        playAll(b, GomokuBoard.WHITE, "10,0 10,1 10,2");
        checkEq(e.genmove(b, GomokuBoard.BLACK), idx(11, 4), "关掉之前先下一次");
        e.close();
        e.close();
        check(true, "close 可以重复调用");
        checkEq(e.genmove(b, GomokuBoard.BLACK), idx(11, 4), "close 之后还能重启进程并正常出招");
        e.close();
    }

    /**
     * BOARD 行序不能乱：pbrain-rapfi 用<b>第一条记录</b>的 side 判断自己执哪一方（第 46 轮审计用真引擎实测）。
     * 摆一个「黑白各一个被堵住的四连、成五点互不相同」的真实轮次局面（10 手，轮黑）：
     * 黑 11 行 5..8（11,4 被白堵）⇒ 黑只能走 (11,9) 成五；白 7 行 5..8（7,4 被黑堵）⇒ 白只能走 (7,9)。
     * 行序对了，引擎执黑时替黑成五；行序颠倒，引擎会认成白棋、跑去走 (7,9)。
     */
    static void testRowOrderWinRace(String bin) {
        RapfiGomokuEngine e = new RapfiGomokuEngine(bin);
        e.setLevel(1);
        GomokuBoard b = new GomokuBoard(15, false);
        String[] bs = {"11,5", "11,6", "11,7", "11,8", "7,4"};
        String[] ws = {"11,4", "7,5", "7,6", "7,7", "7,8"};
        for (int i = 0; i < bs.length; i++) {
            playAll(b, GomokuBoard.BLACK, bs[i]);
            playAll(b, GomokuBoard.WHITE, ws[i]);
        }
        checkEq(b.moves.size(), 10, "10 手，轮到黑棋");
        check(b.isLegal(GomokuBoard.BLACK, idx(11, 9)), "黑棋的成五点 (11,9) 空着");
        check(b.isLegal(GomokuBoard.WHITE, idx(7, 9)), "白棋的成五点 (7,9) 也空着");
        int p = e.genmove(b, GomokuBoard.BLACK);
        checkEq(p, idx(11, 9), "引擎执黑时替黑成五（行序对了才对）");
        check(p != idx(7, 9), "没有替白棋去成五（行序颠倒的典型症状）");
        e.close();
    }

    /**
     * 线序钉死：造一个「记录型」假引擎，把收到的每条输入写进日志、收到 DONE 回 (0,0)，
     * 然后断言日志逐行等于「START / INFO / BOARD / 每手按落子时间顺序、side = mv[0]==请求方?1:2 / DONE」。
     */
    static void testWireOrder(String bin) {
        try {
            File dir = fakeDir(bin);
            File log = new File(dir, "wire.log");
            File fake = new File(dir, "fake-wire.sh");
            writeScript(fake,
                    "#!/bin/sh\n"
                            + "while IFS= read -r line; do\n"
                            + "  echo \"$line\" >> '" + log.getAbsolutePath() + "'\n"
                            + "  case \"$line\" in\n"
                            + "    START*) echo OK ;;\n"
                            + "    DONE) echo 0,0 ;;\n"
                            + "  esac\n"
                            + "done\n");
            RapfiGomokuEngine e = new RapfiGomokuEngine(fake.getAbsolutePath());
            e.setLevel(0);
            GomokuBoard blackTurn = new GomokuBoard(15, false);
            playAll(blackTurn, GomokuBoard.BLACK, "7,7");
            playAll(blackTurn, GomokuBoard.WHITE, "0,0");
            int p = e.genmove(blackTurn, GomokuBoard.BLACK);
            checkEq(p, 210, "假引擎回 (0,0) ⇒ 项目点号 210（14 行 0 列）");
            GomokuBoard whiteTurn = new GomokuBoard(15, false);
            playAll(whiteTurn, GomokuBoard.BLACK, "7,7");
            playAll(whiteTurn, GomokuBoard.WHITE, "0,0");
            playAll(whiteTurn, GomokuBoard.BLACK, "1,1");
            e.genmove(whiteTurn, GomokuBoard.WHITE);
            // 读日志放在 close() 之前：close 会发 END 并立刻结束进程，日志里那行是有竞态的
            String expected =
                    "START 15\n" + "INFO TIMEOUT_TURN 400\n" + "BOARD\n" + "7,7,1\n" + "0,14,2\n" + "DONE\n"
                            + "START 15\n" + "INFO TIMEOUT_TURN 400\n" + "BOARD\n"
                            + "7,7,2\n" + "0,14,1\n" + "1,13,2\n" + "DONE\n";
            checkEq(readAll(log), expected, "线序：行序 == 落子时间顺序、side == 该手是请求方?1:2");
            e.close();
        } catch (Exception ex) {
            check(false, "线序用例异常：" + ex);
        }
    }

    /**
     * 硬超时：假引擎回半行「7」之后就卡住不补完。时限（400 + 15000 ms）到点必须抛「超时」，
     * 而且要把进程结束掉（假引擎的 TERM trap 会写一个标记文件，用它当证据）。
     */
    static void testHardTimeout(String bin) {
        try {
            File dir = fakeDir(bin);
            File marker = new File(dir, "killed.marker");
            File fake = new File(dir, "fake-stall.sh");
            writeScript(fake,
                    "#!/bin/sh\n"
                            + "trap 'echo term > '" + marker.getAbsolutePath() + "'; exit 0' TERM INT\n"
                            + "while IFS= read -r line; do\n"
                            + "  case \"$line\" in\n"
                            + "    START*) echo OK ;;\n"
                            + "    BOARD) printf '7' ;;\n"
                            + "  esac\n"
                            + "done\n");
            RapfiGomokuEngine e = new RapfiGomokuEngine(fake.getAbsolutePath());
            e.setLevel(0);
            long t0 = System.currentTimeMillis();
            boolean threw = false;
            String msg = "";
            try {
                e.genmove(new GomokuBoard(15, false), GomokuBoard.BLACK);
            } catch (IllegalStateException ex) {
                threw = true;
                msg = String.valueOf(ex.getMessage());
            }
            long ms = System.currentTimeMillis() - t0;
            check(threw, "半行卡死 ⇒ 抛 IllegalStateException（而不是一直等）");
            check(msg.contains("超时"), "异常说的是超时（" + msg + "）");
            check(ms < 15400 + 5000, "在时限 + 5 s 内返回（实际 " + ms + " ms）");
            boolean dead = false;
            for (int i = 0; i < 40; i++) {
                if (marker.isFile()) {
                    dead = true;
                    break;
                }
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            check(dead, "超时后进程被结束（假引擎收到 TERM）");
            e.close();
        } catch (Exception ex) {
            check(false, "硬超时用例异常：" + ex);
        }
    }

    /**
     * 半写多字节字符也不能卡住（第 47 轮审计抓到的缝）：假引擎在 BOARD 之后只写一个 0xE4 字节
     * （一个 3 字节汉字的头一个字节）就停住 —— 若读取套的是 InputStreamReader，这里会永久阻塞。
     */
    static void testHalfMultibyteTimeout(String bin) {
        try {
            File dir = fakeDir(bin);
            File marker = new File(dir, "killed-mb.marker");
            File fake = new File(dir, "fake-mb-stall.sh");
            writeScript(fake,
                    "#!/bin/sh\n"
                            + "trap 'echo term > '" + marker.getAbsolutePath() + "'; exit 0' TERM INT\n"
                            + "while IFS= read -r line; do\n"
                            + "  case \"$line\" in\n"
                            + "    START*) echo OK ;;\n"
                            + "    BOARD) printf '\\344' ;;\n"
                            + "  esac\n"
                            + "done\n");
            RapfiGomokuEngine e = new RapfiGomokuEngine(fake.getAbsolutePath());
            e.setLevel(0);
            long t0 = System.currentTimeMillis();
            boolean threw = false;
            String msg = "";
            try {
                e.genmove(new GomokuBoard(15, false), GomokuBoard.BLACK);
            } catch (IllegalStateException ex) {
                threw = true;
                msg = String.valueOf(ex.getMessage());
            }
            long ms = System.currentTimeMillis() - t0;
            check(threw, "半个多字节字符卡住 ⇒ 抛 IllegalStateException（而不是一直等）");
            check(msg.contains("超时"), "异常说的是超时（" + msg + "）");
            check(ms < 15400 + 5000, "在时限 + 5 s 内返回（实际 " + ms + " ms）");
            boolean dead = false;
            for (int i = 0; i < 40; i++) {
                if (marker.isFile()) {
                    dead = true;
                    break;
                }
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            check(dead, "超时后进程被结束（假引擎收到 TERM）");
            e.close();
        } catch (Exception ex) {
            check(false, "半写多字节用例异常：" + ex);
        }
    }

    /**
     * 超时杀掉之后必须能自动重起：假引擎第一次跑就半写一个多字节字符然后停住（会被杀），
     * 第二次跑按正常协议回 (7,7) ⇒ 断言「第二次拿回一手」且脚本被启动过两次。
     */
    static void testRestartAfterTimeout(String bin) {
        try {
            File dir = fakeDir(bin);
            File cnt = new File(dir, "run.count");
            File pidf = new File(dir, "restart.pid");
            File fake = new File(dir, "fake-two-stage.sh");
            writeScript(fake,
                    "#!/bin/sh\n"
                            + "N=0\n"
                            + "if [ -f '" + cnt.getAbsolutePath() + "' ]; then N=$(cat '" + cnt.getAbsolutePath() + "'); fi\n"
                            + "echo $((N+1)) > '" + cnt.getAbsolutePath() + "'\n"
                            + "if [ \"$N\" -eq 0 ]; then\n"
                            + "  echo $$ > '" + pidf.getAbsolutePath() + "'\n"
                            + "  trap '' TERM INT\n"
                            + "  while IFS= read -r line; do\n"
                            + "    case \"$line\" in\n"
                            + "      START*) echo OK ;;\n"
                            + "      BOARD) printf '\\344'; sleep 40 ;;\n"
                            + "    esac\n"
                            + "  done\n"
                            + "else\n"
                            + "  while IFS= read -r line; do\n"
                            + "    case \"$line\" in\n"
                            + "      START*) echo OK ;;\n"
                            + "      DONE) echo 7,7 ;;\n"
                            + "    esac\n"
                            + "  done\n"
                            + "fi\n");
            RapfiGomokuEngine e = new RapfiGomokuEngine(fake.getAbsolutePath());
            e.setLevel(0);
            boolean threw = false;
            try {
                e.genmove(new GomokuBoard(15, false), GomokuBoard.BLACK);
            } catch (IllegalStateException ex) {
                threw = true;
            }
            check(threw, "第一次调用（假引擎卡住）抛超时");
            long t0 = System.currentTimeMillis();
            int p = -1;
            try {
                p = e.genmove(new GomokuBoard(15, false), GomokuBoard.BLACK);
            } catch (RuntimeException ex) {
                check(false, "重起后第二次调用不该抛异常：" + ex);
            }
            long ms = System.currentTimeMillis() - t0;
            checkEq(p, idx(7, 7), "超时杀进程后自动重起，第二次拿回引擎的 (7,7)");
            check(ms < 5000, "重起后的这一手在 5 s 内返回（实际 " + ms + " ms）");
            long pid = -1;
            try {
                pid = Long.parseLong(readAll(pidf).trim());
            } catch (Exception ignored) {
                // 下面那条 check 会报出来
            }
            check(pid > 0, "忽略 TERM 的假引擎会把自己的 pid 写下来");
            check(!aliveWithin(pid, 3000),
                    "超时杀进程：连忽略 TERM 的假引擎也被真的收掉（pid " + pid + "）");
            checkEq(readAll(cnt).trim(), "2", "假引擎被启动过两次（第一次被杀、第二次正常）");
            e.close();
        } catch (Exception ex) {
            check(false, "重起用例异常：" + ex);
        }
    }

    /** 第 48 轮审计 低-3：close() 以前只 destroy() —— 忽略 TERM 的引擎会活下来（孤儿进程）。 */
    static void testCloseKillsEngine(String bin) {
        try {
            File dir = fakeDir(bin);
            File pidf = new File(dir, "close.pid");
            File fake = new File(dir, "fake-ignore-term.sh");
            writeScript(fake,
                    "#!/bin/sh\n"
                            + "trap '' TERM INT\n"
                            + "echo $$ > '" + pidf.getAbsolutePath() + "'\n"
                            + "while IFS= read -r line; do\n"
                            + "  case \"$line\" in\n"
                            + "    START*) echo OK ;;\n"
                            + "    DONE) echo 7,7 ;;\n"
                            + "    END) sleep 40 ;;\n"
                            + "  esac\n"
                            + "done\n");
            RapfiGomokuEngine e = new RapfiGomokuEngine(fake.getAbsolutePath());
            e.setLevel(0);
            checkEq(e.genmove(new GomokuBoard(15, false), GomokuBoard.BLACK), idx(7, 7),
                    "忽略 TERM 的假引擎先正常给一手");
            long pid = -1;
            try {
                pid = Long.parseLong(readAll(pidf).trim());
            } catch (Exception ignored) {
                // 下面那条 check 会报出来
            }
            check(pid > 0, "忽略 TERM 的假引擎会把自己的 pid 写下来（close 用例）");
            e.close();
            check(!aliveWithin(pid, 3000),
                    "close() 也把忽略 TERM 的引擎真的收掉（pid " + pid + "）");
            checkEq(e.genmove(new GomokuBoard(15, false), GomokuBoard.BLACK), idx(7, 7),
                    "close() 之后还能重起（新进程照常给手）");
            long pid2 = -1;
            try {
                pid2 = Long.parseLong(readAll(pidf).trim());
            } catch (Exception ignored) {
                // 下面那条 check 会报出来
            }
            check(pid2 > 0 && pid2 != pid, "重起后的假引擎换了新 pid（旧 " + pid + "、新 " + pid2 + "）");
            e.close();
            check(!aliveWithin(pid2, 3000),
                    "最后一次 close() 也把重起后的引擎真的收掉（pid " + pid2 + "）");
        } catch (Exception ex) {
            check(false, "close() 杀进程用例异常：" + ex);
        }
    }

    /**
     * 引擎灌了一堆输出没人读（管道里有积压）时 close() 也不能卡住 —— 这条分支
     * （`available() > 0` ⇒ 跳过 END 直接收尾）此前没有单测，是第 50 轮审计的提示之一。
     */
    static void testCloseWithBacklog(String bin) {
        try {
            File dir = fakeDir(bin);
            File pidf = new File(dir, "backlog.pid");
            File markf = new File(dir, "backlog.end");
            File fake = new File(dir, "fake-backlog.sh");
            writeScript(fake,
                    "#!/bin/sh\n"
                            + "trap '' TERM INT\n"
                            + "echo $$ > '" + pidf.getAbsolutePath() + "'\n"
                            + "while IFS= read -r line; do\n"
                            + "  case \"$line\" in\n"
                            + "    START*) echo OK ;;\n"
                            + "    DONE)\n"
                            + "      echo 7,7\n"
                            + "      # 先把这一手送出去，再从后台把 stdout 灌满（前台继续读 stdin，才能看见 END）\n"
                            + "      ( i=0; while :; do printf '%s\\n' 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'; i=$((i+1)); done ) &\n"
                            + "      ;;\n"
                            + "    END) echo seen > '" + markf.getAbsolutePath() + "' ;;\n"
                            + "  esac\n"
                            + "done\n");
            final RapfiGomokuEngine e = new RapfiGomokuEngine(fake.getAbsolutePath());
            e.setLevel(0);
            checkEq(e.genmove(new GomokuBoard(15, false), GomokuBoard.BLACK), idx(7, 7),
                    "灌输出的假引擎先正常给一手");
            long pid = -1;
            try {
                pid = Long.parseLong(readAll(pidf).trim());
            } catch (Exception ignored) {
                // 下面那条 check 会报出来
            }
            check(pid > 0, "灌输出的假引擎会把自己的 pid 写下来");
            Thread.sleep(300); // 让后台把管道灌满（64 KB 之后它会阻塞在写）
            // 没人读走积压时，close() 若还去写 END 就可能卡在 write 上：放到守护线程里等 5 s，
            // 卡住就是一条干净的 FAIL，不会把套件挂死（守护线程随 JVM 退出消失）。
            final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean(false);
            Thread closer = new Thread(new Runnable() {
                public void run() {
                    e.close();
                    closed.set(true);
                }
            });
            closer.setDaemon(true);
            long t0 = System.nanoTime();
            closer.start();
            long waited = 0;
            while (!closed.get() && waited < 5000) {
                Thread.sleep(50);
                waited = (System.nanoTime() - t0) / 1000000L;
            }
            check(closed.get(), "管道里有积压、没人读时 close() 也不卡（等了 " + waited + " ms）");
            check(!aliveWithin(pid, 3000),
                    "有积压输出时 close() 也把引擎收掉（pid " + pid + "）");
            check(!markf.exists(),
                    "有积压输出时 close() 跳过 END、直接收尾（守卫 available() > 0 生效）");
        } catch (Exception ex) {
            check(false, "灌输出 close() 用例异常：" + ex);
        }
    }

    /**
     * 奇数手局面（9 手，轮黑）行序颠倒也应当照样成五 —— 这条是给变异脚本当**对照**用的：
     * 「只在奇数手颠倒行序」应当全绿（奇偶不变 ⇒ 引擎认色不变），免得将来出假红。
     */
    static void testForcedFiveOddHands(String bin) {
        try {
            GomokuBoard b = new GomokuBoard(15, false);
            playAll(b, GomokuBoard.BLACK, "7,3");
            playAll(b, GomokuBoard.WHITE, "0,0");
            playAll(b, GomokuBoard.BLACK, "7,4");
            playAll(b, GomokuBoard.WHITE, "0,1");
            playAll(b, GomokuBoard.BLACK, "7,5");
            playAll(b, GomokuBoard.WHITE, "0,2");
            playAll(b, GomokuBoard.BLACK, "7,6");
            playAll(b, GomokuBoard.WHITE, "0,3");
            RapfiGomokuEngine e = new RapfiGomokuEngine(bin);
            e.setLevel(0);
            int p = e.genmove(b, GomokuBoard.BLACK);
            check(p == idx(7, 2) || p == idx(7, 7), "奇数手（9 手，轮黑）局面：强引擎替黑直接成五（实际 " + p + "）");
            e.close();
        } catch (Exception ex) {
            check(false, "奇数手用例异常：" + ex);
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 假引擎放在真引擎所在目录（那个目录一定能执行）。 */
    /** 在 ms 毫秒内反复看 pid 是否还活着；返回 true = 到点还活着。 */
    static boolean aliveWithin(long pid, long ms) {
        if (pid <= 0) {
            // 没拿到有效 pid：当成「已经不在了」——该报的失败由调用点的 check 去报，别在这里抛异常打断用例
            return false;
        }
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < ms) {
            if (!ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                return false;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException ignored) {
                // 继续等
            }
        }
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    static File fakeDir(String bin) {
        File parent = new File(bin).getAbsoluteFile().getParentFile();
        File dir = new File(parent == null ? new File(".") : parent, "rapfi-fake-" + System.nanoTime());
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("建不了临时目录：" + dir);
        }
        return dir;
    }

    static void writeScript(File f, String content) throws Exception {
        java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
        try {
            fo.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            fo.close();
        }
        if (!f.setExecutable(true) && !f.canExecute()) {
            throw new IllegalStateException("加不上执行位：" + f);
        }
    }

    static String readAll(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream(f), java.nio.charset.StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            r.close();
        }
        return sb.toString();
    }

    /** 非 15 路的棋盘必须被适配层自己拒绝（不靠调用方记得把关）。 */
    static void testNonFifteenBoard(String bin) {
        RapfiGomokuEngine e = new RapfiGomokuEngine(bin);
        try {
            int[] sizes = {19, 13};
            for (int sz : sizes) {
                GomokuBoard b = new GomokuBoard(sz, false);
                IllegalStateException caught = null;
                try {
                    e.genmove(b, GomokuBoard.BLACK);
                } catch (IllegalStateException ex) {
                    caught = ex;
                }
                check(caught != null, sz + " 路：genmove 直接抛 IllegalStateException（不喂残缺输入给引擎）");
                if (caught != null) {
                    String msg = caught.getMessage() == null ? "" : caught.getMessage();
                    check(msg.contains("只支持 15 路"), sz + " 路：异常里写明「只支持 15 路」（实际：" + msg + "）");
                    check(msg.contains(String.valueOf(sz)), sz + " 路：异常里带出实际路数（实际：" + msg + "）");
                }
            }
            GomokuBoard b15 = new GomokuBoard(15, false);
            int mv = e.genmove(b15, GomokuBoard.BLACK);
            check(mv >= 0 && mv < 15 * 15, "15 路：守卫不影响正常出招（得到 " + mv + "）");
        } finally {
            e.close();
        }
    }

}
