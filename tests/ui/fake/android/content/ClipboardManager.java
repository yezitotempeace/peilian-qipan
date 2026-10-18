package android.content;

/** 桩测用的假剪贴板：记最后一次复制的内容。 */
public class ClipboardManager {
    public static String lastText;
    public ClipData clip;

    public void setPrimaryClip(ClipData c) {
        clip = c;
        lastText = c == null ? null : c.text;
        android.os.FakeLog.log("clipboard:" + lastText);
    }
}
