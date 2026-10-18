import com.autogo.GomokuBoard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GomokuBoard 的 JVM 单元测试（不需要 Android 运行时）。
 *
 * 约定：坐标 (row, col)，row = 0 在顶部；X = 黑、O = 白、. = 空。
 * 运行：bash run_tests.sh（本文件也在编译集里）
 *
 * 用例分五组：A 基本落子 / B 连线判定 / C 禁手（含反例）/ D 终局 / E 复制与一致性。
 * 每条断言都有对应的「已知会失败的改法」记在 README 里（反证清单 F1~F10）。
 */
public class GomokuTest {

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    // ---------- 工具 ----------

    private static void check(boolean cond, String what) {
        if (cond) {
            passed++;
        } else {
            failures.add(what);
            System.out.println("  FAIL: " + what);
        }
    }

    private static void checkEq(Object actual, Object expected, String what) {
        boolean ok = (actual == null) ? expected == null : actual.equals(expected);
        if (!ok) {
            failures.add(what + "（期望 " + expected + "，实际 " + actual + "）");
            System.out.println("  FAIL: " + what + "（期望 " + expected + "，实际 " + actual + "）");
        } else {
            passed++;
        }
    }

    /** 直接摆子，跳过规则校验（构造测试局面）。pts 形如 "7,3 7,4"（row,col）。 */
    private static void place(GomokuBoard b, int color, String pts) {
        for (String tok : pts.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            String[] rc = tok.split(",");
            int r = Integer.parseInt(rc[0].trim()), c = Integer.parseInt(rc[1].trim());
            b.grid[b.idx(r, c)] = color;
        }
    }

    /** 摆对手的子 + 记录手顺（用于「重放一致」的测试）。 */
    private static void playAll(GomokuBoard b, int color, String pts) {
        for (String tok : pts.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            String[] rc = tok.split(",");
            b.play(color, b.idx(Integer.parseInt(rc[0].trim()), Integer.parseInt(rc[1].trim())));
        }
    }

    /** 尝试落子，返回 true 表示按预期抛了 IllegalMove。 */
    private static boolean throwsIllegal(GomokuBoard b, int color, int point) {
        try {
            b.play(color, point);
            return false;
        } catch (GomokuBoard.IllegalMove e) {
            return true;
        }
    }

    // ---------- A. 基本落子 ----------

