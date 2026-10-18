package android.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;

/** 桩测用的假 FrameLayout：只记子视图，够验证「内容 + 水印」浮层结构。 */
public class FrameLayout extends ViewGroup {

    public FrameLayout(Context c) {
    }

    /** 假布局参数：宽高 + gravity（+ 四边距，够水印用）。 */
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public int gravity = Gravity.TOP | Gravity.LEFT;
        public int leftMargin;
        public int topMargin;
        public int rightMargin;
        public int bottomMargin;

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public void setMargins(int l, int t, int r, int b) {
            leftMargin = l;
            topMargin = t;
            rightMargin = r;
            bottomMargin = b;
        }
    }
}
