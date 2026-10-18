package com.autogo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 启动选择页：从这里选「围棋」或「五子棋」。
 *
 * 这一页只负责分流，不碰任何一方的对局逻辑：
 *   - 围棋 → {@link MainActivity}（原有对局界面，一行没改）
 *   - 五子棋 → {@link GomokuActivity}
 *
 * 主题走 {@link UiTheme}（与 MainActivity 同一份偏好 "autogo" / "themeMode"、同一套判据），
 * 所以设置页换的主题这一页也跟得上：清单声明了 configChanges，本页不会自己重建，
 * 于是 onConfigurationChanged（转屏/字号/深浅色）与 onResume（从设置页返回）各补一次重判，
 * 并每分钟查一次「按时间自动」过没过点。
 */
public class MenuActivity extends Activity {

    private Palette palette;
    private Screen screen;
    /** 这一页当前是不是深色（用来判断要不要重铺）。 */
    private boolean dark;
    /** 每分钟查一次「是不是过了自动模式的切换点」。 */
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable themeTick = new Runnable() {
        @Override
        public void run() {
            maybeAutoSwitchTheme();
            ui.postDelayed(this, 60_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 先定窗口主题再建视图：深色下启动不会先闪一下白底。
        dark = UiTheme.apply(this);
        palette = Palette.of(dark);
        screen = Screen.read(this);
        buildUi();
    }

    /** 系统深浅色变了：清单里声明了 uiMode，本页不会被重建，得自己重判、重铺。 */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshTheme(newConfig);
    }

    /**
     * 从设置页（或五子棋页）回到这一页时只会 resume，不会重建 ⇒ 主题档可能已经变了。
     * 变了就让系统按新主题重建这一页（onCreate 会重新取值建视图）。
     */
    @Override
    protected void onResume() {
        super.onResume();
        // 可能是搁了一夜才回来：先判一次过没过点，之后按分钟盯着（与对局页同一套）
        maybeAutoSwitchTheme();
        ui.removeCallbacks(themeTick);
        ui.postDelayed(themeTick, 60_000L);
        // 另外，从设置页回来时档位本身可能变了：这一种要换窗口主题，让系统按新主题重建本页
        boolean now = UiTheme.dark(this);
        if (now != dark) {
            setTheme(UiTheme.style(now));
            recreate();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(themeTick);
    }

    /**
     * 系统配置变了（转屏 / 系统字号 / 密度 / 深浅色…）：清单声明了这些项由本页自己处理，不会重建。
     * 明暗真变了才换窗口主题；**界面一律重铺** —— 字号是在 setTextSize 时折成 px 的，
     * 不重铺就不会跟系统字号/屏幕尺寸走（对局页与复盘页都是无条件重铺，这里保持同一行为）。
     */
    private void refreshTheme(android.content.res.Configuration cfg) {
        boolean now = UiTheme.dark(UiTheme.mode(this), cfg);
        if (now != dark) {
            dark = now;
            palette = Palette.of(dark);
            setTheme(UiTheme.style(dark));
        }
        screen = Screen.read(this);
        buildUi();
    }

    /** 「按时间自动」下，过点了就换 —— 回到前台时、以及每分钟各查一次（与对局页同一套）。 */
    private void maybeAutoSwitchTheme() {
        if (UiTheme.mode(this) != Theme.AUTO) return;
        boolean now = UiTheme.dark(this);
        if (now != dark) {
            dark = now;
            palette = Palette.of(dark);
            setTheme(UiTheme.style(dark));
            screen = Screen.read(this);
            buildUi();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(palette.background);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        // 右上角：「公告」＝更新说明（点开看这几版更新了什么）
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.RIGHT);
        Button notice = new Button(this);
        notice.setText("公告");
        notice.setAllCaps(false);
        notice.setTextSize(TypedValue.COMPLEX_UNIT_SP, screen.buttonTextSp() - 2);
        notice.setTextColor(palette.buttonText);
        notice.setBackgroundTintList(ColorStateList.valueOf(palette.buttonBg));
        notice.setPadding(dp(10), dp(4), dp(10), dp(4));
        notice.setMinimumWidth(0);
        notice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangelog();
            }
        });
        topRow.addView(notice, textRow(0, 0));
        root.addView(topRow, textRow(0, 4));

        TextView title = new TextView(this);
        title.setText("陪练棋盘");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTextColor(palette.text);
        title.setGravity(Gravity.CENTER);
        root.addView(title, textRow(0, 20));

        Button go = bigButton("围　棋", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MenuActivity.this, MainActivity.class));
            }
        });
        root.addView(go, buttonRow(0, 10));

        Button gomoku = bigButton("五子棋", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MenuActivity.this, GomokuActivity.class));
            }
        });
        root.addView(gomoku, buttonRow(0, 10));

        Button xiangqi = bigButton("象　棋", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MenuActivity.this, XiangqiActivity.class));
            }
        });
        root.addView(xiangqi, buttonRow(0, 10));

        Button junqi = bigButton("军　棋", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MenuActivity.this, JunqiActivity.class));
            }
        });
        root.addView(junqi, buttonRow(0, 10));

        TextView hint = new TextView(this);
        hint.setText("围棋：9 / 13 / 19 路 · KataGo 引擎\n五子棋：13 / 15 / 19 路 · 内置 AI\n象棋：内置三档 AI\n军棋：明棋 / 暗棋 · 内置 AI");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setTextColor(palette.textDim);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, textRow(0, 20));

        // 左下角：当前版本信息（空隙把这一行顶到底部）
        View spacer = new View(this);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        spacerLp.weight = 1f;
        root.addView(spacer, spacerLp);

        TextView ver = new TextView(this);
        ver.setText("版本 " + versionName() + "（versionCode " + versionCode() + "）");
        ver.setGravity(Gravity.LEFT);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        ver.setTextColor(palette.textDim);
        LinearLayout.LayoutParams verLp = textRow(0, 0);
        verLp.gravity = Gravity.LEFT;
        root.addView(ver, verLp);

        
        Watermark.wrap(this, root, palette, 6);
    }

    /** 右上角「公告」：把 Changelog 里的条目摊在弹窗里。 */
    private void showChangelog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(Changelog.title());
        b.setMessage(Changelog.text());
        b.setPositiveButton("知道了", null);
        b.show();
    }

    private Button bigButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, screen != null ? screen.buttonTextSp() + 6 : 20);
        b.setTextColor(palette.buttonText);
        b.setBackgroundTintList(ColorStateList.valueOf(palette.buttonBg));
        b.setOnClickListener(l);
        return b;
    }

    /** 按钮行：整行宽、固定 64dp 高，手指好按。 */
    private LinearLayout.LayoutParams buttonRow(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        int m = dp(6);
        p.setMargins(m, dp(top), m, dp(bottom));
        return p;
    }

    /** 文字行：整行宽、高度自适应。 */
    private LinearLayout.LayoutParams textRow(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = dp(6);
        p.setMargins(m, dp(top), m, dp(bottom));
        return p;
    }

    /** 从 PackageManager 读 versionName，避免在代码里硬编码版本号（与设置页同一做法）。 */
    private int versionCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return -1;
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
