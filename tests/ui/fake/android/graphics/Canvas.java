package android.graphics;
public class Canvas {
    /** 桩测用：记录所有 drawRoundRect 的矩形（全屏返回按钮就画在这）。 */
    public static final java.util.List<RectF> RECTS = new java.util.ArrayList<RectF>();
    public static int translateCount = 0;
    public Canvas() {}
    public void drawRect(RectF r, Paint p) {}
    public void drawRect(float l, float t, float rr, float b, Paint p) {}
    public void drawRoundRect(RectF r, float rx, float ry, Paint p) { RECTS.add(new RectF(r.left, r.top, r.right, r.bottom)); }
    public void drawLine(float x0, float y0, float x1, float y1, Paint p) { LINES.add(new float[]{x0, y0, x1, y1, p.getColor()}); }
    public void drawCircle(float x, float y, float r, Paint p) { CIRCLES.add(new float[]{x, y, r, p.getColor()}); }
    public void drawText(String s, float x, float y, Paint p) { TEXTS.add(s); }
    public static final java.util.List<float[]> CIRCLES = new java.util.ArrayList<float[]>();
    public static final java.util.List<float[]> LINES = new java.util.ArrayList<float[]>();
    public static final java.util.List<String> TEXTS = new java.util.ArrayList<String>();
    public static void reset() { RECTS.clear(); PATHS.clear(); CIRCLES.clear(); LINES.clear(); TEXTS.clear(); translateCount = 0; }
    public static final java.util.List<Path> PATHS = new java.util.ArrayList<Path>();
    public void drawPath(Path path, Paint p) { PATHS.add(path); }
    public void save() {}
    public void restore() {}
    public void translate(float dx, float dy) { translateCount++; }
}
