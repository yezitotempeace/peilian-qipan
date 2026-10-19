package android.app;
public class AlarmManager {
    public static final int ELAPSED_REALTIME = 3;
    public static final int ELAPSED_REALTIME_WAKEUP = 2;
    public static int calls;
    public void set(int type, long trigger, PendingIntent pi) { calls++; }
    public void setInexactRepeating(int type, long trigger, long interval, PendingIntent pi) { calls++; }
}
