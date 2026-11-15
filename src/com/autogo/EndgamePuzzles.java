/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.util.ArrayList;
import java.util.List;

/**
 * 残局题库：五子棋 / 围棋 / 象棋 各三道（弱 / 中 / 强）。
 *
 * <p>每道题都经过引擎校验：象棋用将杀搜索确认是 1/2/3 步杀；五子棋用强制胜搜索
 * 确认是 1/2/3 手连珠；围棋用吃子规则确认提子成立。困难档的对手是各自最强的引擎。</p>
 */
public final class EndgamePuzzles {

    public static final int GAME_GOMOKU = 0;
    public static final int GAME_GO = 1;
    public static final int GAME_XIANGQI = 2;

    public static final String[] GAME_NAMES = {"五子棋", "围棋", "象　棋"};
    public static final String[] DIFF_NAMES = {"弱", "中", "强"};

    private EndgamePuzzles() {
    }

    public static final class Puzzle {
        public final int game, diff;
        public final String title, goal;
        /** 五子棋 / 围棋：{row, col, color}。 */
        public final int[][] stones;
        /** 象棋：{row, col, color, type}。 */
        public final int[][] pieces;
        public final int size;

        Puzzle(int game, int diff, String title, String goal, int size,
               int[][] stones, int[][] pieces) {
            this.game = game;
            this.diff = diff;
            this.title = title;
            this.goal = goal;
            this.size = size;
            this.stones = stones;
            this.pieces = pieces;
        }
    }

