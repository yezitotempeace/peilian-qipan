/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 解析 KataGo 的 kata-genmove_analyze / kata-analyze 输出。
 *
 * 输出形态（一次响应里可能有多个 info 行，每行又串着多个候选点）：
 * <pre>
 * =
 * info move Q4 visits 24 ... winrate 0.4623 scoreLead -0.827 ... pv Q4 D4 D16 info move D16 visits 24 ... pv D16 D4
 * info move Q4 visits 31 ... winrate 0.4559 ... pv Q4 D4
 * play Q4
 * (空行)
 * </pre>
 * 其中 order 0 的候选点代表引擎的首选，其 winrate 就是根节点胜率。
 * 胜率口径由配置里的 reportAnalysisWinratesAs 决定，本项目固定为 SIDETOMOVE，
 * 即「当前行棋方」的胜率。
 */
public final class Analysis {

    public static class MoveInfo {
        public String move = "";
        public int visits;
        public double winrate = Double.NaN;
        public double scoreLead = Double.NaN;
        public int order = Integer.MAX_VALUE;
        public String pv = "";
        /** 这条记录被写入的先后次序，仅用于在 order 并列时取最近上报的那条。 */
        int seq = -1;
    }

    public static class Result {
        public final List<MoveInfo> moves = new ArrayList<>();
        /** move 字符串 -> 在 moves 里的下标，用于去重。 */
        private final Map<String, Integer> moveIndex = new HashMap<>();

        /**
         * 同一个候选点在搜索过程中会被**反复上报** —— kata-genmove_analyze 是流式输出，
         * 每过一个间隔就把所有候选点的最新统计再报一遍。
         *
         * 早先是来一条 add 一条，一次搜索能堆出上千个条目（每个还带完整 PV 字符串），
         * 纯属浪费内存。这里改成：同一个 move 只保留最新的一条。
         * 保留「最新」而不是「第一条」也顺带修正了数据 —— 最新那条的 visits 最大。
         */
        /** 记录写入次序，用来判断哪个候选是「最近一次上报的」。 */
        private int seqCounter;

        void addOrReplace(MoveInfo m) {
            m.seq = seqCounter++;
            Integer at = moveIndex.get(m.move);
            if (at == null) {
                moveIndex.put(m.move, moves.size());
                moves.add(m);
            } else {
                moves.set(at, m);
            }
        }
        /** kata-genmove_analyze 末尾实际落下的那一手（普通 kata-analyze 没有）。 */
        public String playedMove;

        /**
         * 引擎首选。
         *
         * 只比 order 是不够的：一个候选在某一轮上报里是 order 0，之后那轮不再上报它，
         * 它就会带着过期的 order 0 一直留在表里；等新一轮的首选也是 order 0 时，
         * 「先插入的赢」会把那个过期的手返回出去（实测：top() 给 visits=10 的旧手，
         * 而同一批数据里最新一行的首选是 visits=90）。
         * 所以同 order 时先取最近一次上报的（seq 大），再比访问量。
         */
        public MoveInfo top() {
            if (moves.isEmpty()) return null;
            MoveInfo best = moves.get(0);
            for (MoveInfo m : moves) {
                if (m.order < best.order) {
                    best = m;
                } else if (m.order == best.order
                        && (m.seq > best.seq || (m.seq == best.seq && m.visits > best.visits))) {
                    best = m;
                }
            }
            return best;
        }

        /** 根节点胜率（当前行棋方视角）。无法解析时返回 NaN。 */
        public double rootWinrate() {
            MoveInfo t = top();
            return t == null ? Double.NaN : t.winrate;
        }

        public double rootScoreLead() {
            MoveInfo t = top();
            return t == null ? Double.NaN : t.scoreLead;
        }

        /** 按访问量降序的候选点。 */
        public List<MoveInfo> byVisits() {
            List<MoveInfo> copy = new ArrayList<>(moves);
            copy.sort((a, b) -> Integer.compare(b.visits, a.visits));
            return copy;
        }
    }

    private Analysis() {
    }

    public static Result parse(String response) {
        Result r = new Result();
        if (response == null) return r;
        for (String raw : response.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("info")) {
                parseInfoLine(line, r);
            } else {
                // 落子总是排在 info 流之后，所以取最后一个非 info 行。
                // 用「第一个」是错的：任何夹在中间的非 info 行（比如残留的 '='）都会被误当成落子。
                String m = line;
                if (m.toLowerCase(Locale.US).startsWith("play ")) m = m.substring(5).trim();
                r.playedMove = m;
            }
        }
        return r;
    }

    /** 一行里可能串着多个 "info move ..." 片段，逐个切开。 */
    private static void parseInfoLine(String line, Result r) {
        int idx = line.indexOf("info ");
        if (idx < 0) return;
        while (idx >= 0) {
            int next = line.indexOf("info ", idx + 5);
            String chunk = (next < 0) ? line.substring(idx + 5) : line.substring(idx + 5, next);
            MoveInfo m = parseChunk(chunk);
            if (m != null) r.addOrReplace(m);
            idx = next;
        }
    }

    private static MoveInfo parseChunk(String chunk) {
        String[] tok = chunk.trim().split("\\s+");
        if (tok.length < 2 || !tok[0].equals("move")) return null;
        MoveInfo m = new MoveInfo();
        m.move = tok[1];
        for (int i = 2; i < tok.length; i++) {
            String key = tok[i];
            if (i + 1 >= tok.length) break;
            String val = tok[i + 1];
            switch (key) {
                case "visits":
                    m.visits = parseIntSafe(val);
                    i++;
                    break;
                case "winrate":
                    m.winrate = parseDoubleSafe(val);
                    i++;
                    break;
                case "scoreLead":
                    m.scoreLead = parseDoubleSafe(val);
                    i++;
                    break;
                case "order":
                    m.order = parseIntSafe(val);
                    i++;
                    break;
                case "pv": {
                    StringBuilder sb = new StringBuilder();
                    int j = i + 1;
                    while (j < tok.length) {
                        String t = tok[j];
                        if (isKey(t)) break;
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(t);
                        j++;
                    }
                    m.pv = sb.toString();
                    i = j - 1;
                    break;
                }
                default:
                    // 其它键值对跳过（utility/prior/weight/...）
                    i++;
                    break;
            }
        }
        return m.move.isEmpty() ? null : m;
    }

    private static boolean isKey(String t) {
        switch (t) {
            case "move":
            case "visits":
            case "edgeVisits":
            case "utility":
            case "winrate":
            case "scoreMean":
            case "scoreStdev":
            case "scoreLead":
            case "scoreSelfplay":
            case "prior":
            case "lcb":
            case "utilityLcb":
            case "weight":
            case "order":
            case "pv":
                return true;
            default:
                return false;
        }
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDoubleSafe(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** 把引擎返回的落子文本转成内部索引；非法/未知一律按虚手处理。 */
    public static int toPoint(String vertex, int size) {
        if (vertex == null) return Board.PASS;
        String v = vertex.trim();
        if (v.isEmpty() || v.equalsIgnoreCase("pass")) return Board.PASS;
        if (v.equalsIgnoreCase("resign")) return Board.PASS;
        try {
            return Board.gtpToPoint(v, size);
        } catch (IllegalArgumentException e) {
            return Board.PASS;
        }
    }
}
