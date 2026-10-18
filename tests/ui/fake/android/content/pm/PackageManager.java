package android.content.pm;
public class PackageManager {
    public static String VERSION_NAME = "1.20261015.86";
    public static int VERSION_CODE = 33;
    public static boolean THROW = false;
    public PackageInfo getPackageInfo(String pkg, int flags) {
        if (THROW) throw new RuntimeException("fake PackageManager 故障");
        PackageInfo pi = new PackageInfo();
        pi.versionName = VERSION_NAME;
        pi.versionCode = VERSION_CODE;
        return pi;
    }
}
