package com.autogo;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 右下角作者水印（「穗安」）。
 *
 * 关键：水印是**浮层**，不占布局高度 ---- 早先把它当成内容区里的一行加进竖排容器，
 * 会把带 weight 的控件区高度吃掉（围棋页按钮整块塌成 0、五子棋页棋盘算高的可用空间变小）。
 * 现在把整页内容包进一个 FrameLayout，水印以 BOTTOM|RIGHT 贴在其上，内容布局一点不变。
 */
public final class Watermark {

    /** 水印文字。 */
    public static final String TEXT = "穗安";

    /** 最近一次 wrap 的浮层（给桩测读水印用；正式运行里就一个页面用得上）。 */
    public static ViewGroup lastHolder;

    /** 造一个水印文本视图（不挂到任何容器上）。 */
    public static TextView make(Context ctx, Palette palette) {
        TextView tv = new TextView(ctx);
        tv.setText(TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        tv.setTextColor(palette.textDim);
        tv.setAlpha(0.6f);
        return tv;
    }

    /**
     * 把整页内容包进浮层并设为内容视图：内容仍然铺满整屏，水印贴在右下角。
     * 必须在原 setContentView 的位置调用（替换掉那一次调用）。
     */
    public static void wrap(Activity a, View content, Palette palette, int padDp) {
        FrameLayout holder = new FrameLayout(a);
        holder.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        float density = a.getResources().getDisplayMetrics().density;
        int pad = Math.round(padDp * density);
        lp.setMargins(pad, pad, pad, pad);
        holder.addView(make(a, palette), lp);
        lastHolder = holder;
        a.setContentView(holder);
    }

    private Watermark() {
    }
}
