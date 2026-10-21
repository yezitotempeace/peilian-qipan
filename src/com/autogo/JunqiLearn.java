/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 军棋的「学习库」：所有难度档共用。每盘棋结束后，把电脑走子的**局面特征**和胜负记下来；
 * 下次遇到同类特征时，历史胜率高的走法会被加权（快/普通/强都吃这一层经验）。
 *
 * <p>为什么不用精确局面哈希：军棋开局是随机摆子，精确局面几乎不会重复；
 * 所以按「进攻子兵种 + 目标类型（空/已知/未知）+ 目标区域 + 方向」做特征，才能跨局复用。</p>
 *
 * <p>纯 Java、不依赖 Android，可单测；文件格式一行一个特征：key\tplays:wins:losses:draws。</p>
 */
public final class JunqiLearn {

    /** 至少下过这么多次，才敢按经验加权。 */
    public static final int MIN_SAMPLES = 3;
    /** 历史胜率高于这个才给正加权（低于则负加权）。 */
    public static final double GOOD_RATE = 0.60;
    /** 最多记多少个特征。 */
    public static final int MAX_KEYS = 4000;
    /** 默认文件名。 */
    public static final String FILE_NAME = "junqi-learn.dat";

    /** 一局对电脑的结果（从电脑视角）。 */
    public static final int WIN = 1;
    public static final int DRAW = 0;
    public static final int LOSS = -1;

    static final class Stats {
        int plays, wins, losses, draws;

        void add(int result) {
            plays++;
            if (result > 0) {
                wins++;
            } else if (result < 0) {
                losses++;
            } else {
                draws++;
            }
        }

        double rate() {
            return plays <= 0 ? 0.5 : (wins + draws * 0.5) / plays;
        }

        String encode() {
            return plays + ":" + wins + ":" + losses + ":" + draws;
        }

        static Stats decode(String s) {
            String[] f = s.split(":");
            if (f.length != 4) {
                throw new IllegalArgumentException("字段数不是 4：" + s);
            }
            Stats st = new Stats();
            st.plays = Integer.parseInt(f[0]);
            st.wins = Integer.parseInt(f[1]);
            st.losses = Integer.parseInt(f[2]);
            st.draws = Integer.parseInt(f[3]);
            if (st.plays < 0 || st.wins < 0 || st.losses < 0 || st.draws < 0
                    || st.wins + st.losses + st.draws > st.plays) {
                throw new IllegalArgumentException("数字不自洽：" + s);
            }
            return st;
        }
    }

    private final LinkedHashMap<String, Stats> table = new LinkedHashMap<String, Stats>();
    private int games;
    private int wins;
    private int losses;
    private int draws;
    private int maxKeys = MAX_KEYS;

    public int games() {
        return games;
    }

    public int wins() { return wins; }
    public int losses() { return losses; }
    public int draws() { return draws; }

    public int keys() {
        return table.size();
    }

    /** 仅供测试：把容量调小，验证淘汰逻辑。 */
    public void setMaxKeys(int max) {
        if (max > 0) {
            maxKeys = max;
        }
    }

    /** 某特征的历史加权分（-80~+80；样本不够返回 0）。 */
    public int bonus(String key) {
        if (key == null) {
            return 0;
        }
        Stats s = table.get(key);
        if (s == null || s.plays < MIN_SAMPLES) {
            return 0;
        }
        return (int) Math.round((s.rate() - 0.5) * 160);
    }

