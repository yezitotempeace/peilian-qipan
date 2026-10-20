package com.autogo;

/**
 * 更新公告：标题 + 分条要点。纯 Java（无 Android 依赖），所以能在 JVM 套件里单测。
 * 菜单页右上角的「公告」按钮用它填弹窗内容。
 */
public final class Changelog {

    /** 一条公告。 */
    public static final class Entry {
        public final String version;
        public final String[] lines;

        Entry(String version, String... lines) {
            this.version = version;
            this.lines = lines == null ? new String[0] : lines;
        }
    }

    /** 最新的排在前面。 */
    private static final Entry[] ENTRIES = {
        new Entry("v187",
                "军棋玩法修正为正规规则：暗棋只藏对方的子（己方始终可见，以前连自己的子都看不见）；吃子翻牌动画不再留残影（翻完缩小消失，不再和胜方棋子叠在一起）；行营开局留空（安全区不能一上来就摆子）。"),
        new Entry("v186",
                "军棋动画补全：落子滑动带缩放、吃子落点有红色爆点；吃子/被吃/同归于尽时，阵亡的棋子真的「背面→正面」翻过来显露兵种再消失（不再只是文字提示）。"),
    };

    public static Entry[] entries() {
        return ENTRIES.clone();
    }

    public static Entry latest() {
        return ENTRIES.length == 0 ? null : ENTRIES[0];
    }

    /** 弹窗标题，例如「更新了什么（v126）」。 */
    public static String title() {
        Entry e = latest();
        return e == null ? "更新了什么" : "更新了什么（" + e.version + "）";
    }

    /** 弹窗正文：每条公告一段。 */
    public static String text() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : ENTRIES) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.version).append('\n');
            for (String line : e.lines) sb.append("· ").append(line).append('\n');
        }
        return sb.toString().trim();
    }

    private Changelog() {
    }
}
