package com.autogo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;

/**
 * 启动选择页：从这里选「围棋 / 五子棋 / 象棋 / 军棋 / 2048」。
 *
 * 这一页只负责分流，不碰任何一方的对局逻辑：
 *   - 围棋 → {@link MainActivity}（原有对局界面，一行没改）
 *   - 五子棋 → {@link GomokuActivity}
 *   - 象棋 → {@link XiangqiActivity}
 *   - 军棋 → {@link JunqiActivity}
 *   - 2048 → {@link Game2048Activity}
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

    /** 每 2 小时检查一次更新（前台）；手动按钮随时可查。 */
    private static final long UPDATE_INTERVAL_MS = 2 * 3600 * 1000L;
    private Button updateButton;
    private boolean checkingUpdate;
    private final Runnable updateTick = new Runnable() {
        @Override
        public void run() {
            checkUpdates(false);
            ui.postDelayed(this, UPDATE_INTERVAL_MS);
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
        UpdateManager.cleanup(this);
        scheduleBackgroundUpdate();
        showPendingUpdate();
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
        ui.removeCallbacks(updateTick);
        ui.postDelayed(updateTick, UPDATE_INTERVAL_MS);
        showPendingUpdate();
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
        ui.removeCallbacks(updateTick);
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
        root.setBackgroundColor(0x00000000);   // 透明：让后面的装饰背景透出来
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
        topRow.addView(notice, topButton());

        updateButton = new Button(this);
        updateButton.setText("更新");
        updateButton.setAllCaps(false);
        updateButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, screen.buttonTextSp() - 2);
        updateButton.setTextColor(palette.buttonText);
        updateButton.setBackgroundTintList(ColorStateList.valueOf(palette.buttonBg));
        updateButton.setPadding(dp(10), dp(4), dp(10), dp(4));
        updateButton.setMinimumWidth(0);
        updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkUpdates(true);
            }
        });
        topRow.addView(updateButton, topButton());

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

        Button g2048 = bigButton("2048", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MenuActivity.this, Game2048Activity.class));
            }
        });
        root.addView(g2048, buttonRow(0, 10));

        TextView hint = new TextView(this);
        hint.setText("围棋：9 / 13 / 19 路 · KataGo 引擎\n五子棋：13 / 15 / 19 路 · 内置 AI\n象棋：内置三档 AI\n军棋：明棋 / 暗棋 · 内置 AI\n2048：4×4 数字合并");
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

        
        // 满铺装饰背景（明暗两套），内容层透明叠在上面
        MenuBackground bg = new MenuBackground(this);
        bg.setDark(palette.dark);
        FrameLayout screen = new FrameLayout(this);
        screen.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        screen.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Watermark.wrap(this, screen, palette, 6);
    }

    /**
     * 直连 GitHub 仓库检查更新。manual=true 时无更新/失败也提示；自动检查静默。
     * 结果写 lastUpdateCheck；新版本显示在右上角「更新」按钮上。
     */
    private void checkUpdates(final boolean manual) {
        if (checkingUpdate) {
            return;
        }
        long last = getSharedPreferences("autogo", MODE_PRIVATE).getLong("lastUpdateCheck", 0L);
        if (!manual && System.currentTimeMillis() - last < UPDATE_INTERVAL_MS) {
            return;
        }
        checkingUpdate = true;
        final ProgressDialog progress;
        if (manual) {
            progress = new ProgressDialog(this);
            progress.setMessage("正在检查更新…");
            progress.setCancelable(false);
            progress.show();
        } else {
            progress = null;
        }
        UpdateManager.check(this, new UpdateManager.Callback() {
            @Override
            public void onResult(UpdateManager.Info info, String error) {
                checkingUpdate = false;
                if (progress != null) {
                    progress.dismiss();
                }
                getSharedPreferences("autogo", MODE_PRIVATE).edit()
                        .putLong("lastUpdateCheck", System.currentTimeMillis())
                        .apply();
                if (error != null) {
                    if (manual) {
                        showUpdateError(error);
                    }
                    return;
                }
                if (info == null) {
                    return;
                }
                if (info.upToDate) {
                    clearAvailableUpdate();
                    if (updateButton != null) {
                        updateButton.setText("更新");
                    }
                    if (manual) {
                        Toast.makeText(MenuActivity.this, "已是最新版本 " + info.version, Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                if (updateButton != null) {
                    updateButton.setText("更新 · " + info.version);
                }
                if (manual) {
                    showUpdateDialog(info);
                }
            }
        });
    }

    /** 后台检查到的新版本，回到菜单时把按钮点亮；不主动弹窗。 */
    private void showPendingUpdate() {
        if (updateButton == null) {
            return;
        }
        String version = getSharedPreferences("autogo", MODE_PRIVATE)
                .getString("availableUpdateVersion", "");
        if (version == null || version.length() == 0) {
            return;
        }
        int pending = UpdateManager.versionCodeFromTag(version);
        if (pending <= versionCode()) {
            clearAvailableUpdate();
            return;
        }
        updateButton.setText("更新 · " + version);
    }

    private void clearAvailableUpdate() {
        getSharedPreferences("autogo", MODE_PRIVATE).edit()
                .remove("availableUpdateVersion")
                .remove("availableUpdateUrl")
                .remove("availableUpdateNotes")
                .apply();
    }

    /** 后台检查：排下一次；由 receiver 查完再排，重启/强停后下次打开会补排。 */
    private void scheduleBackgroundUpdate() {
        UpdateScheduler.schedule(this);
    }

    /** 检查失败时给一条手动路：直接打开仓库 Release 页。 */
    private void showUpdateError(String error) {
        new AlertDialog.Builder(this)
                .setTitle("检查更新失败")
                .setMessage(error + "\n\n可以打开仓库手动下载最新 APK。")
                .setPositiveButton("打开仓库", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UpdateManager.REPO_PAGE)));
                        } catch (Exception ignored) {
                        }
                    }
                })
                .setNegativeButton("知道了", null)
                .show();
    }

    private void showUpdateDialog(final UpdateManager.Info info) {
        String notes = info.notes == null ? "" : info.notes;
        if (notes.length() > 800) {
            notes = notes.substring(0, 800) + "…";
        }
        new AlertDialog.Builder(this)
                .setTitle("发现新版本 " + info.version)
                .setMessage(notes)
                .setPositiveButton("下载更新", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        UpdateManager.downloadAndInstall(MenuActivity.this, info);
                    }
                })
                .setNegativeButton("以后", null)
                .show();
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

    /** 右上角两个小按钮：各自按文字宽，别在水平行里吃满/被挤成 0。 */
    private LinearLayout.LayoutParams topButton() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
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
