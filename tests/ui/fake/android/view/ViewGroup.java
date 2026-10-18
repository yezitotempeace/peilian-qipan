package android.view;
public class ViewGroup extends View {
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public int width, height, gravity;
        public int topMargin, bottomMargin, leftMargin, rightMargin;
        public float weight;
        public LayoutParams(int w, int h) { width = w; height = h; }
        public void setMargins(int l, int t, int r, int b) { leftMargin = l; topMargin = t; rightMargin = r; bottomMargin = b; }
    }
    public final java.util.List<View> children = new java.util.ArrayList<View>();
    public void addView(View v, LayoutParams p) {
        children.add(v);
        if (p != null) v.setLayoutParams(p);   // 真 ViewGroup 会把 LayoutParams 装到子视图上
        android.os.FakeLog.log("addView:" + describe(v));
    }
    public int getChildCount() { return children.size(); }
    public View getChildAt(int i) { return children.get(i); }
    public void addView(View v) { addView(v, null); }
    private static String describe(View v) {
        if (v instanceof android.widget.TextView) return "\"" + ((android.widget.TextView) v).getText() + "\"";
        return v.getClass().getSimpleName();
    }
}
