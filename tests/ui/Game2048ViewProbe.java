package com.autogo;

import android.graphics.Canvas;
import android.view.MotionEvent;

/** 2048 棋盘绘制与滑动手势的桩测（假 Canvas + 真 Game2048View）。 */
public class Game2048ViewProbe {

    static int pass, fail;
    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) { pass++; }
        else { fail++; System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got); }
    }

    static void draw(Game2048View v) {
        Canvas.reset();
        v.fakeOnDraw(new Canvas());
    }

    public static void main(String[] args) {
        Game2048 g = new Game2048(1);
        g.load(new int[]{2,4,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0}, 0);
        Game2048View v = new Game2048View(new android.content.Context());
        v.fakeSize(600, 600);
        v.setAnimationsEnabled(false);
        v.setGame(g);
        draw(v);
        chk("棋盘底 + 16 个底格 + 2 个数字块 = 19 个圆角矩形", 19, Canvas.RECTS.size());
        chk("2 个数字文字", 2, Canvas.TEXTS.size());
        chk("文字是 2", true, Canvas.TEXTS.contains("2"));
        chk("文字是 4", true, Canvas.TEXTS.contains("4"));

        g.load(new int[]{2,4,8,16, 32,64,128,256, 512,1024,2048,4096, 8192,0,0,0}, 0);
        draw(v);
        chk("棋盘底 + 13 个数字块 + 16 底格 = 30", 30, Canvas.RECTS.size());
        chk("13 个数字文字", 13, Canvas.TEXTS.size());
        chk("有 2048 文字", true, Canvas.TEXTS.contains("2048"));

        final int[] got = new int[]{-1};
        v.setOnSwipe(new Game2048View.OnSwipe() {
            public void onSwipe(int dir) { got[0] = dir; }
        });
        v.onTouchEvent(MotionEvent.down(300, 400));
        v.onTouchEvent(MotionEvent.up(300, 100));
        chk("上滑 = UP", Game2048.UP, got[0]);
        got[0] = -1;
        v.onTouchEvent(MotionEvent.down(300, 100));
        v.onTouchEvent(MotionEvent.up(300, 400));
        chk("下滑 = DOWN", Game2048.DOWN, got[0]);
        got[0] = -1;
        v.onTouchEvent(MotionEvent.down(100, 300));
        v.onTouchEvent(MotionEvent.up(500, 300));
        chk("右滑 = RIGHT", Game2048.RIGHT, got[0]);
        got[0] = -1;
        v.onTouchEvent(MotionEvent.down(500, 300));
        v.onTouchEvent(MotionEvent.up(100, 300));
        chk("左滑 = LEFT", Game2048.LEFT, got[0]);
        got[0] = -1;
        v.onTouchEvent(MotionEvent.down(300, 300));
        v.onTouchEvent(MotionEvent.up(305, 302));
        chk("轻点不动不触发滑动", -1, got[0]);

        System.out.println("2048 棋盘探针：PASS " + pass + " / FAIL " + fail);
        if (fail > 0) System.exit(1);
    }
}
