package android.graphics;
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Align { LEFT, CENTER, RIGHT }
    private int color = Color.BLACK;
    private float textSize = 12f, strokeWidth = 1f;
    private Style style = Style.FILL;
    private Align align = Align.LEFT;
    public Paint() {}
    public Paint(int flags) {}
    public void setColor(int c) { color = c; }
    public int getColor() { return color; }
    public void setShader(Shader s) {}
    public void setStrokeWidth(float w) { strokeWidth = w; }
    public float getStrokeWidth() { return strokeWidth; }
    public void setStyle(Style s) { style = s; }
    public Style getStyle() { return style; }
    public void setTextAlign(Align a) { align = a; }
    public Align getTextAlign() { return align; }
    public void setTextSize(float s) { textSize = s; }
    public float getTextSize() { return textSize; }
    public float measureText(String s) { return s == null ? 0f : s.length() * textSize * 0.6f; }
    public void setAntiAlias(boolean b) {}
}
