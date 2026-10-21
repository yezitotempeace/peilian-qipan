/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

import com.autogo.SudokuGame;

/** 数独纯逻辑：生成合法性、唯一解、三档难度、行列宫冲突、笔记、撤销、存档、种子可复现。 */
public class SudokuTest {

    static int pass, fail;

    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got);
        }
    }

    /** 整盘是否满足「每行 / 每列 / 每宫 1–9 各一次」。 */
    static boolean validFull(int[] g) {
        for (int r = 0; r < 9; r++) {
            boolean[] seen = new boolean[10];
            for (int c = 0; c < 9; c++) {
                int v = g[r * 9 + c];
                if (v < 1 || v > 9 || seen[v]) {
                    return false;
                }
                seen[v] = true;
            }
        }
        for (int c = 0; c < 9; c++) {
            boolean[] seen = new boolean[10];
            for (int r = 0; r < 9; r++) {
                int v = g[r * 9 + c];
                if (v < 1 || v > 9 || seen[v]) {
                    return false;
                }
                seen[v] = true;
            }
        }
        for (int br = 0; br < 3; br++) {
            for (int bc = 0; bc < 3; bc++) {
                boolean[] seen = new boolean[10];
                for (int a = 0; a < 3; a++) {
                    for (int b = 0; b < 3; b++) {
                        int v = g[(br * 3 + a) * 9 + bc * 3 + b];
                        if (v < 1 || v > 9 || seen[v]) {
                            return false;
                        }
                        seen[v] = true;
                    }
                }
            }
        }
        return true;
    }

    static int clues(int[] g) {
        int n = 0;
        for (int v : g) {
            if (v != 0) {
                n++;
            }
        }
        return n;
    }

    public static void main(String[] args) {
        // ── 1. 三档都能出题，且结构合法 ──
        for (int diff = 0; diff <= 2; diff++) {
            long t0 = System.currentTimeMillis();
            SudokuGame g = new SudokuGame(diff, 12345 + diff);
            long ms = System.currentTimeMillis() - t0;

            int[] puz = g.givenSnapshot();
            chk(SudokuGame.DIFF_NAMES[diff] + " 题面结构合法（行列宫无重复）", true, validPartial(puz));
            chk(SudokuGame.DIFF_NAMES[diff] + " 题面提示数 = clues()", clues(puz), g.clues());
            chk(SudokuGame.DIFF_NAMES[diff] + " 唯一解", 1, SudokuGame.countSolutions(puz, 2));
            chk(SudokuGame.DIFF_NAMES[diff] + " 初始棋盘 = 题面", SudokuGame.encode(puz), SudokuGame.encode(g.snapshot()));
            chk(SudokuGame.DIFF_NAMES[diff] + " 开局没有空格被填", 0, 81 - g.remaining() - clues(puz));
            System.out.println("  " + SudokuGame.DIFF_NAMES[diff] + "：提示数 " + g.clues()
                    + "，生成耗时 " + ms + "ms，试探次数 " + SudokuGame.analyse(puz).guesses);
            chk(SudokuGame.DIFF_NAMES[diff] + " 生成不超过 4 秒", true, ms < 4000);
        }

        // ── 2. 难度确实分开：简单不用试探、困难必须多次试探 ──
        SudokuGame easy = new SudokuGame(SudokuGame.EASY, 555);
        SudokuGame normal = new SudokuGame(SudokuGame.NORMAL, 666);
        SudokuGame hard = new SudokuGame(SudokuGame.HARD, 777);

        chk("简单：不需要试探（guesses=0）", 0, SudokuGame.analyse(easy.givenSnapshot()).guesses);
        chk("简单：提示数 >= 40", true, easy.clues() >= 40);
        chk("普通：提示数 >= 28", true, normal.clues() >= 28);
        chk("普通：比简单少（题目更空）", true, normal.clues() < easy.clues());
        chk("困难：需要 >= 2 次试探", true, SudokuGame.analyse(hard.givenSnapshot()).guesses >= 2);
        chk("困难：提示数 <= 26", true, hard.clues() <= 26);
        chk("困难：比普通更空", true, hard.clues() < normal.clues());
        chk("困难：仍然唯一解", 1, SudokuGame.countSolutions(hard.givenSnapshot(), 2));

        // ── 3. 同一 seed 出同一题 ──
        SudokuGame a1 = new SudokuGame(SudokuGame.HARD, 424242);
        SudokuGame a2 = new SudokuGame(SudokuGame.HARD, 424242);
        chk("同种子题面一致", SudokuGame.encode(a1.givenSnapshot()), SudokuGame.encode(a2.givenSnapshot()));

        // ── 4. 行列宫冲突判定（用户加的那条规则） ──
        SudokuGame cg = new SudokuGame(SudokuGame.EASY, 99);
        int r0 = firstEmptyRow(cg);
        int[] empties = emptiesInRow(cg, r0);
        chk("测试行至少有 3 个空格", true, empties.length >= 3);

        int c0 = empties[0], c1 = empties[1], c2 = empties[2];
        chk("填数字前不冲突", false, cg.conflictAt(r0, c0));
        chk("填 5 被接受", true, cg.set(r0, c0, 5));
        chk("同一行再填 5：冲突（标红）", true, cgSetAndCheck(cg, r0, c1, 5));
        chk("冲突格计数 >= 2", true, cg.conflictCount() >= 2);
        chk("冲突时也可以填（行为 A：只标红不拦）", 5, cg.at(r0, c1));

        // 同列冲突
        SudokuGame col = new SudokuGame(SudokuGame.EASY, 100);
        int cc = firstEmptyColWithTwo(col);
        int rr1 = firstEmptyInCol(col, cc, -1);
        int rr2 = firstEmptyInCol(col, cc, rr1);
        col.set(rr1, cc, 7);
        col.set(rr2, cc, 7);
        chk("同一列重复数字：冲突", true, col.conflictAt(rr2, cc));

        // 同宫冲突（不同行不同列）
        SudokuGame box = new SudokuGame(SudokuGame.EASY, 101);
        int[] pair = boxPairWithTwoEmpty(box);
        chk("找到同宫两个空格", true, pair != null);
        if (pair != null) {
            box.set(pair[0], pair[1], 4);
            box.set(pair[2], pair[3], 4);
            chk("同一宫重复数字：冲突", true, box.conflictAt(pair[2], pair[3]));
            chk("同宫重复但不同行列：行不冲突", true, pair[0] != pair[2] && pair[1] != pair[3]);
        }

        // 擦掉后冲突消失
        SudokuGame clear = new SudokuGame(SudokuGame.EASY, 102);
        int er = firstEmptyRow(clear);
        int[] ec = emptiesInRow(clear, er);
        clear.set(er, ec[0], 3);
        clear.set(er, ec[1], 3);
        chk("清除后冲突消失", false, afterClear(clear, er, ec[1]));

        // ── 5. 题面格不能改 ──
        SudokuGame given = new SudokuGame(SudokuGame.EASY, 103);
        int gi = firstGiven(given);
        chk("找到题面格", true, gi >= 0);
        chk("题面格 set 返回 false", false, given.set(gi / 9, gi % 9, 9));
        chk("题面格内容没被改", given.at(gi / 9, gi % 9), given.givenSnapshot()[gi]);

        // ── 6. 提示与撤销 ──
        SudokuGame h = new SudokuGame(SudokuGame.EASY, 104);
        chk("提示前 canUndo=false", false, h.canUndo());
        chk("提示成功", true, h.hint());
        chk("提示后 canUndo=true", true, h.canUndo());
        int filled = firstFilledNotEmpty(h);
        chk("提示填的是正确答案", true, isHintCorrect(h));
        chk("撤销后回到空", true, h.undo());
        chk("撤销后那格空了", 0, h.at(filled / 9, filled % 9));

        // ── 7. 笔记 ──
        SudokuGame nt = new SudokuGame(SudokuGame.EASY, 105);
        int ni = firstEmptyIndex(nt);
        chk("记一个候选 3", true, nt.toggleNote(ni / 9, ni % 9, 3));
        chk("候选位图含 3", true, (nt.noteMask(ni / 9, ni % 9) & (1 << 3)) != 0);
        chk("再点一次取消 3", true, nt.toggleNote(ni / 9, ni % 9, 3));
        chk("候选位图不含 3", false, (nt.noteMask(ni / 9, ni % 9) & (1 << 3)) != 0);
        nt.toggleNote(ni / 9, ni % 9, 5);
        nt.set(ni / 9, ni % 9, 8);
        chk("填数后笔记被清空", 0, nt.noteMask(ni / 9, ni % 9));

        // 笔记存档编解码
        int ni2 = firstEmptyIndex(nt);
        nt.toggleNote(ni2 / 9, ni2 % 9, 1);
        nt.toggleNote(ni2 / 9, ni2 % 9, 9);
        String ns = SudokuGame.encodeNotes(nt.noteSnapshot());
        chk("笔记编码长度 243（每格 3 位十六进制）", 243, ns.length());
        chk("笔记存档往返一致", ns, SudokuGame.encodeNotes(SudokuGame.decodeNotes(ns)));
        chk("笔记存档含 1 和 9 两位", true,
                (SudokuGame.decodeNotes(ns)[ni2] & (1 << 1)) != 0
                        && (SudokuGame.decodeNotes(ns)[ni2] & (1 << 9)) != 0);
        chk("坏笔记存档返回 null", null, SudokuGame.decodeNotes("abc"));

        // ── 8. 完成判定 ──
        SudokuGame done = new SudokuGame(SudokuGame.EASY, 106);
        chk("开局没完成", false, done.solved());
        int[] sol = new int[81];
        for (int i = 0; i < 81; i++) {
            sol[i] = done.solutionAt(i / 9, i % 9);
        }
        for (int i = 0; i < 81; i++) {
            done.set(i / 9, i % 9, sol[i]);
        }
        chk("按答案填满算完成", true, done.solved());
        chk("完成盘结构合法", true, validFull(done.snapshot()));

        // ── 9. 存档往返 ──
        SudokuGame sv = new SudokuGame(SudokuGame.NORMAL, 107);
        String pz = SudokuGame.encode(sv.givenSnapshot());
        String bd = SudokuGame.encode(sv.snapshot());
        int[] np = SudokuGame.decode(pz);
        int[] nb = SudokuGame.decode(bd);
        chk("存档编码长度 81", 81, pz.length());
        chk("解码还原题面", pz, SudokuGame.encode(np));
        chk("解码坏数据返回 null", null, SudokuGame.decode("12x"));
        SudokuGame ld = new SudokuGame(SudokuGame.EASY, 1);
        ld.load(np, nb, new int[81], SudokuGame.NORMAL, 107);
        chk("读档后题面一致", pz, SudokuGame.encode(ld.givenSnapshot()));
        chk("读档后难度一致", SudokuGame.NORMAL, ld.difficulty());
        chk("读档后答案反解正确（提示命中）", true, loadHintCorrect(ld));

        // ── 10. 无冲突局面不误报 ──
        SudokuGame ok = new SudokuGame(SudokuGame.EASY, 108);
        chk("初始局面无冲突", false, ok.hasConflict());

        System.out.println("数独测试：通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }

    // ───────── 辅助 ─────────

    /** 题面（部分填）是否自洽：已填的格行/列/宫都不重复。 */
    static boolean validPartial(int[] g) {
        for (int i = 0; i < 81; i++) {
            int v = g[i];
            if (v == 0) {
                continue;
            }
            int r = i / 9, c = i % 9;
            for (int k = 0; k < 9; k++) {
                if (k != c && g[r * 9 + k] == v) {
                    return false;
                }
                if (k != r && g[k * 9 + c] == v) {
                    return false;
                }
            }
            for (int a = 0; a < 3; a++) {
                for (int b = 0; b < 3; b++) {
                    int rr = (r / 3) * 3 + a, cc = (c / 3) * 3 + b;
                    if ((rr != r || cc != c) && g[rr * 9 + cc] == v) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static int firstEmptyRow(SudokuGame g) {
        for (int r = 0; r < 9; r++) {
            if (emptiesInRow(g, r).length >= 3) {
                return r;
            }
        }
        return 0;
    }

    static int[] emptiesInRow(SudokuGame g, int r) {
        int n = 0;
        for (int c = 0; c < 9; c++) {
            if (g.at(r, c) == 0) {
                n++;
            }
        }
        int[] out = new int[n];
        int k = 0;
        for (int c = 0; c < 9; c++) {
            if (g.at(r, c) == 0) {
                out[k++] = c;
            }
        }
        return out;
    }

    /** 在同一行填同样的数字，返回该格是否冲突。 */
    static boolean cgSetAndCheck(SudokuGame g, int r, int c, int v) {
        g.set(r, c, v);
        return g.conflictAt(r, c);
    }

    static int firstEmptyColWithTwo(SudokuGame g) {
        for (int c = 0; c < 9; c++) {
            int n = 0;
            for (int r = 0; r < 9; r++) {
                if (g.at(r, c) == 0) {
                    n++;
                }
            }
            if (n >= 2) {
                return c;
            }
        }
        return 0;
    }

    static int firstEmptyInCol(SudokuGame g, int c, int skip) {
        for (int r = 0; r < 9; r++) {
            if (r != skip && g.at(r, c) == 0) {
                return r;
            }
        }
        return -1;
    }

    /** 找同一宫里的两个空格，尽量选不同行不同列的（r1,c1,r2,c2）。 */
    static int[] boxPairWithTwoEmpty(SudokuGame g) {
        for (int br = 0; br < 3; br++) {
            for (int bc = 0; bc < 3; bc++) {
                int[] e = new int[6];
                int n = 0;
                for (int a = 0; a < 3; a++) {
                    for (int b = 0; b < 3; b++) {
                        int r = br * 3 + a, c = bc * 3 + b;
                        if (g.at(r, c) == 0 && n + 2 <= e.length) {
                            e[n++] = r;
                            e[n++] = c;
                        }
                    }
                }
                for (int i = 0; i + 3 < n; i += 2) {
                    for (int j = i + 2; j + 1 < n; j += 2) {
                        if (e[i] != e[j] && e[i + 1] != e[j + 1]) {
                            return new int[]{e[i], e[i + 1], e[j], e[j + 1]};
                        }
                    }
                }
            }
        }
        return null;
    }

    static boolean afterClear(SudokuGame g, int r, int c) {
        g.set(r, c, 0);
        return g.conflictAt(r, c);
    }

    static int firstGiven(SudokuGame g) {
        int[] pz = g.givenSnapshot();
        for (int i = 0; i < 81; i++) {
            if (pz[i] != 0) {
                return i;
            }
        }
        return -1;
    }

    static int firstEmptyIndex(SudokuGame g) {
        for (int i = 0; i < 81; i++) {
            if (g.at(i / 9, i % 9) == 0) {
                return i;
            }
        }
        return -1;
    }

    static int firstFilledNotEmpty(SudokuGame g) {
        for (int i = 0; i < 81; i++) {
            if (g.at(i / 9, i % 9) != 0 && !g.isGiven(i / 9, i % 9)) {
                return i;
            }
        }
        return -1;
    }

    static boolean isHintCorrect(SudokuGame g) {
        for (int i = 0; i < 81; i++) {
            int r = i / 9, c = i % 9;
            if (!g.isGiven(r, c) && g.at(r, c) != 0) {
                return g.at(r, c) == g.solutionAt(r, c);
            }
        }
        return false;
    }

    static boolean loadHintCorrect(SudokuGame g) {
        for (int i = 0; i < 81; i++) {
            int r = i / 9, c = i % 9;
            if (g.at(r, c) == 0) {
                int want = g.solutionAt(r, c);
                if (want < 1 || want > 9) {
                    return false;
                }
                return g.hint(r, c) && g.at(r, c) == want;
            }
        }
        return false;
    }
}
