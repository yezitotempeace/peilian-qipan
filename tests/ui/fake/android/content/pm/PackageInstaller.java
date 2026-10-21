/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.content.pm;
public class PackageInstaller {
    public static final int STATUS_PENDING_USER_ACTION = -1;
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_FAILURE = 1;
    public static final int STATUS_FAILURE_ABORTED = 2;
    public static final int STATUS_FAILURE_BLOCKED = 3;
    public static final int STATUS_FAILURE_CONFLICT = 4;
    public static final int STATUS_FAILURE_INCOMPATIBLE = 5;
    public static final int STATUS_FAILURE_INVALID = 6;
    public static final int STATUS_FAILURE_STORAGE = 7;
    public static class SessionParams {
        public static final int MODE_FULL_INSTALL = 1;
        public SessionParams(int mode) {}
        public void setAppPackageName(String s) {}
    }
    public static class Session {
        public java.io.OutputStream openWrite(String name, long offset, long length) { return new java.io.ByteArrayOutputStream(); }
        public void fsync(java.io.OutputStream o) {}
        public void commit(android.content.IntentSender s) {}
        public void close() {}
    }
    public int createSession(SessionParams p) { return 1; }
    public Session openSession(int id) { return new Session(); }
}
