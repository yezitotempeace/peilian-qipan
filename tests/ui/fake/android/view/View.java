package android.view;
public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;
    public interface OnClickListener { void onClick(View v); }
    public static int performClicks = 0;
    private ViewGroup.LayoutParams lp;
    private android.content.Context context;
    private int width, height;
    public View() {}
    public View(android.content.Context c) { context = c; }
    public View(android.content.Context c, android.util.AttributeSet a) { context = c; }
    public void setBackgroundColor(int c) { bgColor = c; }
    public void setPadding(int l, int t, int r, int b) {}
    public OnClickListener clickListener;
    public void setOnClickListener(OnClickListener l) { clickListener = l; android.os.FakeLog.log("setOnClickListener"); }
    /** 桩测专用：直接触发点击回调（真机上由触摸/无障碍动作触发）。 */
    public void fakeClick() { if (clickListener != null) clickListener.onClick(this); }
    public int bgColor = 0;
    public void setBackgroundTintList(android.content.res.ColorStateList c) {}
    public void setLayoutParams(ViewGroup.LayoutParams p) { lp = p; }
    public ViewGroup.LayoutParams getLayoutParams() { return lp; }
    public void setVisibility(int v) { android.os.FakeLog.log("setVisibility:" + v); }
    public void setMinimumWidth(int w) {}
    public android.view.ViewPropertyAnimator animate() { return new android.view.ViewPropertyAnimator(); }

    public void postInvalidateOnAnimation() {}

    public void invalidate() {}
    public void postInvalidate() {}
    /** 桩测专用：立即执行（真机上会排到下一帧，测里同步跑更好断言）。 */
    public void post(Runnable r) { if (r != null) r.run(); }

    /** 布局变化回调（真机上有；第 107 轮审计要求用它做「布局完成后再算尺寸」）。 */
    public interface OnLayoutChangeListener {
        void onLayoutChange(View v, int left, int top, int right, int bottom,
                            int oldLeft, int oldTop, int oldRight, int oldBottom);
    }

    private final java.util.List<OnLayoutChangeListener> layoutListeners =
            new java.util.ArrayList<OnLayoutChangeListener>();

    public void addOnLayoutChangeListener(OnLayoutChangeListener l) {
        if (l != null) layoutListeners.add(l);
    }

    public void removeOnLayoutChangeListener(OnLayoutChangeListener l) {
        layoutListeners.remove(l);
    }

    /** 桩测专用：模拟 ViewRootImpl 完成一次 layout（真机上 layout() 之后才回调监听）。 */
    public void fakeLayout(int w, int h) {
        int ow = width, oh = height;
        width = w;
        height = h;
        for (OnLayoutChangeListener l : new java.util.ArrayList<OnLayoutChangeListener>(layoutListeners)) {
            l.onLayoutChange(this, 0, 0, w, h, 0, 0, ow, oh);
        }
    }
    public void setFocusable(boolean f) {}
    public void setWillNotDraw(boolean b) {}
    public void requestLayout() {}
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    /** 桩测专用：直接设视图尺寸（真机上由布局给出）。 */
    public void fakeSize(int w, int h) { width = w; height = h; }
    public android.content.Context getContext() { return context; }
    public android.content.res.Resources getResources() {
        if (context != null) return context.getResources();
        android.content.res.Resources r =
                new android.content.res.Resources(new android.content.res.Configuration(),
                        new android.util.DisplayMetrics());
        r.getDisplayMetrics().density = 2f;
        return r;
    }
    public boolean performClick() { performClicks++; return true; }
    public static class MeasureSpec {
        public static final int UNSPECIFIED = 0, EXACTLY = 1, AT_MOST = 2;
        public static int getMode(int s) { return (s & 0xC0000000) >>> 30; }
        public static int getSize(int s) { return s & 0x3FFFFFFF; }
        public static int makeMeasureSpec(int size, int mode) { return (size & 0x3FFFFFFF) | (mode << 30); }
    }
    protected static int resolveSize(int size, int measureSpec) { return size; }
    protected int getSuggestedMinimumWidth() { return 0; }
    protected int getSuggestedMinimumHeight() { return 0; }
    protected void onMeasure(int w, int h) { setMeasuredDimension(getWidth(), getHeight()); }
    public void fakeOnMeasure(int w, int h) { onMeasure(w, h); }
    public int measuredW = -1, measuredH = -1;
    protected void setMeasuredDimension(int w, int h) { measuredW = w; measuredH = h; }
    protected void onDraw(android.graphics.Canvas c) {}
    /** 桩测专用：把 protected 回调暴露出来（基类自己调用，动态分派到子类覆盖）。 */
    public void fakeOnDraw(android.graphics.Canvas c) { onDraw(c); }
    public void fakeOnSizeChanged(int w, int h, int ow, int oh) { onSizeChanged(w, h, ow, oh); }
    protected void onSizeChanged(int w, int h, int ow, int oh) {}
    public boolean onTouchEvent(android.view.MotionEvent e) { return false; }
    public boolean onInterceptTouchEvent(android.view.MotionEvent e) { return false; }
    public float alpha = 1.0f;

    public void setAlpha(float a) {
        this.alpha = a;
    }
    public void setScaleX(float s) {}
    public void setScaleY(float s) {}

}
