/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.util.ArrayList;
import java.util.List;

/**
 * 军棋 AI（暗棋）—— 完全重写。
 *
 * <p><b>核心思路</b>：暗棋是<b>不完全信息</b>博弈，AI 只能知道：</p>
 * <ul>
 *   <li>己方全部棋子的真实身份；</li>
 *   <li>敌方<b>已经交战过（亮出）</b>的棋子身份；</li>
 *   <li>敌方棋子的位置，以及它<b>是否移动过</b>（移动过 ⇒ 一定不是地雷/军旗）。</li>
 * </ul>
 *
 * <p><b>强档（困难）用「确定性化 + alpha-beta 搜索」</b>——这是暗棋/Stratego 类游戏
 * 的标准强 AI 做法：把未亮出的敌子按剩余类型分布<b>随机采样成一套确定的身份</b>，
 * 在这个“全知世界”里做 alpha-beta 搜索；重复采样多个世界，把每个候选着法的得分
 * <b>取平均</b>，选平均分最高的。这样既不“开天眼”，又能看到几步之后的战术组合。</p>
 *
 * <p><b>快 / 普通档</b>用较轻的单层启发式（快），保证响应速度。</p>
 *
 * <p>难度：0=快、1=普通、2=强（困难，必须难）。</p>
 */
public class JunqiAI {

    public static final int FAST = 0;
    public static final int NORMAL = 1;
    public static final int STRONG = 2;

    /** 强档：确定性化采样几个“可能世界”。 */
    private static final int SAMPLES = 5;
    /** 强档：搜索深度（己方→对方→己方 = 3 层）。 */
    private static final int DEPTH = 3;
    /** 每个节点最多展开多少个着法（控制算力，手机上不能爆炸）。 */
    private static final int BRANCH_LIMIT = 10;

    private int level = NORMAL;
    private final java.util.Random rnd = new java.util.Random();

    public void setLevel(int l) {
        level = (l < FAST || l > STRONG) ? NORMAL : l;
    }

    public int level() {
        return level;
    }

    /** 出招。轮不到黑方或无棋可走时返回 null。 */
    public int[] genmove(JunqiBoard board) {
        if (board == null || board.gameOver() || board.isSetupPhase()) {
            return null;
        }
        List<int[]> moves = board.legalMoves(JunqiBoard.BLACK);
        if (moves.isEmpty()) {
            return null;
        }
        if (level == STRONG) {
            return searchGenmove(board, moves);
        }
        return heuristicGenmove(board, moves);
    }

    // ------------------------------------------------------------ 强档：确定性化 + alpha-beta

    private int[] searchGenmove(JunqiBoard board, List<int[]> moves) {
        double[] acc = new double[moves.size()];
        for (int k = 0; k < SAMPLES; k++) {
            JunqiBoard world = board.copy();
            determinize(world, rnd.nextLong());
            for (int i = 0; i < moves.size(); i++) {
                int[] m = moves.get(i);
                JunqiBoard child = world.copy();
                child.play(JunqiBoard.BLACK, m[0], m[1], m[2], m[3]);
                acc[i] += alphabeta(child, DEPTH - 1, -1e18, 1e18);
            }
        }
        int best = 0;
        for (int i = 1; i < moves.size(); i++) {
            if (acc[i] > acc[best]) {
                best = i;
            }
        }
        return moves.get(best);
    }