    private static void testBasics() {
        GomokuBoard b = new GomokuBoard(15, false);
        checkEq(b.size, 15, "A1 棋盘 15 路");
        boolean allEmpty = true;
        for (int i = 0; i < b.grid.length; i++) if (b.grid[i] != GomokuBoard.EMPTY) allEmpty = false;
        check(allEmpty, "A1 空盘全是 EMPTY");
        checkEq(b.grid.length, 225, "A1 格子数 15*15");

        for (int[] rc : new int[][]{{0, 0}, {0, 14}, {14, 0}, {14, 14}, {7, 7}}) {
            int p = b.idx(rc[0], rc[1]);
            check(b.row(p) == rc[0] && b.col(p) == rc[1],
                    "A1 row/col/idx 互逆 (" + rc[0] + "," + rc[1] + ")");
        }
        checkEq(b.idx(0, 0), 0, "A1 idx(0,0)=0");
        checkEq(b.idx(14, 14), 224, "A1 idx(14,14)=224");

        // A2 重复落子
        int p = b.idx(7, 7);
        b.play(GomokuBoard.BLACK, p);
        checkEq(b.at(p), GomokuBoard.BLACK, "A2 落子后该点变黑");
        checkEq(b.moves.size(), 1, "A2 手顺长度 1");
        check(b.illegalReason(GomokuBoard.WHITE, p) != null, "A2 重复落子有非法原因");
        check(b.illegalReason(GomokuBoard.WHITE, p).contains("已有子"), "A2 原因写明已有子");
        check(!b.isLegal(GomokuBoard.WHITE, p), "A2 重复落子不合法");
        check(throwsIllegal(b, GomokuBoard.WHITE, p), "A2 重复落子抛 IllegalMove");
        checkEq(b.moves.size(), 1, "A2 失败落子不写手顺");

        // A3 越界
        check(b.illegalReason(GomokuBoard.BLACK, -1) != null, "A3 -1 非法");
        check(b.illegalReason(GomokuBoard.BLACK, 225) != null, "A3 size*size 非法");
        check(throwsIllegal(b, GomokuBoard.BLACK, -1), "A3 -1 抛 IllegalMove（不是 AIOOBE）");
        check(throwsIllegal(b, GomokuBoard.BLACK, 225), "A3 size*size 抛 IllegalMove");

        // A4 手顺内容
        b = new GomokuBoard(15, false);
        int p1 = b.idx(3, 4), p2 = b.idx(9, 10);
        b.play(GomokuBoard.BLACK, p1);
        b.play(GomokuBoard.WHITE, p2);
        checkEq(b.moves.size(), 2, "A4 两手手顺长度为 2");
        checkEq(b.moves.get(0)[0], GomokuBoard.BLACK, "A4 第 1 手颜色");
        checkEq(b.moves.get(0)[1], p1, "A4 第 1 手坐标");
        checkEq(b.moves.get(1)[0], GomokuBoard.WHITE, "A4 第 2 手颜色");
        checkEq(b.moves.get(1)[1], p2, "A4 第 2 手坐标");

        // A5 颜色不合法
        check(b.illegalReason(0, b.idx(0, 0)) != null, "A5 颜色 0 非法");
        check(throwsIllegal(b, 0, b.idx(0, 0)), "A5 颜色 0 抛 IllegalMove");
        check(b.illegalReason(3, b.idx(0, 0)) != null, "A5 颜色 3 非法");

        // A6 尺寸下限
        boolean threw = false;
        try {
            new GomokuBoard(4, false);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "A6 4 路棋盘被拒");

        // A7 opponent / inBounds
        checkEq(GomokuBoard.opponent(GomokuBoard.BLACK), GomokuBoard.WHITE, "A7 opponent(黑)=白");
        checkEq(GomokuBoard.opponent(GomokuBoard.WHITE), GomokuBoard.BLACK, "A7 opponent(白)=黑");
        check(!GomokuBoard.inBounds(15, -1, 0) && !GomokuBoard.inBounds(15, 0, 15)
                && GomokuBoard.inBounds(15, 14, 14), "A7 inBounds 边界");
    }

    // ---------- B. 连线判定 ----------

    private static void testRuns() {
        // B1 横五
        GomokuBoard b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "7,3 7,4 7,5 7,6 7,7");
        check(b.gameOver && b.winner == GomokuBoard.BLACK, "B1 横五：黑胜");
        check(b.endReason.contains("黑"), "B1 结论里有黑（实际：" + b.endReason + "）");
        check(b.endReason.contains("五连"), "B1 结论写明五连（实际：" + b.endReason + "）");
        checkEq(b.winLine == null ? -1 : b.winLine.length, 5, "B1 获胜线 5 个点");
        int[] rowPts = {b.idx(7, 3), b.idx(7, 4), b.idx(7, 5), b.idx(7, 6), b.idx(7, 7)};
        checkEq(Arrays.toString(b.winLine), Arrays.toString(rowPts), "B1 获胜线就是那 5 个点");

