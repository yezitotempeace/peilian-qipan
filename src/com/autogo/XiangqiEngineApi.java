/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

/**
 * 象棋对手的统一接口：内置 AI（{@link XiangqiAI}）和外部强引擎（{@link XiangqiEngine}）
 * 都实现它，界面侧只认这个接口。
 */
public interface XiangqiEngineApi {

    /** 引擎名（界面显示用）。 */
    String name();

    /** 设难度：0 = 快，1 = 普通（默认），2 = 强。越界按普通处理。 */
    void setLevel(int level);

    int level();

    /**
     * 该走哪一手，返回 {@code {fromRow, fromCol, toRow, toCol}}；
     * 无着可走或已终局返回 null。约定：只读棋局，不改传入的 board。
     */
    int[] genmove(XiangqiBoard board);
}
