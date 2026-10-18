package android.view;
public class MotionEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2, ACTION_CANCEL = 3;
    private final int action;
    private final float x, y;
    private MotionEvent(int a, float x, float y) { this.action = a; this.x = x; this.y = y; }
    public static MotionEvent down(float x, float y) { return new MotionEvent(ACTION_DOWN, x, y); }
    public static MotionEvent up(float x, float y) { return new MotionEvent(ACTION_UP, x, y); }
    public static MotionEvent move(float x, float y) { return new MotionEvent(ACTION_MOVE, x, y); }
    public int getAction() { return action; }
    public float getX() { return x; }
    public float getY() { return y; }
}