    /** 把红方未亮出的棋子随机指定成一套“可能的身份”，并全部视为已知。 */
    private void determinize(JunqiBoard world, long seed) {
        List<Integer> pos = new ArrayList<Integer>();
        for (int r = 0; r < JunqiBoard.ROWS; r++) {
            for (int c = 0; c < JunqiBoard.COLS; c++) {
                JunqiBoard.Pc p = world.at(r, c);
                if (p != null && p.side == JunqiBoard.RED && !p.revealed) {
                    pos.add(JunqiBoard.idx(r, c));
                }
            }
        }
        if (pos.isEmpty()) {
            return;
        }
        int[] unk = unknownCounts(world);
        List<Integer> bag = new ArrayList<Integer>();
        for (int t = 1; t <= 12; t++) {
            for (int i = 0; i < unk[t]; i++) {
                bag.add(realType(t));
            }
        }
        shuffle(bag, new java.util.Random(seed));
        for (int i = 0; i < pos.size(); i++) {
            int idx = pos.get(i);
            JunqiBoard.Pc p = world.at(idx / JunqiBoard.COLS, idx % JunqiBoard.COLS);
            p.type = bag.get(i % bag.size());
        }
        // 修正：移动过的棋子不可能是地雷/军旗，与“没动过且非雷旗”的位置对调
        for (int i = 0; i < pos.size(); i++) {
            int ia = pos.get(i);
            JunqiBoard.Pc pa = world.at(ia / JunqiBoard.COLS, ia % JunqiBoard.COLS);
            if (pa.moved && (pa.type == JunqiBoard.DILEI || pa.type == JunqiBoard.JUNQI)) {
                for (int j = i + 1; j < pos.size(); j++) {
                    int ib = pos.get(j);
                    JunqiBoard.Pc pb = world.at(ib / JunqiBoard.COLS, ib % JunqiBoard.COLS);
                    if (!pb.moved && pb.type != JunqiBoard.DILEI && pb.type != JunqiBoard.JUNQI) {
                        int t = pa.type;
                        pa.type = pb.type;
                        pb.type = t;
                        break;
                    }
                }
            }
        }
        world.revealAll(JunqiBoard.RED);
    }

    private double alphabeta(JunqiBoard b, int depth, double alpha, double beta) {
        if (b.gameOver() || depth <= 0) {
            return evaluate(b);
        }
        int side = b.sideToMove();
        List<int[]> moves = b.legalMoves(side);
        if (moves.isEmpty()) {
            // 无棋可走判负
            return (side == JunqiBoard.BLACK) ? -1e6 : 1e6;
        }
        orderMoves(b, moves);
        int limit = Math.min(moves.size(), BRANCH_LIMIT);
        boolean maxing = (side == JunqiBoard.BLACK);
        double best = maxing ? -1e18 : 1e18;
        for (int i = 0; i < limit; i++) {
            int[] m = moves.get(i);
            JunqiBoard child = b.copy();
            child.play(side, m[0], m[1], m[2], m[3]);
            double v = alphabeta(child, depth - 1, alpha, beta);
            if (maxing) {
                if (v > best) best = v;
                if (v > alpha) alpha = v;
            } else {
                if (v < best) best = v;
                if (v < beta) beta = v;
            }
            if (beta <= alpha) {
                break;
            }
        }
        return best;
    }

    /** 着法排序：吃子（尤其吃高价值子）排前面，提升剪枝效率。 */
    private void orderMoves(JunqiBoard b, List<int[]> moves) {
        double[] score = new double[moves.size()];
        for (int i = 0; i < moves.size(); i++) {
            JunqiBoard.Pc t = b.at(moves.get(i)[2], moves.get(i)[3]);
            score[i] = t == null ? 0 : val(t.type) + 10;
        }
        for (int i = 1; i < moves.size(); i++) {
            int[] mm = moves.get(i);
            double ss = score[i];
            int j = i - 1;
            while (j >= 0 && score[j] < ss) {
                moves.set(j + 1, moves.get(j));
                score[j + 1] = score[j];
                j--;
            }
            moves.set(j + 1, mm);
            score[j + 1] = ss;
        }
    }

