/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 每个棋页右上角的「规则」入口。
 *
 * <p>点右上角的小按钮弹出一整块规则卡片，把这个棋怎么下、什么吃什么、怎么赢
 * 全部写清楚；点「知道了」或卡片外任意处收起。做成浮层，不挤占棋盘空间。</p>
 *
 * <p>挂在 {@link Watermark#lastHolder}（Watermark.wrap 造出来的整页浮层容器）上，
 * 这样不占用内容布局的高度，也不会被滚动内容顶走。</p>
 */
public final class RulesHelp {

    private RulesHelp() {
    }

    /** 在页面右上角挂一个「规则」按钮 + 可收起的规则卡片。 */
    public static void attach(Activity a, Palette palette, String rules) {
        ViewGroup host = Watermark.lastHolder;
        if (host == null) {
            return;
        }
        float d = a.getResources().getDisplayMetrics().density;
        int m = Math.round(8 * d);

        // ---- 右上角悬浮按钮 ----
        Button btn = new Button(a);
        btn.setText("规则");
        btn.setTextSize(12f);
        btn.setTextColor(palette.buttonText);
        btn.setBackgroundColor(palette.buttonBg);
        btn.setPadding(0, 0, 0, 0);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                Math.round(56 * d), Math.round(40 * d));
        bp.gravity = Gravity.TOP | Gravity.RIGHT;
        bp.setMargins(m, m, m, m);
        host.addView(btn, bp);

        // ---- 规则卡片（默认收起） ----
        final ScrollView card = new ScrollView(a);
        card.setBackgroundColor(palette.background);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(palette.background);
        int pad = Math.round(16 * d);
        box.setPadding(pad, pad, pad, pad);

        TextView head = new TextView(a);
        head.setText("玩法说明");
        head.setTextSize(17f);
        head.setTextColor(palette.text);
        head.setGravity(Gravity.CENTER);
        box.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(a);
        body.setText(rules);
        body.setTextSize(14f);
        body.setTextColor(palette.text);
        body.setPadding(0, pad, 0, pad);
        box.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button ok = new Button(a);
        ok.setText("知道了");
        ok.setTextColor(palette.buttonText);
        ok.setBackgroundColor(palette.buttonBg);
        box.addView(ok, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        cp.setMargins(Math.round(14 * d), Math.round(56 * d),
                Math.round(14 * d), Math.round(14 * d));
        card.setVisibility(View.GONE);
        host.addView(card, cp);

        final boolean[] shown = {false};
        View.OnClickListener toggle = new View.OnClickListener() {
            public void onClick(View v) {
                shown[0] = !shown[0];
                card.setVisibility(shown[0] ? View.VISIBLE : View.GONE);
            }
        };
        btn.setOnClickListener(toggle);
        ok.setOnClickListener(toggle);
        // 点卡片外的暗处也能收起
        card.setOnClickListener(toggle);
        // 吃掉点在卡片内容上的点击，避免误关
        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            }
        });
    }
}
