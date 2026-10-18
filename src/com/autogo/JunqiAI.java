package com.autogo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 军棋 AI（暗棋/明棋共用）。核心约束：**不得偷看**未暴露的敌子 ——
 * 决策前先把「未暴露的敌子」统一替换成 {@link #UNKNOWN}，因此改变这些棋子的真实兵种
 * 不会改变 AI 的走法（这一条用测试钉住）。
 *
 * <p>三档难度是三套算法，快档也保持「能玩」而不是乱走：
 * <ul>
 *   <li>快（FAST）：1 层贪心（子力差 + 推进），一步算完就走，速度最快；</li>
 *   <li>普通（NORMAL）：1 层贪心 + 对未知敌子的期望估值（会避雷/避炸弹/避开大子）；</li>
 *   <li>强（STRONG）：前 24 个候选做 2 层搜索（对手全部回手），未知子同样按期望估值。</li>
 * </ul>
 * 三档都会吃 {@link JunqiLearn} 的经验加权（同一份学习库，所有档位共用）。
 */
public final class JunqiAI {

    /** 未暴露的敌子在 AI 眼里就是「一个排长」，只是一个占位估值。 */
    public static final int UNKNOWN = JunqiBoard.PLATOON;

    public static final int FAST = 0;
    public static final int NORMAL = 1;
    public static final int STRONG = 2;

    /** 强档看多少个 1 层候选（再逐个模拟对手回手）。 */
    private static final int STRONG_CANDIDATES = 48;
    /** 终局分（远大于任何子力差）。 */
    private static final int WIN_SCORE = 100000;

    private int level = NORMAL;
    private JunqiLearn learn;

    public JunqiAI() {
    }

    public JunqiAI(int level) {
        setLevel(level);
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        if (level < FAST || level > STRONG) {
            this.level = NORMAL;
        } else {
            this.level = level;
        }
    }

    /** 学习库：三档共用；传 null 表示关闭学习。 */
    public void setLearn(JunqiLearn learn) {
        this.learn = learn;
    }

    /** 把棋盘上「不是 color 一方、且没被暴露」的棋子统一换成 {@link #UNKNOWN}。 */
    public static JunqiBoard masked(JunqiBoard board, int color, boolean[] revealed) {
        JunqiBoard view = board.copy();
        for (int r = 0; r < JunqiBoard.ROWS; r++) {
            for (int c = 0; c < JunqiBoard.COLS; c++) {
                int owner = view.ownerAt(r, c);
                if (owner == JunqiBoard.EMPTY || owner == color) {
                    continue;
                }
                int p = JunqiBoard.idx(r, c);
                if (revealed == null || p >= revealed.length || !revealed[p]) {
                    view.put(r, c, owner, UNKNOWN);
                }
            }
        }
        return view;
    }

    /**
     * 出一手。返回 {fromRow, fromCol, toRow, toCol}；终局或无棋可走返回 null。
     *
     * @param revealed 长度 = COLS*ROWS；true 表示该格棋子已暴露（双方都看得见）。可为 null（全暗）
     */
    public int[] genmove(JunqiBoard board, int color, boolean[] revealed) {
        return genmove(board, color, revealed, null);
    }

    /** 同上；frames 不为 null 时，把这一手的「局面特征」追加进去（学习库用）。 */
    public int[] genmove(JunqiBoard board, int color, boolean[] revealed, List<String> frames) {
        if (board == null || board.gameOver()) {
            return null;
        }
        JunqiBoard view = masked(board, color, revealed);
        List<int[]> moves = view.legalMoves(color);
        if (moves.isEmpty()) {
            return null;
        }
        boolean[] hidden = null;
        if (level != FAST) {
            // 只把「哪些敌格还没亮」做成掩码传给搜索；不把 revealed 数组本身交出去（守卫要求）。
            hidden = new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
            for (int r = 0; r < JunqiBoard.ROWS; r++) {
                for (int c = 0; c < JunqiBoard.COLS; c++) {
                    int p = JunqiBoard.idx(r, c);
                    int owner = board.ownerAt(r, c);
                    if (owner != JunqiBoard.EMPTY && owner != color
                            && (revealed == null || p >= revealed.length || !revealed[p])) {
                        hidden[p] = true;
                    }
                }
            }
        }
        int[] chosen;
        if (level == STRONG) {
            chosen = pickStrong(view, color, moves, hidden);
        } else if (level == FAST) {
            chosen = pickFast(view, color, moves);
        } else {
            chosen = pickNormal(view, color, moves, hidden);   // 普通档：材料 + 未知子期望
        }
        if (frames != null && chosen != null) {
            frames.add(JunqiLearn.keyFor(view, color, chosen));
        }
        return to4(chosen);
    }

    /** 普通档：1 层贪心 + 对未知敌子的期望（会避雷/避炸弹/避开大子）。 */
    private int[] pickNormal(JunqiBoard view, int color, List<int[]> moves, boolean[] hidden) {
        int base = evaluate(view, color);
        int[] best = moves.get(0);
        int bestValue = valueOfNormal(view, color, moves.get(0), hidden, base);
        for (int i = 1; i < moves.size(); i++) {
            int[] mv = moves.get(i);
            int v = valueOfNormal(view, color, mv, hidden, base);
            if (v > bestValue) {
                bestValue = v;
                best = mv;
            }
        }
        return best;
    }

    private int valueOfNormal(JunqiBoard view, int color, int[] mv, boolean[] hidden, int base) {
        int tr = JunqiBoard.rowOf(mv[1]);
        int tc = JunqiBoard.colOf(mv[1]);
        int at = JunqiBoard.idx(tr, tc);
        int value;
        if (hidden != null && hidden[at]) {
            value = base + expectedAttack(view, color, mv[0], mv[1]);
        } else {
            value = base + score(view, color, mv);
        }
        return value - repeatPenalty(view, mv) + learnBonus(view, color, mv);
    }

    /** 快档：1 层贪心（不额外算未知子期望），出手快，但会正常吃子、推进，不再乱走。 */
    private int[] pickFast(JunqiBoard view, int color, List<int[]> moves) {
        int[] best = moves.get(0);
        int bestValue = score(view, color, best) - repeatPenalty(view, best) + learnBonus(view, color, best);
        for (int i = 1; i < moves.size(); i++) {
            int[] mv = moves.get(i);
            int v = score(view, color, mv) - repeatPenalty(view, mv) + learnBonus(view, color, mv);
            if (v > bestValue) {
                bestValue = v;
                best = mv;
            }
        }
        return best;
    }

    /**
     * 强档：1 层取前 48 个候选，再看对手全部回手里最坏的那个（2 层）；
     * 未知子按期望估值；三档都吃学习库加成。
     */
    private int[] pickStrong(JunqiBoard view, int color, List<int[]> moves, boolean[] hidden) {
        List<int[]> scored = new ArrayList<int[]>(moves.size());
        for (int[] mv : moves) {
            scored.add(new int[]{mv[0], mv[1], score(view, color, mv) - repeatPenalty(view, mv)
                    + learnBonus(view, color, mv)});
        }
        Collections.sort(scored, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(b[2], a[2]);
            }
        });
        int limit = Math.min(STRONG_CANDIDATES, scored.size());
        int bestValue = Integer.MIN_VALUE;
        int[] best = null;
        for (int i = 0; i < limit; i++) {
            int[] cand = scored.get(i);
            int tr0 = JunqiBoard.rowOf(cand[1]);
            int tc0 = JunqiBoard.colOf(cand[1]);
            int at0 = JunqiBoard.idx(tr0, tc0);
            int bonus = learnBonusPoints(view, color, cand[0], cand[1]);
            if (hidden[at0]) {
                int ev = evaluate(view, color) + expectedAttack(view, color, cand[0], cand[1])
                        - repeatPenalty(view, cand) + bonus;
                if (ev > bestValue) {
                    bestValue = ev;
                    best = cand;
                }
                continue;
            }
            JunqiBoard next = view.copy();
            next.play(JunqiBoard.rowOf(cand[0]), JunqiBoard.colOf(cand[0]),
                    JunqiBoard.rowOf(cand[1]), JunqiBoard.colOf(cand[1]));
            int value;
            if (next.gameOver()) {
                value = next.winner() == color ? WIN_SCORE : -WIN_SCORE;
            } else {
                value = evaluate(next, color);
                for (int[] reply : next.legalMoves(JunqiBoard.opponent(color))) {
                    int tr = JunqiBoard.rowOf(reply[1]);
                    int tc = JunqiBoard.colOf(reply[1]);
                    JunqiBoard after = next.copy();
                    after.play(JunqiBoard.rowOf(reply[0]), JunqiBoard.colOf(reply[0]), tr, tc);
                    int v;
                    if (after.gameOver()) {
                        v = after.winner() == color ? WIN_SCORE : -WIN_SCORE;
                    } else {
                        v = evaluate(after, color);
                        // 再往下看一手「我能马上反吃回来吗」（只展开吃子；只给前 12 个候选加深，控制耗时）
                        for (int[] back : (i < 12 ? after.legalMoves(color) : java.util.Collections.<int[]>emptyList())) {
                            int btr = JunqiBoard.rowOf(back[1]);
                            int btc = JunqiBoard.colOf(back[1]);
                            if (after.ownerAt(btr, btc) == JunqiBoard.EMPTY) {
                                continue;
                            }
                            JunqiBoard after2 = after.copy();
                            after2.play(JunqiBoard.rowOf(back[0]), JunqiBoard.colOf(back[0]), btr, btc);
                            int v2;
                            if (after2.gameOver()) {
                                v2 = after2.winner() == color ? WIN_SCORE : -WIN_SCORE;
                            } else {
                                v2 = evaluate(after2, color);
                            }
                            if (v2 > v) {
                                v = v2;
                            }
                        }
                    }
                    if (v < value) {
                        value = v;
                    }
                }
            }
            value += bonus;
            if (value > bestValue) {
                bestValue = value;
                best = cand;
            }
        }
        return best == null ? moves.get(0) : new int[]{best[0], best[1]};
    }

    private int learnBonus(JunqiBoard view, int color, int[] mv) {
        return learn == null ? 0 : learn.bonus(JunqiLearn.keyFor(view, color, mv));
    }

    private int learnBonusPoints(JunqiBoard view, int color, int from, int to) {
        return learn == null ? 0 : learn.bonus(JunqiLearn.keyFor(view, color, new int[]{from, to}));
    }

    /** legalMoves 用的是 {fromPoint, toPoint}，对外返回 {fr, fc, tr, tc}。 */
    private static int[] to4(int[] two) {
        return new int[]{JunqiBoard.rowOf(two[0]), JunqiBoard.colOf(two[0]),
                JunqiBoard.rowOf(two[1]), JunqiBoard.colOf(two[1])};
    }

    private int score(JunqiBoard view, int color, int[] mv) {
        JunqiBoard next = view.copy();
        int before = evaluate(next, color);
        next.play(JunqiBoard.rowOf(mv[0]), JunqiBoard.colOf(mv[0]),
                JunqiBoard.rowOf(mv[1]), JunqiBoard.colOf(mv[1]));
        int s = evaluate(next, color) - before;
        next = null;
        return s;
    }

    /** 从 color 视角看子力差 + 少量推进奖励。 */
    public static int evaluate(JunqiBoard board, int color) {
        int sum = 0;
        for (int r = 0; r < JunqiBoard.ROWS; r++) {
            for (int c = 0; c < JunqiBoard.COLS; c++) {
                int owner = board.ownerAt(r, c);
                if (owner == JunqiBoard.EMPTY) {
                    continue;
                }
                int type = board.at(r, c);
                int v = value(type);
                if (type != JunqiBoard.MINE && type != JunqiBoard.FLAG && !JunqiBoard.isHq(r, c)) {
                    int depth = owner == JunqiBoard.RED ? (JunqiBoard.ROWS - 1 - r) : r;
                    v += depth * 4;
                }
                sum += (owner == color) ? v : -v;
            }
        }
        return sum;
    }

    /** 上一手是 A→B，这手又 B→A：来回蹭的惩罚。 */
    private static int repeatPenalty(JunqiBoard board, int[] mv) {
        List<int[]> h = board.history();
        if (h.isEmpty()) {
            return 0;
        }
        int[] last = h.get(h.size() - 1);
        int lastFrom = JunqiBoard.idx(last[0], last[1]);
        int lastTo = JunqiBoard.idx(last[2], last[3]);
        return (mv[0] == lastTo && mv[1] == lastFrom) ? 40 : 0;
    }

    /** 敌方 25 子的先验构成（用于「打未知子」的期望值；按位置再过滤地雷/炸弹/军旗）。 */
    private static final int[] PRIOR_TYPES = {
            JunqiBoard.COMMANDER, JunqiBoard.ARMY, JunqiBoard.DIVISION, JunqiBoard.BRIGADE,
            JunqiBoard.REGIMENT, JunqiBoard.BATTALION, JunqiBoard.COMPANY, JunqiBoard.PLATOON,
            JunqiBoard.ENGINEER, JunqiBoard.MINE, JunqiBoard.BOMB, JunqiBoard.FLAG
    };
    private static final int[] PRIOR_COUNTS = {1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 2, 1};

    /**
     * 攻击一个「未知敌子」的期望子力收益（用敌方 25 子的先验构成）。
     * 位置会过滤掉不可能的兵种：军旗只可能在大本营、地雷只在后两排、炸弹不在第一排。
     */
    static int expectedAttack(JunqiBoard view, int color, int fromPoint, int toPoint) {
        int atk = view.at(JunqiBoard.rowOf(fromPoint), JunqiBoard.colOf(fromPoint));
        int tr = JunqiBoard.rowOf(toPoint);
        int tc = JunqiBoard.colOf(toPoint);
        int enemy = JunqiBoard.opponent(color);
        boolean enemyHq = JunqiBoard.isHq(tr, tc) && (enemy == JunqiBoard.BLACK ? tr == 0 : tr == JunqiBoard.ROWS - 1);
        boolean backTwo = enemy == JunqiBoard.BLACK ? (tr == 0 || tr == 1) : (tr == 10 || tr == 11);
        boolean front = enemy == JunqiBoard.BLACK ? tr == 5 : tr == 6;
        long sum = 0;
        int total = 0;
        for (int i = 0; i < PRIOR_TYPES.length; i++) {
            int type = PRIOR_TYPES[i];
            if (type == JunqiBoard.FLAG && !enemyHq) {
                continue;
            }
            if (type == JunqiBoard.MINE && !backTwo) {
                continue;
            }
            if (type == JunqiBoard.BOMB && front) {
                continue;
            }
            int count = PRIOR_COUNTS[i];
            total += count;
            sum += (long) count * combatDelta(atk, type);
        }
        return total == 0 ? 0 : (int) Math.round(sum / (double) total);
    }

    /** 我方 atk 攻击敌方 type 的子力差（正数=赚）。 */
    private static int combatDelta(int atk, int def) {
        if (def == JunqiBoard.FLAG) {
            return value(JunqiBoard.FLAG);
        }
        if (atk == JunqiBoard.BOMB) {
            return value(def) - value(atk);
        }
        if (def == JunqiBoard.BOMB) {
            return value(JunqiBoard.BOMB) - value(atk);
        }
        if (def == JunqiBoard.MINE) {
            return atk == JunqiBoard.ENGINEER ? value(JunqiBoard.MINE) : -value(atk);
        }
        if (atk > def) {
            return value(def);
        }
        if (atk < def) {
            return -value(atk);
        }
        return value(def) - value(atk);
    }

    private static int value(int type) {
        switch (type) {
            case JunqiBoard.COMMANDER: return 900;
            case JunqiBoard.ARMY: return 700;
            case JunqiBoard.DIVISION: return 500;
            case JunqiBoard.BRIGADE: return 400;
            case JunqiBoard.REGIMENT: return 300;
            case JunqiBoard.BATTALION: return 250;
            case JunqiBoard.COMPANY: return 200;
            case JunqiBoard.PLATOON: return 150;
            case JunqiBoard.ENGINEER: return 120;
            case JunqiBoard.BOMB: return 260;
            case JunqiBoard.MINE: return 180;
            case JunqiBoard.FLAG: return 3000;
            default: return 100;
        }
    }
}
