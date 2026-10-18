package android.app;
public class AlertDialog {
    public static class Builder {
        public Builder(android.content.Context c) {}
        public Builder setTitle(CharSequence t) { android.os.FakeLog.log("dialog.title:" + t); return this; }
        public Builder setMessage(CharSequence m) { android.os.FakeLog.log("dialog.msg:" + m); return this; }
        public Builder setPositiveButton(CharSequence t, android.content.DialogInterface.OnClickListener l) { android.os.FakeLog.log("dialog.pos:" + t); return this; }
        public Builder setNeutralButton(CharSequence t, android.content.DialogInterface.OnClickListener l) { android.os.FakeLog.log("dialog.neutral:" + t); return this; }
        public Builder setNegativeButton(CharSequence t, android.content.DialogInterface.OnClickListener l) { android.os.FakeLog.log("dialog.neg:" + t); return this; }
        public Builder setView(android.view.View v) { android.os.FakeLog.log("dialog.view:" + (v == null ? "null" : v.getClass().getSimpleName())); return this; }
        public Builder setCancelable(boolean b) { return this; }
        public AlertDialog show() { android.os.FakeLog.log("dialog.show"); return new AlertDialog(); }
    }
}
