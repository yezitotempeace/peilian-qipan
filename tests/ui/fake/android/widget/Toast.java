package android.widget;
public class Toast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;
    public static Toast makeText(android.content.Context c, CharSequence s, int d) { android.os.FakeLog.log("toast:" + s); return new Toast(); }
    public void show() {}
}