    /** 局面评估（黑方视角）。 */
    private double evaluate(JunqiBoard b) {
        if (b.gameOver()) {
            if (b.result() == 2) return 1e7;    // 黑（AI）胜
            if (b.result() == 1) return -1e7;   // 红胜
            return 0;                            // 和
        }
        double s = 0;
        for (int r = 0; r < JunqiBoard.ROWS; r++) {
            for (int c = 0; c < JunqiBoard.COLS; c++) {
                JunqiBoard.Pc p = b.at(r, c);
                if (p == null) {
                    continue;
                }
                double v = val(p.type);
                if (p.side == JunqiBoard.BLACK) {
                    s += v;
                    s += (11 - r) * 1.2;         // 越靠前越有威胁
                    if (b.isCamp(r, c)) s += 4;  // 占行营安全
                } else {
                    s -= v;
                    s -= r * 1.2;
                }
            }
        }
        return s;
    }

    // ------------------------------------------------------------ 快 / 普通档：单层启发式

    private int[] heuristicGenmove(JunqiBoard board, List<int[]> moves) {
        int[] unk = unknownCounts(board);
        double best = -1e18;
        List<int[]> bestMoves = new ArrayList<int[]>();
        for (int[] m : moves) {
            double s = score(board, m, unk);
            if (s > best + 1e-9) {
                best = s;
                bestMoves.clear();
                bestMoves.add(m);
            } else if (s > best - 1e-9) {
                bestMoves.add(m);
            }
        }
        int[] pick = bestMoves.get(0);
        if (level == NORMAL && bestMoves.size() > 1) {
            pick = bestMoves.get(rnd.nextInt(Math.min(bestMoves.size(), 3)));
        } else if (level == FAST) {
            List<int[]> ok = new ArrayList<int[]>();
            for (int[] m : moves) {
                if (score(board, m, unk) > best - 60) {
                    ok.add(m);
                }
            }
            if (!ok.isEmpty()) {
                pick = ok.get(rnd.nextInt(ok.size()));
            }
        } else if (bestMoves.size() > 1) {
            pick = bestMoves.get(rnd.nextInt(bestMoves.size()));
        }
        return new int[]{pick[0], pick[1], pick[2], pick[3]};
    }

    private double score(JunqiBoard b, int[] m, int[] unk) {
        int fr = m[0], fc = m[1], tr = m[2], tc = m[3];
        JunqiBoard.Pc mover = b.at(fr, fc);
        JunqiBoard.Pc target = b.at(tr, tc);
        double s = 0;

        // 翻棋：翻牌也算一步。翻出的是啥不知道，但信息很有价值；
        // 己方还没亮出可动的子时，翻牌优先级更高。
        if (b.isFlipMode() && fr == tr && fc == tc) {
            int ownUp = 0;
            for (int r = 0; r < JunqiBoard.ROWS; r++) {
                for (int c = 0; c < JunqiBoard.COLS; c++) {
                    JunqiBoard.Pc p = b.at(r, c);
                    if (p != null && p.side == JunqiBoard.BLACK && !p.faceDown) ownUp++;
                }
            }
            return 8 + (ownUp == 0 ? 25 : 0) + rnd.nextDouble() * 4;
        }

        if (target != null && target.revealed && target.type == JunqiBoard.JUNQI) {
            return 1e9;
        }

        if (target != null) {
            if (target.revealed) {
                int out = JunqiBoard.fight(mover.type, target.type);
                if (out > 0) {
                    s += 60 + val(target.type);
                    if (target.type == JunqiBoard.JUNQI) s += 1e6;
                } else if (out < 0) {
                    s -= 60 + val(mover.type);
                } else {
                    s += val(target.type) - val(mover.type);
                }
            } else {
                s += unknownFightExpectation(mover, target, unk);
                if (b.isHq(tr, tc)) s += 30;
            }
        } else {
            s += 1.0;
        }

        s += advance(mover, fr, tr);
        if (b.isCamp(tr, tc)) s += 6;
        s -= riskAfterMove(b, m);
        return s;
    }