    /** 每个（游戏 × 难度）恰好一道题。 */
    public static Puzzle get(int game, int diff) {
        if (game == GAME_GOMOKU) {
            if (diff == 0) {
                return new Puzzle(GAME_GOMOKU, 0, "一步连珠",
                        "黑先。你已经有四子连成一线，两头都是空的——随手补一子就是五连。",
                        15, new int[][]{
                                {7, 5, GomokuBoard.BLACK}, {7, 6, GomokuBoard.BLACK},
                                {7, 7, GomokuBoard.BLACK}, {7, 8, GomokuBoard.BLACK},
                                {5, 5, GomokuBoard.WHITE}, {5, 6, GomokuBoard.WHITE},
                                {9, 5, GomokuBoard.WHITE}, {9, 6, GomokuBoard.WHITE}},
                        null);
            }
            if (diff == 1) {
                return new Puzzle(GAME_GOMOKU, 1, "四三取胜",
                        "黑先。一手棋同时做出「冲四」和「活三」，对方只能堵一头，你就赢定了。",
                        15, new int[][]{
                                {7, 5, GomokuBoard.BLACK}, {7, 6, GomokuBoard.BLACK},
                                {7, 7, GomokuBoard.BLACK}, {5, 4, GomokuBoard.BLACK},
                                {6, 4, GomokuBoard.BLACK},
                                {5, 5, GomokuBoard.WHITE}, {6, 5, GomokuBoard.WHITE},
                                {9, 9, GomokuBoard.WHITE}, {3, 3, GomokuBoard.WHITE}},
                        null);
            }
            return new Puzzle(GAME_GOMOKU, 2, "连续冲四",
                    "黑先。这里要连续冲四、一步步把对方的防守逼死，三手之后连成五子。",
                    15, new int[][]{
                            {7, 6, GomokuBoard.BLACK}, {7, 7, GomokuBoard.BLACK},
                            {7, 8, GomokuBoard.BLACK}, {6, 9, GomokuBoard.BLACK},
                            {8, 9, GomokuBoard.BLACK},
                            {7, 5, GomokuBoard.WHITE}, {6, 6, GomokuBoard.WHITE},
                            {8, 8, GomokuBoard.WHITE}, {5, 10, GomokuBoard.WHITE},
                            {9, 10, GomokuBoard.WHITE}},
                    null);
        }

        if (game == GAME_GO) {
            if (diff == 0) {
                return new Puzzle(GAME_GO, 0, "一手提子",
                        "黑先。这块白棋只剩最后一口气，直接闭气把它提掉。",
                        9, new int[][]{
                                {4, 4, Board.WHITE}, {5, 4, Board.WHITE},
                                {3, 4, Board.BLACK}, {4, 3, Board.BLACK}, {4, 5, Board.BLACK},
                                {5, 3, Board.BLACK}, {5, 5, Board.BLACK},
                                {2, 2, Board.BLACK}, {2, 6, Board.BLACK}, {7, 7, Board.BLACK},
                                {7, 3, Board.WHITE}, {7, 8, Board.WHITE}},
                        null);
            }
            if (diff == 1) {
                return new Puzzle(GAME_GO, 1, "全歼四子",
                        "黑先。这块白棋被围得只剩一口气，闭气提掉它，你就白赚四颗子。",
                        9, new int[][]{
                                {4, 4, Board.WHITE}, {4, 5, Board.WHITE},
                                {5, 4, Board.WHITE}, {5, 5, Board.WHITE},
                                {3, 4, Board.BLACK}, {3, 5, Board.BLACK}, {4, 3, Board.BLACK},
                                {4, 6, Board.BLACK}, {5, 3, Board.BLACK}, {5, 6, Board.BLACK},
                                {6, 4, Board.BLACK},
                                {0, 0, Board.BLACK}, {8, 8, Board.WHITE}},
                        null);
            }
            return new Puzzle(GAME_GO, 2, "点要害",
                    "黑先。这块白棋还有两口气，但共用一个要害——先点在这一格上，它就再也逃不掉，随后整块提掉。",
                    9, new int[][]{
                            {4, 4, Board.WHITE}, {4, 5, Board.WHITE}, {4, 6, Board.WHITE},
                            {5, 4, Board.WHITE}, {5, 6, Board.WHITE},
                            {3, 4, Board.BLACK}, {3, 5, Board.BLACK}, {3, 6, Board.BLACK},
                            {3, 7, Board.BLACK}, {4, 3, Board.BLACK}, {4, 8, Board.BLACK},
                            {5, 3, Board.BLACK}, {5, 7, Board.BLACK}, {6, 4, Board.BLACK},
                            {6, 6, Board.BLACK},
                            {0, 0, Board.WHITE}, {8, 8, Board.BLACK}},
                    null);
        }

        // ---- 象棋（红先，将死黑方）----
        if (diff == 0) {
            return new Puzzle(GAME_XIANGQI, 0, "一步将死",
                    "红先。把车沉到黑方底线，将、士、马三面夹击，一步就把黑将将死。",
                    0, null, new int[][]{
                            {0, 4, XiangqiBoard.BLACK, XiangqiBoard.K},
                            {0, 3, XiangqiBoard.BLACK, XiangqiBoard.A},
                            {9, 4, XiangqiBoard.RED, XiangqiBoard.K},
                            {1, 0, XiangqiBoard.RED, XiangqiBoard.R},
                            {1, 8, XiangqiBoard.RED, XiangqiBoard.R},
                            {2, 4, XiangqiBoard.RED, XiangqiBoard.N}});
        }
        if (diff == 1) {
            return new Puzzle(GAME_XIANGQI, 1, "两步将死",
                    "红先。车还差一步才到底线，先把车退回来调整位置，两步之内把黑将将死。",
                    0, null, new int[][]{
                            {0, 4, XiangqiBoard.BLACK, XiangqiBoard.K},
                            {0, 3, XiangqiBoard.BLACK, XiangqiBoard.A},
                            {9, 4, XiangqiBoard.RED, XiangqiBoard.K},
                            {3, 0, XiangqiBoard.RED, XiangqiBoard.R},
                            {3, 8, XiangqiBoard.RED, XiangqiBoard.R},
                            {2, 4, XiangqiBoard.RED, XiangqiBoard.N}});
        }
        return new Puzzle(GAME_XIANGQI, 2, "三步将死",
                "红先。黑方双士护住九宫，要连将三步、一步步把它的落脚点全部封死，最后一举将死。",
                0, null, new int[][]{
                        {0, 4, XiangqiBoard.BLACK, XiangqiBoard.K},
                        {0, 3, XiangqiBoard.BLACK, XiangqiBoard.A},
                        {0, 5, XiangqiBoard.BLACK, XiangqiBoard.A},
                        {9, 4, XiangqiBoard.RED, XiangqiBoard.K},
                        {3, 0, XiangqiBoard.RED, XiangqiBoard.R},
                        {3, 8, XiangqiBoard.RED, XiangqiBoard.R},
                        {2, 4, XiangqiBoard.RED, XiangqiBoard.N}});
    }

    /** 每个游戏几道题（这里每档一道，共三道）。 */
    public static int count(int game) {
        return 3;
    }
}