        // B2 竖五
        b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "3,7 4,7 5,7 6,7 7,7");
        check(b.gameOver && b.winner == GomokuBoard.BLACK, "B2 竖五：黑胜");

        // B3 斜五（左上→右下）
        b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "3,3 4,4 5,5 6,6 7,7");
        check(b.gameOver && b.winner == GomokuBoard.BLACK, "B3 斜五：黑胜");

        // B4 反斜五（右上→左下）
        b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "3,11 4,10 5,9 6,8 7,7");
        check(b.gameOver && b.winner == GomokuBoard.BLACK, "B4 反斜五：黑胜");

        // B4b 反斜五的「方向缺失」探针：只靠 (1,-1) 才能连起来
        b = new GomokuBoard(15, false);
        place(b, GomokuBoard.BLACK, "6,6 5,7 4,8");
        checkEq(b.longestRun(GomokuBoard.BLACK, 6, 6), 3, "B4b 反斜方向数得到 3 连");

        // B5 四连不成五
        b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "7,3 7,4 7,5 7,6");
        check(!b.gameOver && b.winner == GomokuBoard.EMPTY, "B5 四连不结束");
        checkEq(b.longestRun(GomokuBoard.BLACK, 7, 3), 4, "B5 最长连 4");

        // B6 长连（自由规则算赢）：先摆 5 子（摆子不触发判胜），再补第 6 子
        b = new GomokuBoard(15, false);
        place(b, GomokuBoard.BLACK, "7,2 7,3 7,4 7,5 7,6");
        checkEq(b.longestRun(GomokuBoard.BLACK, 7, 2), 5, "B6 摆好的 5 连");
        b.play(GomokuBoard.BLACK, b.idx(7, 7));
        check(b.gameOver && b.winner == GomokuBoard.BLACK, "B6 长连：黑胜（自由规则）");
        check(b.endReason.contains("长连"), "B6 结论写明长连（实际：" + b.endReason + "）");
        checkEq(b.winLine == null ? -1 : b.winLine.length, 6, "B6 获胜线含 6 个点");

        // B7 边界五
        b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "0,3 0,4 0,5 0,6 0,7");
        check(b.gameOver && b.winner == GomokuBoard.BLACK, "B7 贴着 row=0 的横五");
        b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "3,14 4,14 5,14 6,14 7,14");
        check(b.gameOver && b.winner == GomokuBoard.BLACK, "B7 贴着 col=14 的竖五");

        // B8 十字五：只判一次胜、手顺不多算
        b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "7,3 7,4 7,5 7,6 3,7 4,7 5,7");
        check(!b.gameOver, "B8 十字还没成（7 手）");
        playAll(b, GomokuBoard.BLACK, "6,7 7,7");
        check(b.gameOver && b.winner == GomokuBoard.BLACK, "B8 十字五：黑胜");
        checkEq(b.moves.size(), 9, "B8 手顺 9 手（不多算）");
        checkEq(b.winLine == null ? -1 : b.winLine.length, 5, "B8 获胜线只报一条 5 连");

        // B9 中间夹白不算连
        b = new GomokuBoard(15, false);
        place(b, GomokuBoard.BLACK, "7,3 7,4 7,6 7,7 7,8");
        place(b, GomokuBoard.WHITE, "7,5");
        checkEq(b.longestRun(GomokuBoard.BLACK, 7, 3), 2, "B9 白子断开后左侧只有 2 连");
        checkEq(b.longestRun(GomokuBoard.BLACK, 7, 8), 3, "B9 白子断开后右侧 3 连");
    }

    // ---------- C. 禁手（只对黑生效）+ 反例 ----------

    private static void testForbidden() {
        // C1a 长连禁手：黑走成六连 ⇒ 不许落
        GomokuBoard b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "7,2 7,3 7,4 7,5 7,6");
        int p = b.idx(7, 7);
        check(b.isForbidden(GomokuBoard.BLACK, 7, 7), "C1a 黑六连是禁手");
        check(!b.isLegal(GomokuBoard.BLACK, p), "C1a 禁手点不合法");
        check(b.illegalReason(GomokuBoard.BLACK, p).contains("禁手"), "C1a 原因写明禁手");
        checkEq(b.forbiddenKind(7, 7), "长连", "C1a 禁手种类=长连");
        check(throwsIllegal(b, GomokuBoard.BLACK, p), "C1a 禁手点抛 IllegalMove");
        checkEq(b.moves.size(), 0, "C1a 被拒的落子不写手顺");

        // C1b 同一局面白走 ⇒ 合法（白无禁手）
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.WHITE, "7,2 7,3 7,4 7,5 7,6");
        check(!b.isForbidden(GomokuBoard.WHITE, 7, 7), "C1b 白六连不是禁手");
        b.play(GomokuBoard.WHITE, b.idx(7, 7));
        check(b.winner == GomokuBoard.WHITE, "C1b 白长连照样赢");
        check(b.endReason.contains("长连"), "C1b 白长连的结论（实际：" + b.endReason + "）");

        // C1c 成五优先于禁手：黑补成五连 ⇒ 合法且胜
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "7,3 7,4 7,5 7,6");
        check(!b.isForbidden(GomokuBoard.BLACK, 7, 7), "C1c 黑成五不是禁手");
        b.play(GomokuBoard.BLACK, b.idx(7, 7));
        check(b.gameOver && b.winner == GomokuBoard.BLACK, "C1c 黑成五判胜");

        // C2 四四（两个方向各一个「四」）
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "7,4 7,5 7,6 4,7 5,7 6,7");
        check(b.isForbidden(GomokuBoard.BLACK, 7, 7), "C2 四四是禁手");
        checkEq(b.forbiddenKind(7, 7), "四四", "C2 禁手种类=四四");
        check(throwsIllegal(b, GomokuBoard.BLACK, b.idx(7, 7)), "C2 四四抛 IllegalMove");

        // C3 双活三
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "7,5 7,6 5,7 6,7");
        check(b.isForbidden(GomokuBoard.BLACK, 7, 7), "C3 双活三是禁手");
        checkEq(b.forbiddenKind(7, 7), "三三", "C3 禁手种类=三三");
        check(throwsIllegal(b, GomokuBoard.BLACK, b.idx(7, 7)), "C3 三三抛 IllegalMove");

        // C4 反例·假活三（两条都被白堵住）⇒ 不是禁手
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "7,5 7,6 5,7 6,7");
        place(b, GomokuBoard.WHITE, "7,4 4,7");
        check(!b.isForbidden(GomokuBoard.BLACK, 7, 7), "C4 两条眠三不是禁手");
        b.play(GomokuBoard.BLACK, b.idx(7, 7));
        check(!b.gameOver, "C4 合法落子后棋局继续");

        // C5 反例·单活三 + 单四 ⇒ 不是禁手
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "7,5 7,6 4,7 5,7 6,7");
        check(!b.isForbidden(GomokuBoard.BLACK, 7, 7), "C5 一活三一四不是禁手");
        b.play(GomokuBoard.BLACK, b.idx(7, 7));
        check(!b.gameOver, "C5 合法落子后棋局继续");

        // C6 两个「冲四」仍算双四（四的一端被白堵住不影响「四」成立）
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "7,4 7,5 7,6 4,7 5,7 6,7");
        place(b, GomokuBoard.WHITE, "7,2 2,7");
        check(b.isForbidden(GomokuBoard.BLACK, 7, 7), "C6 两个冲四也算双四");
        checkEq(b.forbiddenKind(7, 7), "四四", "C6 种类=四四");

        // C7a 反例·贴边的「三」不算活三（外端在盘外，做不成活四）⇒ 不构成三三
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "0,0 1,0 2,1 2,2");
        check(!b.isForbidden(GomokuBoard.BLACK, 2, 0), "C7a 贴边两向都不是活三");
        // C7b 对照：同一形状整体挪到棋盘内部 ⇒ 两个方向都成活三 ⇒ 禁手
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "3,3 4,3 5,4 5,5");
        check(b.isForbidden(GomokuBoard.BLACK, 5, 3), "C7b 挪进棋盘内就是三三");
        checkEq(b.forbiddenKind(5, 3), "三三", "C7b 种类=三三");

        // C8 白棋同样局面一律不算禁手
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.WHITE, "7,4 7,5 7,6 4,7 5,7 6,7");
        check(!b.isForbidden(GomokuBoard.WHITE, 7, 7), "C8 白无禁手（四四局面）");
        b.play(GomokuBoard.WHITE, b.idx(7, 7));
        check(!b.gameOver, "C8 白可以落在黑会被禁的点上");

        // C9 禁手关掉时同样的六连局面合法
        b = new GomokuBoard(15, false);
        place(b, GomokuBoard.BLACK, "7,2 7,3 7,4 7,5 7,6");
        b.play(GomokuBoard.BLACK, b.idx(7, 7));
        check(b.gameOver && b.winner == GomokuBoard.BLACK, "C9 关掉禁手则长连照赢");

        // C10 禁手判定不改棋盘（isForbidden/forbiddenKind 都不许留下痕迹）
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "7,4 7,5 7,6 4,7 5,7 6,7");
        int before = b.grid[b.idx(7, 7)];
        b.isForbidden(GomokuBoard.BLACK, 7, 7);
        b.forbiddenKind(7, 7);
        b.illegalReason(GomokuBoard.BLACK, b.idx(7, 7));
        checkEq(b.grid[b.idx(7, 7)], before, "C10 禁手判定后该点仍是空的");
        checkEq(b.moves.size(), 0, "C10 禁手判定后手顺仍是 0");

        // C12a 越界坐标不许抛 AIOOBE，按契约返回 false
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "7,7");
        check(!b.isForbidden(GomokuBoard.BLACK, 20, 20), "C12a 越界坐标不是禁手（不抛异常）");
        check(!b.isForbidden(GomokuBoard.BLACK, 7, 7), "C12a 已落子的点不是禁手点");

        // C12b 契约：被占的点（白子）返回 false —— 不许把别人的子当成黑子去算
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "7,3 7,4 7,5 4,7 5,7 6,7");
        place(b, GomokuBoard.WHITE, "7,7");
        check(!b.isForbidden(GomokuBoard.BLACK, 7, 7), "C12b 白子占着的点不是黑的禁手点");
        check(b.illegalReason(GomokuBoard.BLACK, b.idx(7, 7)).contains("已有子"),
                "C12b 该点的非法原因是已有子（不是禁手）");

        // C13 活三判定必须「这一手在四里」：隔着老远的四不算这一手造出来的活三
        b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "7,3 7,4 7,5 5,10 6,10");
        check(!b.isForbidden(GomokuBoard.BLACK, 7, 10), "C13 远处的活四不算这一手的活三（只有一个活三）");
        b.play(GomokuBoard.BLACK, b.idx(7, 10));
        check(!b.gameOver, "C13 合法落子后棋局继续");

        // C11 19 路大盘上四四照样判得出来（点号 > 255，钉住去重键的位宽）
        b = new GomokuBoard(19, true);
        place(b, GomokuBoard.BLACK, "17,10 17,11 17,12 14,13 15,13 16,13");
        check(b.isForbidden(GomokuBoard.BLACK, 17, 13), "C11 19 路上的四四");
        checkEq(b.forbiddenKind(17, 13), "四四", "C11 19 路种类=四四");
    }

    // ---------- D. 终局 ----------

    private static void testTerminal() {
        // D1a 5 路盘下满未连五 ⇒ 和棋
        GomokuBoard b = new GomokuBoard(5, false);
        String[] rows = {"BBWWB", "WWBBW", "BBWWB", "WWBBW", "BBWWB"};
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                b.play(rows[r].charAt(c) == 'B' ? GomokuBoard.BLACK : GomokuBoard.WHITE, b.idx(r, c));
            }
        }
        check(b.isFull(), "D1a 5 路盘下满");
        check(b.gameOver, "D1a 下满即终局");
        checkEq(b.winner, GomokuBoard.EMPTY, "D1a 和棋：没有胜方");
        check(b.endReason.contains("和"), "D1a 结论写明和棋（实际：" + b.endReason + "）");

        // D1b 15 路盘下满（生成式填法：横竖都只有 2 连）
        b = new GomokuBoard(15, false);
        int[] colorCount = new int[3];
        for (int r = 0; r < 15; r++) {
            for (int c = 0; c < 15; c++) {
                int color = ((2 * r + c) % 4 < 2) ? GomokuBoard.BLACK : GomokuBoard.WHITE;
                colorCount[color]++;
                b.play(color, b.idx(r, c));
            }
        }
        checkEq(colorCount[GomokuBoard.BLACK] + colorCount[GomokuBoard.WHITE], 225, "D1b 15 路摆满 225 子");
        check(b.isFull(), "D1b 盘满");
        checkEq(b.winner, GomokuBoard.EMPTY, "D1b 和棋");
        check(b.endReason.contains("和"), "D1b 结论写明和棋");
        checkEq(b.moves.size(), 225, "D1b 手顺 225 手");

        // D2 终局后再落子非法（含认输后的局面）
        b = new GomokuBoard(15, false);
        playAll(b, GomokuBoard.BLACK, "7,3 7,4 7,5 7,6 7,7");
        check(b.gameOver, "D2 已终局");
        int q = b.idx(0, 0);
        check(!b.isLegal(GomokuBoard.WHITE, q), "D2 终局后落子不合法");
        check(b.illegalReason(GomokuBoard.WHITE, q).contains("已结束"), "D2 原因写明已结束");
        check(throwsIllegal(b, GomokuBoard.WHITE, q), "D2 终局后落子抛 IllegalMove");

        // D3 认输
        b = new GomokuBoard(15, false);
        b.play(GomokuBoard.BLACK, b.idx(7, 7));
        b.play(GomokuBoard.WHITE, b.idx(7, 8));
        b.resign(GomokuBoard.BLACK);
        check(b.gameOver, "D3 认输即终局");
        checkEq(b.winner, GomokuBoard.WHITE, "D3 黑认输 ⇒ 白胜");
        check(b.endReason.contains("认输"), "D3 结论写明认输（实际：" + b.endReason + "）");
        check(b.endReason.contains("白"), "D3 结论写明白胜");
        check(throwsIllegal(b, GomokuBoard.WHITE, b.idx(0, 0)), "D3 认输后落子非法");
        boolean threw = false;
        try {
            b.resign(GomokuBoard.WHITE);
        } catch (GomokuBoard.IllegalMove e) {
            threw = true;
        }
        check(threw, "D3 终局后再认输抛 IllegalMove");
        boolean colorThrew = false;
        try {
            new GomokuBoard(15, false).resign(0);
        } catch (GomokuBoard.IllegalMove e) {
            colorThrew = true;
        }
        check(colorThrew, "D3 resign 颜色不合法也抛 IllegalMove");

        // D4 白认输
        b = new GomokuBoard(15, false);
        b.resign(GomokuBoard.WHITE);
        checkEq(b.winner, GomokuBoard.BLACK, "D4 白认输 ⇒ 黑胜");
    }

    // ---------- E. 复制与一致性 ----------

    /** 坐标命名：界面上的列字母 / 行号都走这里，写错了复盘和手顺全乱。 */
    private static void testNaming() {
        GomokuBoard b = new GomokuBoard(15, false);
        checkEq(GomokuBoard.pointToName(15, b.idx(7, 7)), "H8", "15 路天元 = H8");
        checkEq(GomokuBoard.pointToName(15, b.idx(0, 0)), "A15", "左上角 = A15");
        checkEq(GomokuBoard.pointToName(15, b.idx(14, 14)), "P1", "右下角 = P1（跳过 I，第 15 列是 P）");
        checkEq(GomokuBoard.pointToName(9, 81), "", "点号超出 9 路范围 -> 空串");
        checkEq(GomokuBoard.pointToName(9, 80), "J1", "9 路右下角 = J1（行号从下往上数，最下面一行是 1）");
        checkEq(GomokuBoard.pointToName(15, -1), "", "负数点号 -> 空串");
        // 列字母跳过 I：第 8 列是 H，第 9 列是 J
        checkEq(String.valueOf(GomokuBoard.GTP_COLS.charAt(8)), "J", "第 9 个字母是 J（没有 I）");
    }

    private static void testCopy() {
        GomokuBoard b = new GomokuBoard(15, true);
        playAll(b, GomokuBoard.BLACK, "7,7");
        playAll(b, GomokuBoard.WHITE, "7,8");
        playAll(b, GomokuBoard.BLACK, "8,8");

        GomokuBoard c = b.copy();
        check(Arrays.equals(c.grid, b.grid), "E1 副本 grid 与原件相同");
        checkEq(c.moves.size(), 3, "E1 副本手顺 3 手");
        check(c.moves != b.moves, "E1 副本手顺是另一个 List");
        check(c.moves.get(0) != b.moves.get(0), "E1 副本手顺里的数组也是新的");

        c.grid[0] = GomokuBoard.WHITE;
        c.moves.add(new int[]{GomokuBoard.WHITE, 0});
        c.winner = GomokuBoard.BLACK;
        c.gameOver = true;
        c.endReason = "改过了";
        c.forbiddenForBlack = false;
        checkEq(b.grid[0], GomokuBoard.EMPTY, "E1 改副本 grid 不影响原件");
        checkEq(b.moves.size(), 3, "E1 改副本手顺不影响原件");
        checkEq(b.winner, GomokuBoard.EMPTY, "E1 改副本 winner 不影响原件");
        check(!b.gameOver, "E1 改副本 gameOver 不影响原件");
        checkEq(b.endReason, "", "E1 改副本 endReason 不影响原件");
        check(b.forbiddenForBlack, "E1 改副本禁手开关不影响原件");

        // winLine 也要深拷贝
        GomokuBoard w = new GomokuBoard(15, false);
        playAll(w, GomokuBoard.BLACK, "7,3 7,4 7,5 7,6 7,7");
        GomokuBoard w2 = w.copy();
        check(w2.winLine != w.winLine, "E1 获胜线数组是新的");
        w2.winLine[0] = 99999;
        checkEq(w.winLine[0], w.idx(7, 3), "E1 改副本获胜线不影响原件");
        GomokuBoard empty = new GomokuBoard(5, false);
        checkEq(empty.copy().winLine, null, "E1 没终局的盘 copy 后获胜线仍是 null");

        // E2 手顺重放得到同样的棋盘
        GomokuBoard b2 = new GomokuBoard(15, true);
        for (int[] mv : b.moves) b2.play(mv[0], mv[1]);
        check(Arrays.equals(b2.grid, b.grid), "E2 重放手顺得到同样的 grid");
        checkEq(b2.winner, b.winner, "E2 重放后 winner 一致");
        checkEq(b2.gameOver, b.gameOver, "E2 重放后 gameOver 一致");

        // E3 长局重放（含终局那一手）
        GomokuBoard g = new GomokuBoard(15, false);
        playAll(g, GomokuBoard.BLACK, "7,3 7,4 7,5 7,6 7,7");
        GomokuBoard g2 = new GomokuBoard(15, false);
        for (int[] mv : g.moves) g2.play(mv[0], mv[1]);
        checkEq(g2.winner, GomokuBoard.BLACK, "E3 重放后同样判黑胜");
        checkEq(Arrays.toString(g2.winLine), Arrays.toString(g.winLine), "E3 重放后获胜线一致");

        // E4 副本可以继续下棋
        GomokuBoard d = b.copy();
        d.play(GomokuBoard.WHITE, d.idx(0, 0));
        checkEq(d.moves.size(), 4, "E4 副本能继续落子");
        checkEq(b.moves.size(), 3, "E4 副本落子不影响原件手顺");
    }

    // ---------- F. 重放（悔棋 / 进程内恢复棋局的口径） ----------

    /** 黑 (7,4)(7,5)(7,6) + (4,7)(5,7)(6,7) → (7,7) 成四四；白在角上应手。 */
    private static List<int[]> fourFourHistory() {
        List<int[]> h = new ArrayList<int[]>();
        int[][] black = {{7, 4}, {7, 5}, {7, 6}, {4, 7}, {5, 7}, {6, 7}, {7, 7}};
        int[][] white = {{0, 0}, {0, 1}, {1, 0}, {1, 1}, {2, 0}, {2, 1}};
        for (int i = 0; i < black.length; i++) {
            h.add(new int[]{GomokuBoard.BLACK, black[i][0] * 15 + black[i][1]});
            if (i < white.length) h.add(new int[]{GomokuBoard.WHITE, white[i][0] * 15 + white[i][1]});
        }
        return h;
    }

    /** 黑 (7,5)(7,6)(5,7)(6,7)，之后 (7,7) 对黑是三三。 */
    private static List<int[]> threeThreeHistory() {
        List<int[]> h = new ArrayList<int[]>();
        int[][] black = {{7, 5}, {7, 6}, {5, 7}, {6, 7}};
        int[][] white = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
        for (int i = 0; i < black.length; i++) {
            h.add(new int[]{GomokuBoard.BLACK, black[i][0] * 15 + black[i][1]});
            if (i < white.length) h.add(new int[]{GomokuBoard.WHITE, white[i][0] * 15 + white[i][1]});
        }
        return h;
    }

    private static void testReplay() {
        int p77 = 7 * 15 + 7;

        // F1 自由规则下重放一串「含四四」的手顺：合法
        GomokuBoard free = GomokuBoard.replay(fourFourHistory(), 15, false);
        checkEq(free.moves.size(), 13, "F1 自由规则重放 13 手");
        checkEq(free.grid[p77], GomokuBoard.BLACK, "F1 第 13 手（四四）落在盘上");
        checkEq(free.winner, GomokuBoard.EMPTY, "F1 重放完还没分出胜负");

        // F2 同一串手顺、开关开着重放也必须成功（已落下的手不重新审判）。
        //    这一条正对着「开着禁手去悔棋 / 恢复棋局 ⇒ IllegalMove」那个崩溃写法。
        GomokuBoard back = GomokuBoard.replay(fourFourHistory(), 15, true);
        checkEq(back.moves.size(), 13, "F2 开着禁手照样能把含四四的旧手顺重放完");
        checkEq(back.grid[p77], GomokuBoard.BLACK, "F2 四四那手还在盘上");
        check(back.forbiddenForBlack, "F2 重放完开关置回（开着）");

        // F3 置回的开关对「新」手仍然生效
        GomokuBoard three = GomokuBoard.replay(threeThreeHistory(), 15, true);
        check(!three.isLegal(GomokuBoard.BLACK, p77), "F3 开关开着：三三点对新棋仍是禁手");
        GomokuBoard threeFree = GomokuBoard.replay(threeThreeHistory(), 15, false);
        check(threeFree.isLegal(GomokuBoard.BLACK, p77), "F3 开关关着：同一点可以下");

        // F4 重放完还能接着下，且成五优先于禁手
        GomokuBoard win = GomokuBoard.replay(fourFourHistory(), 15, true);
        win.play(GomokuBoard.BLACK, 7 * 15 + 3);
        checkEq(win.winner, GomokuBoard.BLACK, "F4 重放后再走一手成五：黑胜（成五优先）");
        check(win.endReason.contains("五连"), "F4 结论写五连：" + win.endReason);

        // F5 重放不了的手顺照旧报错；空手顺得到空盘
        List<int[]> bad = new ArrayList<int[]>();
        bad.add(new int[]{GomokuBoard.BLACK, p77});
        bad.add(new int[]{GomokuBoard.WHITE, p77});
        boolean threw = false;
        try {
            GomokuBoard.replay(bad, 15, false);
        } catch (GomokuBoard.IllegalMove e) {
            threw = e.getMessage() != null && e.getMessage().contains("已有子");
        }
        check(threw, "F5 手顺里重复点位：重放抛 IllegalMove 且说明「已有子」");
        GomokuBoard none = GomokuBoard.replay(new ArrayList<int[]>(), 15, true);
        checkEq(none.moves.size(), 0, "F5 空手顺重放出空盘（0 手）");
        check(none.forbiddenForBlack, "F5 空手顺也把开关置回");
    }

    public static void main(String[] args) throws Exception {
        testBasics();
        testRuns();
        testForbidden();
        testTerminal();
        testCopy();
        testNaming();
        testReplay();

        System.out.println();
        System.out.println("通过 " + passed + " 项，失败 " + failures.size() + " 项");
        if (!failures.isEmpty()) {
            System.out.println("失败清单：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }
}
