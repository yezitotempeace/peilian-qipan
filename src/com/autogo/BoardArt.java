package com.autogo;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * 棋具的立体感画法：棋子阴影 + 棋子高光 + 棋盘立体边。
 * 围棋、五子棋共用这一套；以后新加的棋种（象棋/军棋…）也调这里，保证观感一致。
 */
public final class BoardArt {

    /** 单独一支画笔，避免打乱调用方画笔的 shader/style。 */
    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 棋子投影：贴右下的一团淡黑，画在棋身之前。 */
    public static void stoneShadow(Canvas c, float r) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(0x38000000);
        c.drawCircle(r * 0.10f, r * 0.17f, r * 1.00f, P);
    }

    /** 棋子高光：左上小亮点，画在棋身之上。 */
    public static void stoneGloss(Canvas c, float r) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(0x5CFFFFFF);
        c.drawCircle(-r * 0.32f, -r * 0.36f, r * 0.33f, P);
    }

    /** 棋盘立体边：下/右一道暗影、上/左一道高光。 */
    public static void boardEdge(Canvas c, float left, float top, float right, float bottom) {
        float w = Math.max(1.5f, (right - left) * 0.0045f);
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w);
        P.setColor(0x40000000);
        c.drawLine(left, bottom, right, bottom, P);
        c.drawLine(right, top, right, bottom, P);
        P.setColor(0x50FFFFFF);
        c.drawLine(left, top, right, top, P);
        c.drawLine(left, top, left, bottom, P);
    }

    private BoardArt() {
    }
}
