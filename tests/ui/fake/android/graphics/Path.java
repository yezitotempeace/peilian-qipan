package android.graphics;
public class Path {
    public int ops = 0;
    public void reset() { ops = 0; }
    public void rewind() { ops = 0; }
    public void moveTo(float x, float y) { ops++; }
    public void lineTo(float x, float y) { ops++; }
    public void quadTo(float x1, float y1, float x2, float y2) { ops++; }
    public void cubicTo(float a, float b, float c, float d, float e, float f) { ops++; }
    public void close() { ops++; }
    public boolean isEmpty() { return ops == 0; }
}
