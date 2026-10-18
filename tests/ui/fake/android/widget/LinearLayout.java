package android.widget;
public class LinearLayout extends android.view.ViewGroup {
    public static final int VERTICAL = 1;
    public static final int HORIZONTAL = 0;
    public static class LayoutParams extends android.view.ViewGroup.LayoutParams {
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int w, int h, float weight) { super(w, h); this.weight = weight; }
    }
    public LinearLayout(android.content.Context c) {}
    public int orientation = -1;
    public int gravity = -1;
    public void setOrientation(int o) { orientation = o; }
    public void setGravity(int g) { gravity = g; }
}
