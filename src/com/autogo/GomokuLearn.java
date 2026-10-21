/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 五子棋的「经验库」：把下过的局面记下来，下次遇到同样局面时，优先挑历史上赢过的着法。
 *
 * <p>纯 Java、不依赖 Android，可直接单测。数据只有两类：</p>
 * <ul>
 *   <li><b>战绩</b>：局面（含轮到谁走）→ 该局面下每个落点被下过几次、各赢/输/和多少；</li>
 *   <li><b>你的失误</b>：局面（含轮到人走）→ 人在这里下过的、复盘判定为大失误的落点及次数。</li>
 * </ul>
 *
 * <p>取值口径：{@link #pick} 只在两个前提下改写引擎的首选 —— 历史上该着法的样本够多
 * （{@link #MIN_SAMPLES}）且胜率够高（{@link #GOOD_RATE}），或者候选落子能把你带进
 * 「你曾经在这里犯过大失误」的局面。其余情况一律听引擎的，避免越学越弱。</p>
 */
// 非 final：tests/ui/Driver95.java 的 ⑲b/⑲c 用子类记账（计数 recordGame/recordBlunders 调用）
public class GomokuLearn {

    /** 落点至少被下过这么多次，才敢照经验走。 */
    public static final int MIN_SAMPLES = 3;
    /** 历史胜率至少这么高，才敢照经验走。 */
    public static final double GOOD_RATE = 0.6;
    /** 经验库最多记多少个局面（超了按「下得最少」的淘汰）。 */
    public static final int MAX_POSITIONS = 20000;
    /** 默认文件名。 */
    public static final String FILE_NAME = "gomoku-learn.dat";

    /** 一个落点上的战绩。 */
    public static final class MoveStats {
        public int plays;
        public int wins;
        public int losses;
        public int draws;

        public void add(int result, int mover) {
            plays++;
            if (result == GomokuBoard.EMPTY) {
                draws++;
            } else if (result == mover) {
                wins++;
            } else {
                losses++;
            }
        }

        /** 胜率（平局算半分；没下过返回 0.5，避免空样本被当成好或坏）。 */
        public double rate() {
            if (plays <= 0) return 0.5;
            return (wins + draws * 0.5) / plays;
        }

        public String encode() {
            return plays + ":" + wins + ":" + losses + ":" + draws;
        }

        public static MoveStats decode(String s) {
            String[] f = s.split(":");
            if (f.length != 4) throw new IllegalArgumentException("字段数不是 4：" + s);
            MoveStats m = new MoveStats();
            m.plays = Integer.parseInt(f[0]);
            m.wins = Integer.parseInt(f[1]);
            m.losses = Integer.parseInt(f[2]);
            m.draws = Integer.parseInt(f[3]);
            if (m.plays < 0 || m.wins < 0 || m.losses < 0 || m.draws < 0
                    || m.wins + m.losses + m.draws > m.plays) {
                throw new IllegalArgumentException("数字不自洽：" + s);
            }
            return m;
        }
    }

    private final LinkedHashMap<String, LinkedHashMap<Integer, MoveStats>> table = new LinkedHashMap<>();
    private final LinkedHashMap<String, LinkedHashMap<Integer, Integer>> blunders = new LinkedHashMap<>();
    private int games;
    private int maxPositions = MAX_POSITIONS;

    /** 仅供测试：把容量调小，验证淘汰逻辑。 */
    public void setMaxPositions(int max) {
        if (max > 0) maxPositions = max;
    }

    // ---------- 查询 ----------

    public int games() {
        return games;
    }

    public int positions() {
        return table.size();
    }

    public int blunderPositions() {
        return blunders.size();
    }

    public int totalRecords() {
        int n = 0;
        for (LinkedHashMap<Integer, MoveStats> m : table.values()) n += m.size();
        return n;
    }

    /** 某局面下某落点的战绩；没有记录返回 null。 */
    public MoveStats statsAt(String hash, int point) {
        LinkedHashMap<Integer, MoveStats> m = table.get(hash);
        if (m == null) return null;
        return m.get(point);
    }

    /** 某局面下「你犯过的失误落点」的累计次数；没有返回 0。 */
    public int blunderCountAt(String hash, int point) {
        LinkedHashMap<Integer, Integer> m = blunders.get(hash);
        if (m == null) return 0;
        Integer v = m.get(point);
        return v == null ? 0 : v;
    }

    // ---------- 记录 ----------

    /**
     * 记一盘棋的结果（result = 胜方颜色，和棋传 EMPTY）。
     *
     * 手顺非法时**整盘不记**：先拿一副临时棋盘把这盘棋走完验一遍，验不过就直接返回 false，
     * 一点数据都不写（否则「返回 false 却留下半盘脏数据」，调用方没法收拾）。
     */
    public boolean recordGame(List<int[]> moves, int size, int result) {
        if (moves == null || moves.isEmpty()) return false;
        if (replay(moves, size) == null) return false;   // 先验手顺，验不过不写
        GomokuBoard b = new GomokuBoard(size, false);
        for (int[] mv : moves) {
            int point = mv[1], color = mv[0];
            String h = hash(b, color);
            LinkedHashMap<Integer, MoveStats> m = table.get(h);
            if (m == null) {
                m = new LinkedHashMap<>();
                table.put(h, m);
            }
            MoveStats st = m.get(point);
            if (st == null) {
                st = new MoveStats();
                m.put(point, st);
            }
            st.add(result, color);
            try {
                b.play(color, point);
            } catch (GomokuBoard.IllegalMove e) {
                // 前面已经验过手顺，正常到不了这里；真到了说明棋盘实现变了，宁可整盘回滚也不留脏数据。
                table.remove(h);
                return false;
            }
        }
        games++;
        evictIfNeeded();
        return true;
    }

    /**
     * 记复盘发现的「你的失误」：loss[i] 是第 i+1 手的亏损（与 {@code GomokuReview.Review#loss} 同口径）。
     * 只有当第 i 手的落子方 == humanColor 且亏损 ≥ blunderMin 时才记。
     *
     * 与 {@link #recordGame} 一样先验手顺：手顺非法 ⇒ 返回 0 且一条都不写（不留半份数据）。
     */
    public int recordBlunders(List<int[]> moves, int size, int humanColor, double[] loss, double blunderMin) {
        if (moves == null || moves.isEmpty() || loss == null) return 0;
        if (replay(moves, size) == null) return 0;       // 先验手顺，验不过不写
        GomokuBoard b = new GomokuBoard(size, false);
        int n = 0;
        for (int i = 0; i < moves.size(); i++) {
            int[] mv = moves.get(i);
            int point = mv[1], color = mv[0];
            if (color == humanColor && i < loss.length && !Double.isNaN(loss[i]) && loss[i] >= blunderMin) {
                String h = hash(b, color);
                LinkedHashMap<Integer, Integer> m = blunders.get(h);
                if (m == null) {
                    m = new LinkedHashMap<>();
                    blunders.put(h, m);
                }
                Integer c = m.get(point);
                m.put(point, c == null ? 1 : c + 1);
                n++;
            }
            try {
                b.play(color, point);
            } catch (GomokuBoard.IllegalMove e) {
                break;
            }
        }
        evictIfNeeded();
        return n;
    }

    /** 你自己下的那一手的落点（第 i 手），便于复盘侧对齐。 */
    public static int pointOf(List<int[]> moves, int index) {
        return moves.get(index)[1];
    }

    /**
     * 把一手顺在临时棋盘上走一遍：能走完返回终局棋盘，中途非法返回 null（调用方据此决定记不记）。
     * 「先验后记」是为了让记录动作要么整盘生效、要么一点不动。
     */
    private static GomokuBoard replay(List<int[]> moves, int size) {
        GomokuBoard b = new GomokuBoard(size, false);
        for (int[] mv : moves) {
            if (mv == null || mv.length < 2) return null;
            try {
                b.play(mv[0], mv[1]);
            } catch (GomokuBoard.IllegalMove e) {
                return null;
            }
        }
        return b;
    }

    // ---------- 选择着法 ----------

    /**
     * 在「引擎给的候选」里照经验挑一手。
     *
     * @param b        当前局面
     * @param color    AI 执的颜色
     * @param ranked   引擎给的候选（按好坏排序，越靠前越好；可为 null/空）
     * @param engineTop 引擎的首选（兜底答案）
     * @return 最终着法（点号）；无处可下返回 -1
     */
    public int pick(GomokuBoard b, int color, int[] ranked, int engineTop) {
        if (engineTop < 0) return -1;
        if (ranked == null || ranked.length == 0) return engineTop;
        String h = hash(b, color);
        // 1) 历史上赢过的着法（样本够、胜率够）
        for (int p : ranked) {
            MoveStats st = statsAt(h, p);
            if (st != null && st.plays >= MIN_SAMPLES && st.rate() >= GOOD_RATE) return p;
        }
        // 2) 「把你带进你常犯错的局面」的着法：下一手轮到对方，对方在你曾失误的位置有过记录
        for (int p : ranked) {
            if (!isPairable(b, color, p)) continue;
            GomokuBoard child = childBoard(b, color, p);
            if (child == null) continue;
            String ch = hash(child, GomokuBoard.opponent(color));
            LinkedHashMap<Integer, Integer> m = blunders.get(ch);
            if (m == null) continue;
            int hits = 0;
            for (Integer c : m.values()) hits += c;
            if (hits >= 2) return p;
        }
        return engineTop;
    }

    private static boolean isPairable(GomokuBoard b, int color, int point) {
        return point >= 0 && point < b.size * b.size && b.grid[point] == GomokuBoard.EMPTY && b.isLegal(color, point);
    }

    private static GomokuBoard childBoard(GomokuBoard b, int color, int point) {
        if (!isPairable(b, color, point)) return null;
        GomokuBoard c = b.copy();
        try {
            c.play(color, point);
        } catch (GomokuBoard.IllegalMove e) {
            return null;
        }
        return c;
    }

    // ---------- 哈希 ----------

    /** 局面（含轮到谁走）的稳定哈希：同局面同轮次必同值，任一石子/路数/轮次不同则不同。 */
    public static String hash(GomokuBoard b, int sideToMove) {
        long h = 0xcbf29ce484222325L;
        h = mix(h, b.size);
        h = mix(h, sideToMove);
        for (int v : b.grid) h = mix(h, v);
        return String.format("%016x", h);
    }

    private static long mix(long h, int v) {
        h ^= (v & 0xffL);
        h *= 0x100000001b3L;
        return h;
    }

    // ---------- 淘汰与清理 ----------

    private void evictIfNeeded() {
        if (table.size() <= maxPositions) return;
        while (table.size() > maxPositions) {
            String worst = null;
            int worstPlays = Integer.MAX_VALUE;
            for (Map.Entry<String, LinkedHashMap<Integer, MoveStats>> e : table.entrySet()) {
                int plays = 0;
                for (MoveStats st : e.getValue().values()) plays += st.plays;
                if (plays < worstPlays) {
                    worstPlays = plays;
                    worst = e.getKey();
                }
            }
            if (worst == null) break;
            table.remove(worst);
            blunders.remove(worst);
        }
    }

    /** 清空全部学习数据。 */
    public void clear() {
        table.clear();
        blunders.clear();
        games = 0;
    }

    /** 给界面用的一句话统计。 */
    public String describe() {
        if (games == 0 && table.isEmpty()) {
            return "还没学到东西：下几盘（或在复盘页看过失误）之后这里会有数据。";
        }
        return "累计学了 " + games + " 盘、" + table.size() + " 个局面（"
                + totalRecords() + " 条战绩），其中你的失误局面 " + blunderPositions() + " 个。";
    }

    // ---------- 存盘 ----------

    public void save(File f) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            StringBuilder sb = new StringBuilder();
            sb.append("V1\n");
            sb.append("games ").append(games).append('\n');
            for (Map.Entry<String, LinkedHashMap<Integer, MoveStats>> e : table.entrySet()) {
                sb.append("pos ").append(e.getKey()).append(' ');
                boolean first = true;
                for (Map.Entry<Integer, MoveStats> m : e.getValue().entrySet()) {
                    if (!first) sb.append(',');
                    sb.append(m.getKey()).append(':').append(m.getValue().encode());
                    first = false;
                }
                sb.append('\n');
            }
            for (Map.Entry<String, LinkedHashMap<Integer, Integer>> e : blunders.entrySet()) {
                sb.append("blu ").append(e.getKey()).append(' ');
                boolean first = true;
                for (Map.Entry<Integer, Integer> m : e.getValue().entrySet()) {
                    if (!first) sb.append(',');
                    sb.append(m.getKey()).append(':').append(m.getValue());
                    first = false;
                }
                sb.append('\n');
            }
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 读盘：文件不存在当空的，坏行跳过（宁可少学，也不能因为文件坏了打不开 App）。 */
    public static GomokuLearn load(File f) {
        GomokuLearn g = new GomokuLearn();
        if (f == null || !f.exists() || !f.isFile()) return g;
        byte[] raw;
        try (FileInputStream in = new FileInputStream(f)) {
            raw = new byte[(int) Math.min(f.length(), 8L * 1024 * 1024)];
            int off = 0, r;
            while (off < raw.length && (r = in.read(raw, off, raw.length - off)) > 0) off += r;
        } catch (IOException e) {
            return g;
        }
        String text = new String(raw, StandardCharsets.UTF_8);
        for (String line : text.split("\n")) {
            String s = line.trim();
            if (s.isEmpty()) continue;
            try {
                if (s.startsWith("games ")) {
                    g.games = Integer.parseInt(s.substring(6).trim());
                } else if (s.startsWith("pos ")) {
                    String[] parts = s.split(" ");
                    if (parts.length != 3) continue;
                    LinkedHashMap<Integer, MoveStats> m = new LinkedHashMap<>();
                    for (String one : parts[2].split(",")) {
                        if (one.isEmpty()) continue;
                        int c = one.indexOf(':');
                        if (c <= 0) throw new IllegalArgumentException("缺冒号：" + one);
                        m.put(Integer.parseInt(one.substring(0, c)), MoveStats.decode(one.substring(c + 1)));
                    }
                    g.table.put(parts[1], m);
                } else if (s.startsWith("blu ")) {
                    String[] parts = s.split(" ");
                    if (parts.length != 3) continue;
                    LinkedHashMap<Integer, Integer> m = new LinkedHashMap<>();
                    for (String one : parts[2].split(",")) {
                        if (one.isEmpty()) continue;
                        int c = one.indexOf(':');
                        if (c <= 0) throw new IllegalArgumentException("缺冒号：" + one);
                        m.put(Integer.parseInt(one.substring(0, c)), Integer.parseInt(one.substring(c + 1)));
                    }
                    g.blunders.put(parts[1], m);
                }
            } catch (RuntimeException e) {
                // 单行坏了只跳过这一行
            }
        }
        return g;
    }

    /** 供测试/统计：所有记录过的落点（点号）列表。 */
    public List<Integer> movesAt(String hash) {
        LinkedHashMap<Integer, MoveStats> m = table.get(hash);
        if (m == null) return new ArrayList<>();
        return new ArrayList<>(m.keySet());
    }
}
