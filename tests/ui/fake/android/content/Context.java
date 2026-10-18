package android.content;
public class Context {
    public static final String CLIPBOARD_SERVICE = "clipboard";

    /** 学习库要写文件：给假 Context 一个临时目录（每个进程一个）。 */
    private static java.io.File filesDir;
    public java.io.File getFilesDir() {
        if (filesDir == null) {
            filesDir = new java.io.File(System.getProperty("java.io.tmpdir"), "fake-files-" + ProcessHandle.current().pid());
        }
        if (!filesDir.isDirectory()) filesDir.mkdirs();
        return filesDir;
    }
    public static final int MODE_PRIVATE = 0;
    public android.content.res.Resources resources =
            new android.content.res.Resources(new android.content.res.Configuration(), new android.util.DisplayMetrics());
    public SharedPreferences prefs = new SharedPreferences();
    public android.content.res.Resources getResources() { return resources; }
    public SharedPreferences getSharedPreferences(String name, int mode) { return prefs; }
    public String getPackageName() { return "com.autogo"; }
    public android.content.pm.PackageManager getPackageManager() { return new android.content.pm.PackageManager(); }
    public android.content.pm.ApplicationInfo applicationInfo = new android.content.pm.ApplicationInfo();
    public android.content.pm.ApplicationInfo getApplicationInfo() { return applicationInfo; }

    /** 假系统服务：目前只需要剪贴板。 */
    public Object getSystemService(String name) {
        if (CLIPBOARD_SERVICE.equals(name)) {
            return new ClipboardManager();
        }
        return null;
    }
}
