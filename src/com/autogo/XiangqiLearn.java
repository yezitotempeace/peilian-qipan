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
 * 象棋的「学习库」：所有难度档共用。每盘棋结束后，把电脑走子的**局面特征**和胜负记下来；
 * 下次遇到同类特征时，历史胜率高的走法会被加权（快/普通/强都吃这一层经验）。
 *
 * <p>为什么不用精确局面哈希：象棋开局体系很多，精确局面跨局几乎不重复；
 * 所以按「走的兵种 + 起点区域 + 终点区域 + 是否吃子 + 是否过河」做特征，才能跨局复用。</p>
 *
 * <p>纯 Java、不依赖 Android，可单测；文件格式一行一个特征：key\tplays:wins:losses:draws。</p>
 */
public final class XiangqiLearn {

    /** 至少下过这么多次，才敢按经验加权。 */
    public static final int MIN_SAMPLES = 3;
    /** 历史胜率高于这个才给正加权（低于则负加权）。 */
    public static final double GOOD_RATE = 0.60;
    /** 最多记多少个特征。 */
    public static final int MAX_KEYS = 4000;
    /** 默认文件名。 */
    public static final String FILE_NAME = "xiangqi-learn.dat";

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
            if (plays == 0) {
                return 0.5;
            }
            return (wins + draws * 0.5) / plays;
        }
    }

    private final Map<String, Stats> table = new LinkedHashMap<String, Stats>();
    private int games, wins, losses, draws;
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

    /** 学习库文件体积上限（2 GiB）。超过就按「下得最少的先丢」裁到上限以内再写盘。 */
    public static final long MAX_BYTES = 2L * 1024 * 1024 * 1024L;

    /** 保存前的字符预算：UTF-8 下单字符最多 3 字节，按最坏情况折算。 */
    private long maxChars = MAX_BYTES / 3;

    /** 测试用：直接设字符预算。 */
    public void setMaxChars(long chars) {
        if (chars > 0) {
            maxChars = chars;
        }
    }

    /** 把当前内容拼成一整块（保存与体积裁剪共用）。 */
    String snapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append(BuildInfo.header()).append('\n');
        sb.append("# games ").append(games).append(' ').append(wins).append(' ')
                .append(losses).append(' ').append(draws).append('\n');
        for (Map.Entry<String, Stats> e : table.entrySet()) {
            Stats s = e.getValue();
            sb.append(e.getKey()).append('\t').append(s.plays).append(':').append(s.wins)
                    .append(':').append(s.losses).append(':').append(s.draws).append('\n');
        }
        return sb.toString();
    }

    public void save(File f) {
        if (f == null) {
            return;
        }
        String content = snapshot();
        int guard = 0;
        while (content.length() > maxChars && !table.isEmpty() && guard++ < 1000) {
            evictBatch();
            content = snapshot();
        }
        OutputStreamWriter w = null;
        try {
            w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8);
            w.write(content);
        } catch (IOException ignored) {
            // 学习库存不下来不影响下棋
        } finally {
            if (w != null) {
                try { w.close(); } catch (IOException ignored) { }
            }
        }
    }

    /** 一次淘汰约 10%（至少 1 条）下得最少的特征。 */
    private void evictBatch() {
        int n = Math.max(1, table.size() / 10);
        for (int i = 0; i < n && !table.isEmpty(); i++) {
            evictOne();
        }
    }

    /** 头部戳是否可接受（老存档无戳也放行，避免升级时丢掉用户的学习记录）。 */
    static boolean headerAcceptable(BufferedReader r) {
        try {
            r.mark(4096);
            String first = r.readLine();
            r.reset();
            if (first == null) {
                return true;
            }
            String t = first.trim();
            // 只有形如 "# XXX/YYY/..." 的才是我们的戳；其它（老格式 "# games ..."）一律放行
            if (t.startsWith("# ") && t.indexOf('/') > 0) {
                return BuildInfo.Check.stamp(t);
            }
            return true;
        } catch (IOException e) {
            return true;   // 读不动就当老存档处理，不让学习库把 App 拦下来
        }
    }

    public static XiangqiLearn load(File f) {
        XiangqiLearn l = new XiangqiLearn();
        if (f == null || !f.isFile()) {
            return l;
        }
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
            // 头部戳校验：有戳但对不上 ⇒ 不是本程序写的，拒绝加载；没有戳的是老存档，照常读
            if (!headerAcceptable(r)) {
                return l;
            }
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#")) {
                    String[] p = line.split("\\s+");
                    if (p.length >= 5 && "games".equals(p[1])) {
                        try {
                            l.games = Integer.parseInt(p[2]);
                            l.wins = Integer.parseInt(p[3]);
                            l.losses = Integer.parseInt(p[4]);
                            l.draws = Integer.parseInt(p[5]);
                        } catch (RuntimeException ignored) {
                        }
                    }
                    continue;
                }
                int tab = line.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }
                String key = line.substring(0, tab);
                String[] nums = line.substring(tab + 1).split(":");
                if (nums.length != 4) {
                    continue;
                }
                try {
                    Stats s = new Stats();
                    s.plays = Integer.parseInt(nums[0]);
                    s.wins = Integer.parseInt(nums[1]);
                    s.losses = Integer.parseInt(nums[2]);
                    s.draws = Integer.parseInt(nums[3]);
                    l.table.put(key, s);
                } catch (RuntimeException ignored) {
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (r != null) {
                try { r.close(); } catch (IOException ignored) { }
            }
        }
        return l;
    }

    // ---------- 特征 ----------

    /**
     * 一步棋的局面特征：走的兵种 + 起点区域 + 终点区域 + 吃子情况 + 是否过河。
     * 区域按「己方底线/己方腹地/中路/对方腹地/对方底线」分五段，跨局可复用。
     */
    public static String keyFor(XiangqiBoard view, int color, int[] move) {
        if (view == null || move == null || move.length < 4) {
            return null;
        }
        int fr = move[0], fc = move[1], tr = move[2], tc = move[3];
        if (fr < 0 || fr >= XiangqiBoard.ROWS || tr < 0 || tr >= XiangqiBoard.ROWS
                || fc < 0 || fc >= XiangqiBoard.COLS || tc < 0 || tc >= XiangqiBoard.COLS) {
            return null;
        }
        int piece = view.at(fr, fc);
        int type = XiangqiBoard.typeOf(piece);
        int def = view.at(tr, tc);
        String cap = def == 0 ? "0" : "1";
        String half0 = zoneOf(color, fr);
        String half1 = zoneOf(color, tr);
        boolean crossed = crossedRiver(color, fr, tr);
        return "p" + type + "a" + half0 + "b" + half1 + "c" + cap + "x" + (crossed ? "1" : "0");
    }

    /** 己方视角的区域：0=底线两行、1=己方腹地、2=中路（河界附近）、3=对方腹地、4=对方底线。 */
    public static String zoneOf(int color, int row) {
        // 红方在下（row 9 底线），黑方在上（row 0 底线）
        int d = color == XiangqiBoard.RED ? (XiangqiBoard.ROWS - 1 - row) : row;
        if (d <= 1) return "0";
        if (d <= 3) return "1";
        if (d <= 5) return "2";
        if (d <= 7) return "3";
        return "4";
    }

    /** 这一步是否过了河（红方向上前进、黑方向下前进）。 */
    public static boolean crossedRiver(int color, int fromRow, int toRow) {
        if (color == XiangqiBoard.RED) {
            return fromRow > 4 && toRow <= 4;
        }
        return fromRow < 5 && toRow >= 5;
    }

    private XiangqiLearn() {
    }
}
