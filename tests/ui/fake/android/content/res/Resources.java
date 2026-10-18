package android.content.res;
public class Resources {
    private Configuration cfg;
    private android.util.DisplayMetrics dm;
    public Resources(Configuration c, android.util.DisplayMetrics d) { cfg = c; dm = d; }
    public Configuration getConfiguration() { return cfg; }
    public android.util.DisplayMetrics getDisplayMetrics() { return dm; }
}