    private double unknownFightExpectation(JunqiBoard.Pc mover, JunqiBoard.Pc target, int[] unk) {
        int total = 0;
        for (int t = 1; t <= 12; t++) total += unk[t];
        if (total <= 0) return 0;
        double exp = 0;
        for (int t = 1; t <= 12; t++) {
            int n = unk[t];
            if (n == 0) continue;
            int def = realType(t);
            if (target.moved && (def == JunqiBoard.DILEI || def == JunqiBoard.JUNQI)) continue;
            double p = (double) n / total;
            int out = JunqiBoard.fight(mover.type, def);
            double v;
            if (out > 0) v = (def == JunqiBoard.JUNQI) ? 1e6 : 40 + val(def);
            else if (out < 0) v = -(40 + val(mover.type));
            else v = val(def) - val(mover.type);
            exp += p * v;
        }
        return exp;
    }

    private double advance(JunqiBoard.Pc mover, int fromRow, int toRow) {
        double s = 0;
        if (toRow < fromRow) s += (fromRow - toRow) * 1.5;
        if (fromRow >= 6 && toRow <= 5) s += 8;
        if (mover.type == JunqiBoard.GONGBING) s -= 3;
        if (mover.type == JunqiBoard.SILING && toRow <= 5) s -= 4;
        return s;
    }

    private double riskAfterMove(JunqiBoard b, int[] m) {
        int fr = m[0], fc = m[1], tr = m[2], tc = m[3];
        if (b.isFlipMode() && fr == tr && fc == tc) return 0;   // 翻牌不涉及送子
        JunqiBoard.Pc mover = b.at(fr, fc);
        if (mover == null) return 0;
        b.aiSet(fr, fc, null);
        b.aiSet(tr, tc, mover);
        double worst = 0;
        List<int[]> replies = b.legalMoves(JunqiBoard.RED);
        for (int[] r : replies) {
            if (r[2] != tr || r[3] != tc) continue;
            JunqiBoard.Pc attacker = b.at(r[0], r[1]);
            if (attacker == null) continue;
            double loss;
            if (attacker.revealed) {
                int out = JunqiBoard.fight(attacker.type, mover.type);
                if (out > 0) loss = val(mover.type) + 30;
                else if (out < 0) loss = -(val(attacker.type) + 30);
                else loss = val(mover.type) - val(attacker.type);
            } else {
                int[] unk = unknownCounts(b);
                loss = -unknownFightExpectation(mover, attacker, unk) * 0.6;
            }
            if (loss > worst) worst = loss;
        }
        b.aiSet(tr, tc, null);
        b.aiSet(fr, fc, mover);
        return worst;
    }

    // ------------------------------------------------------------ 信息推断

    private int[] unknownCounts(JunqiBoard b) {
        int[] c = new int[13];
        for (int r = 0; r < JunqiBoard.ROWS; r++) {
            for (int cc = 0; cc < JunqiBoard.COLS; cc++) {
                JunqiBoard.Pc p = b.at(r, cc);
                if (p == null || p.side != JunqiBoard.RED || p.revealed) continue;
                c[typeIndex(p.type)]++;
            }
        }
        return c;
    }

    private static int typeIndex(int t) {
        return t > 0 ? t : (9 - t);
    }

    private static int realType(int idx) {
        return idx <= 9 ? idx : (9 - idx);
    }

    private static void shuffle(List<Integer> a, java.util.Random rnd) {
        for (int i = a.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a.get(i);
            a.set(i, a.get(j));
            a.set(j, t);
        }
    }

    /** 棋子价值（AI 内部评估用）。 */
    static double val(int t) {
        switch (t) {
            case JunqiBoard.SILING: return 100;
            case JunqiBoard.JUNZHANG: return 90;
            case JunqiBoard.SHIZHANG: return 78;
            case JunqiBoard.LVZHANG: return 66;
            case JunqiBoard.TUANZHANG: return 55;
            case JunqiBoard.YINGZHANG: return 46;
            case JunqiBoard.LIANZHANG: return 38;
            case JunqiBoard.PAIZHANG: return 30;
            case JunqiBoard.GONGBING: return 50;
            case JunqiBoard.ZHADAN: return 58;
            case JunqiBoard.DILEI: return 42;
            case JunqiBoard.JUNQI: return 1000;
            default: return 0;
        }
    }
}
