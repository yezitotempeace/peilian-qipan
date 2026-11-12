/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 用户协议页（启动入口）：只有点「我同意」才进得了主菜单，点「不同意」直接退出。
 *
 * <p>这是一个**独立页面**而不是主菜单里的弹窗，原因有三：
 * <ol>
 *   <li>协议在进入主菜单之前就要挡住，用独立页最直接；</li>
 *   <li>按返回键、按最近任务回来都绕不过去（退出即结束整个任务）；</li>
 *   <li>界面层探针（{@code MenuProbe} 直接 new {@link MenuActivity}）不会被这道门干扰。</li>
 * </ol>
 *
 * <p>条款正文与「要不要显示」的判定在 {@link Eula}（纯 Java，有单测），本页只负责画和记选择。</p>
 *
 * <p>本页同时是**完整性标记的检查点**：每次启动先核对资源里的中性标记与
 * {@link BuildInfo} 常量是否一致。不一致时<b>不立刻</b>报错，而是按
 * {@link IntegrityGuard} 排一个几小时到几天后的到期时间；到期后才提示「不是正版」并退出。
 * 正常安装（标记完整）永远不会触发。</p>
 */
public class EulaActivity extends Activity {

    /** 首次启动时间（毫秒）。 */
    private static final String KEY_FIRST_SEEN = "mk_seen";
    /** 第一次发现标记异常的时间（毫秒）。 */
    private static final String KEY_BAD_SINCE = "mk_bad";
    /** 预定报错的时间（毫秒）。 */
    private static final String KEY_DUE = "mk_due";