    /** 记一整盘：keys 是电脑每一步的局面特征（按时间顺序），result 是从电脑视角的胜负。 */
    public void recordGame(List<String> keys, int result) {
        games++;
        if (result > 0) {
            wins++;
        } else if (result < 0) {
            losses++;
        } else {
            draws++;
        }
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            Stats s = table.get(key);
            if (s == null) {
                if (table.size() >= maxKeys) {
                    evictOne();
                }
                s = new Stats();
                table.put(key, s);
            }
            s.add(result);
        }
    }

    /** 淘汰下得最少的一个特征（简单策略，避免无限增长）。 */
    private void evictOne() {
        String worst = null;
        int least = Integer.MAX_VALUE;
        for (Map.Entry<String, Stats> e : table.entrySet()) {
            if (e.getValue().plays < least) {
                least = e.getValue().plays;
                worst = e.getKey();
            }
        }
        if (worst != null) {
            table.remove(worst);
        }
    }

    public String describe() {
        return "学习库：" + games + " 局、" + table.size() + " 个特征";
    }

    // ---------- 持久化 ----------

    public void save(File f) {
        if (f == null) {
            return;
        }
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write("games=" + games + "\n");
            w.write("wins=" + wins + "\n");
            w.write("losses=" + losses + "\n");
            w.write("draws=" + draws + "\n");
            for (Map.Entry<String, Stats> e : table.entrySet()) {
                w.write(e.getKey() + "\t" + e.getValue().encode() + "\n");
            }
        } catch (IOException ignored) {
            // 学习库写失败不该影响对局
        }
    }

    private static int parseIntOr0(String s) {
        try {
            return Math.max(0, Integer.parseInt(s.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static JunqiLearn load(File f) {
        JunqiLearn learn = new JunqiLearn();
        if (f == null || !f.isFile()) {
            return learn;
        }
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("games=")) {
                    learn.games = parseIntOr0(line.substring(6));
                    continue;
                }
                if (line.startsWith("wins=")) {
                    learn.wins = parseIntOr0(line.substring(5));
                    continue;
                }
                if (line.startsWith("losses=")) {
                    learn.losses = parseIntOr0(line.substring(7));
                    continue;
                }
                if (line.startsWith("draws=")) {
                    learn.draws = parseIntOr0(line.substring(6));
                    continue;
                }
                int tab = line.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }
                try {
                    Stats s = Stats.decode(line.substring(tab + 1));
                    if (learn.table.size() < learn.maxKeys) {
                        learn.table.put(line.substring(0, tab), s);
                    }
                } catch (RuntimeException ignored) {
                    // 坏行跳过
                }
            }
        } catch (IOException ignored) {
            // 读失败就从空库开始
        }
        return learn;
    }

    // ---------- 特征 ----------

    /**
     * 一步棋的特征：进攻子兵种 + 目标类型（E 空 / K 已知敌子 / U 未知敌子）+ 目标区域 + 方向。
     * 区域按电脑视角：0=自家半场、1=中间、2=敌方半场；方向 F=向前、B=向后、S=横走。
     */
    public static String keyFor(JunqiBoard view, int color, int[] move) {
        if (view == null || move == null || move.length < 2) {
            return null;
        }
        int from;
        int to;
        if (move.length >= 4) {
            from = JunqiBoard.idx(move[0], move[1]);
            to = JunqiBoard.idx(move[2], move[3]);
        } else if (move.length == 2) {
            from = move[0];
            to = move[1];
        } else {
            return null;
        }
        int atk = view.at(JunqiBoard.rowOf(from), JunqiBoard.colOf(from));
        int def = view.at(JunqiBoard.rowOf(to), JunqiBoard.colOf(to));
        String target = def == JunqiBoard.EMPTY ? "E" : (def == JunqiAI.UNKNOWN ? "U" : "K");
        int row = JunqiBoard.rowOf(to);
        int zone;
        if (color == JunqiBoard.RED) {
            zone = row <= 3 ? 2 : (row <= 7 ? 1 : 0);
        } else {
            zone = row >= 8 ? 2 : (row >= 4 ? 1 : 0);
        }
        int dr = JunqiBoard.rowOf(to) - JunqiBoard.rowOf(from);
        String dir = dr == 0 ? "S" : ((color == JunqiBoard.RED ? dr < 0 : dr > 0) ? "F" : "B");
        return "a" + atk + "t" + target + "z" + zone + "d" + dir;
    }

    private JunqiLearn() {
    }
}
