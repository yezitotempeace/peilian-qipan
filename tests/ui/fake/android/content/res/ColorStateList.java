package android.content.res;
public class ColorStateList {
    public final int color;
    private ColorStateList(int c) { color = c; }
    public static ColorStateList valueOf(int c) { return new ColorStateList(c); }
}