    private Screen screen;
    private Palette palette;
    private boolean dark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 完整性标记：正常时什么都不做；异常时只排期，不在这里报错
        if (checkIntegrity()) {
            return;   // 已到期：提示并退出，不再往下走
        }
        // 已同意过当前版本（例如从设置里再次进来）：直接进主菜单，不停在这一页
        if (!Eula.shouldShow(acceptedVersion())) {
            enterMenu();
            return;
        }
        dark = UiTheme.apply(this);
        palette = Palette.of(dark);
        screen = Screen.read(this);
        buildUi();
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        // 清单声明了这些项由本页自己处理：重判明暗并重铺（字号是在 setTextSize 时折成 px 的）
        boolean now = UiTheme.dark(UiTheme.mode(this), cfg);
        if (now != dark) {
            dark = now;
            palette = Palette.of(dark);
            setTheme(UiTheme.style(dark));
        }
        screen = Screen.read(this);
        buildUi();
    }

    /** 返回键等同「不同意」：不留绕过口子。 */
    @Override
    public void onBackPressed() {
        refuse();
    }

    // ---------------------------------------------------------------- 选择

    /**
     * 核对标记并推进「延迟报错」的状态机。
     *
     * @return true = 已到期，调用方应立即停止（本方法已弹窗并结束任务）
     */
    private boolean checkIntegrity() {
        SharedPreferences sp = getSharedPreferences(UiTheme.PREF, MODE_PRIVATE);
        long now = System.currentTimeMillis();

        // 首次启动时间：只记一次，用于「装完当天不弹」
        long firstSeen = sp.getLong(KEY_FIRST_SEEN, 0L);
        if (firstSeen <= 0L) {
            firstSeen = now;
            sp.edit().putLong(KEY_FIRST_SEEN, firstSeen).apply();
        }

        boolean markOk = markIntact();
        long badSince = sp.getLong(KEY_BAD_SINCE, 0L);
        long due = sp.getLong(KEY_DUE, 0L);

        int verdict = IntegrityGuard.decide(now, firstSeen, badSince, due, markOk);
        if (verdict == IntegrityGuard.OK) {
            // 恢复正常：把异常状态清掉（换回正版就该照常用）
            if (badSince != 0L || due != 0L) {
                sp.edit().remove(KEY_BAD_SINCE).remove(KEY_DUE).apply();
            }
            return false;
        }
        if (verdict == IntegrityGuard.GRACE) {
            // 第一次发现异常：排期，但这次照常运行，不提示
            if (badSince <= 0L || due <= 0L) {
                long newDue = IntegrityGuard.dueAt(now, firstSeen, now);
                sp.edit().putLong(KEY_BAD_SINCE, now).putLong(KEY_DUE, newDue).apply();
            }
            return false;
        }
        // 到期：提示「不是正版」并退出
        showNotGenuine();
        return true;
    }

    /** 资源里的中性标记是否与代码常量一致。 */
    private boolean markIntact() {
        try {
            return BuildInfo.Check.mark(
                    getString(R.string.cfg_tag),
                    getString(R.string.cfg_rev),
                    getString(R.string.cfg_sig),
                    getString(R.string.cfg_src));
        } catch (RuntimeException e) {
            // 资源被删掉时 getString 会抛：同样算「标记不完整」
            return false;
        }
    }

    /** 到期提示：说明原因，然后退出。不删任何用户数据。 */
    private void showNotGenuine() {
        try {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("无法继续使用")
                    .setMessage("检测到本程序的文件已被修改，不是正版。\n\n"
                            + "请从官方仓库重新下载安装：\n" + BuildInfo.repo())
                    .setCancelable(false)
                    .setPositiveButton("退出", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            finishAffinity();
                        }
                    })
                    .show();
        } catch (RuntimeException e) {
            finishAffinity();
        }
    }

    private int acceptedVersion() {
        SharedPreferences sp = getSharedPreferences(UiTheme.PREF, MODE_PRIVATE);
        return sp.getInt(Eula.KEY_ACCEPTED, 0);
    }

    /** 同意了：记下版本号，进主菜单，并把本页关掉（别让返回键退回协议页）。 */
    private void agree() {
        getSharedPreferences(UiTheme.PREF, MODE_PRIVATE)
                .edit()
                .putInt(Eula.KEY_ACCEPTED, Eula.VERSION)
                .apply();
        enterMenu();
    }

    private void enterMenu() {
        startActivity(new Intent(this, MenuActivity.class));
        finish();
    }

    /** 不同意：什么都不记，直接退出整个任务（清掉所有 Activity）。 */
    private void refuse() {
        finishAffinity();
    }

    // ---------------------------------------------------------------- 界面

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = screen != null ? screen.dp(18) : 48;
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("用户协议");
        title.setTextColor(palette.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, (screen != null ? screen.buttonTextSp() : 18) + 8);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, (screen != null ? screen.dp(12) : 32));
        root.addView(title);

        TextView body = new TextView(this);
        body.setText(Eula.text());
        body.setTextColor(palette.text);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, (screen != null ? screen.buttonTextSp() : 16) - 2);
        body.setLineSpacing(0f, 1.45f);
        body.setTextIsSelectable(true);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 两个按钮：同意 / 不同意
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, (screen != null ? screen.dp(16) : 44), 0, 0);

        Button refuse = actionButton("不同意", false);
        refuse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refuse();
            }
        });
        Button agree = actionButton("我同意", true);
        agree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                agree();
            }
        });
        row.addView(refuse, weighted());
        row.addView(agree, weighted());
        root.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll.addView(root);
        // 右下角水印（与其他页面一致；wrap 内部会自己 setContentView）
        Watermark.wrap(this, scroll, palette, 6);
    }

    private Button actionButton(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, screen != null ? screen.buttonTextSp() : 18);
        b.setTextColor(primary ? palette.buttonText : palette.text);
        b.setBackgroundTintList(ColorStateList.valueOf(primary ? palette.buttonBg : palette.surface));
        return b;
    }

    /** 两个按钮等分宽度、左右各留一点间距。 */
    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        int gap = screen != null ? screen.dp(6) : 16;
        p.setMargins(gap, 0, gap, 0);
        return p;
    }
}
