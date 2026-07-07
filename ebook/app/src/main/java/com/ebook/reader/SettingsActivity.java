package com.ebook.reader;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.ebook.reader.util.UpdateManager;
import com.ebook.reader.util.VersionInfo;

import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvAppVersion;
    private LinearLayout containerBookVersions;
    private Button btnCheckApp;
    private Button btnCheckContent;
    private TextView tvLastCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("设置");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvAppVersion = findViewById(R.id.tv_app_version);
        containerBookVersions = findViewById(R.id.container_book_versions);
        btnCheckApp = findViewById(R.id.btn_check_app);
        btnCheckContent = findViewById(R.id.btn_check_content);
        tvLastCheck = findViewById(R.id.tv_last_check);

        // 1. 加载版本信息
        loadVersionInfo();

        // 2. 检查更新按钮
        btnCheckApp.setOnClickListener(v -> checkAppUpdate());
        btnCheckContent.setOnClickListener(v -> checkContentUpdate());

        // 3. 主题切换
        RadioGroup themeGroup = findViewById(R.id.theme_group);
        int currentMode = ThemeHelper.getThemeMode(this);

        int checkedId;
        switch (currentMode) {
            case ThemeHelper.MODE_LIGHT:
                checkedId = R.id.theme_light;
                break;
            case ThemeHelper.MODE_DARK:
                checkedId = R.id.theme_dark;
                break;
            default:
                checkedId = R.id.theme_system;
                break;
        }
        themeGroup.check(checkedId);

        themeGroup.setOnCheckedChangeListener((group, id) -> {
            int mode;
            if (id == R.id.theme_light) {
                mode = ThemeHelper.MODE_LIGHT;
            } else if (id == R.id.theme_dark) {
                mode = ThemeHelper.MODE_DARK;
            } else {
                mode = ThemeHelper.MODE_SYSTEM;
            }
            ThemeHelper.setThemeMode(SettingsActivity.this, mode);
            recreate();
        });

        // 4. 显示最后检查时间
        updateLastCheckTime();
    }

    /**
     * 加载并显示版本信息
     */
    private void loadVersionInfo() {
        VersionInfo info = UpdateManager.getLocalVersion(this);
        if (info == null) {
            tvAppVersion.setText("无法读取版本信息");
            return;
        }

        // App 版本
        tvAppVersion.setText(String.format(Locale.CHINA, "%s  (%s)",
                info.appVersionName, info.appCommitSha));

        // 各书内容版本
        containerBookVersions.removeAllViews();
        if (info.books != null) {
            for (VersionInfo.BookVersion book : info.books) {
                TextView tv = new TextView(this);
                String sha = book.contentSha != null ? book.contentSha : "unknown";
                tv.setText(String.format(Locale.CHINA, "  •  %s  %s", book.name, sha));
                tv.setTextSize(15f);
                tv.setPadding(0, 4, 0, 4);
                tv.setTextColor(getColor(android.R.color.darker_gray));
                containerBookVersions.addView(tv);
            }
        }
    }

    // ============================================================
    // App 更新检查
    // ============================================================

    private void checkAppUpdate() {
        btnCheckApp.setEnabled(false);
        btnCheckApp.setText("检查中...");

        UpdateManager.checkAppUpdate(this, new UpdateManager.UpdateCallback() {
            @Override
            public void onChecking() {
                runOnUiThread(() -> btnCheckApp.setText("检查中..."));
            }

            @Override
            public void onUpdateAvailable(UpdateManager.UpdateType type, String version, String releaseNotes) {
                runOnUiThread(() -> {
                    btnCheckApp.setEnabled(true);
                    btnCheckApp.setText("📱 App 更新");
                    updateLastCheckTime();

                    new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("📱 App 更新")
                            .setMessage("发现新版本: " + version + "\n\n" + releaseNotes)
                            .setPositiveButton("立即下载", (dialog, which) -> {
                                Toast.makeText(SettingsActivity.this,
                                        "正在下载 APK...", Toast.LENGTH_SHORT).show();
                                // 后台下载并安装
                                new Thread(() -> {
                                    List<UpdateManager.GitHubRelease> releases;
                                    try {
                                        releases = UpdateManager.fetchReleases();
                                        if (releases != null) {
                                            String apkUrl = UpdateManager.getAppApkUrl(
                                                    releases, version);
                                            if (apkUrl != null) {
                                                runOnUiThread(() -> Toast.makeText(
                                                        SettingsActivity.this,
                                                        "APK 下载中，稍后会自动安装",
                                                        Toast.LENGTH_LONG).show());
                                                UpdateManager.downloadAndInstallApk(
                                                        SettingsActivity.this, apkUrl);
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                }).start();
                            })
                            .setNegativeButton("稍后", null)
                            .show();
                });
            }

            @Override
            public void onNoUpdate() {
                runOnUiThread(() -> {
                    btnCheckApp.setEnabled(true);
                    btnCheckApp.setText("📱 App 更新");
                    updateLastCheckTime();
                    Toast.makeText(SettingsActivity.this,
                            "App 已是最新版本", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnCheckApp.setEnabled(true);
                    btnCheckApp.setText("📱 App 更新");
                    Toast.makeText(SettingsActivity.this,
                            "检查失败: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ============================================================
    // 内容更新检查
    // ============================================================

    private void checkContentUpdate() {
        btnCheckContent.setEnabled(false);
        btnCheckContent.setText("检查中...");

        UpdateManager.checkContentUpdateManual(this, new UpdateManager.UpdateCallback() {
            @Override
            public void onChecking() {
                runOnUiThread(() -> btnCheckContent.setText("检查中..."));
            }

            @Override
            public void onUpdateAvailable(UpdateManager.UpdateType type, String version, String releaseNotes) {
                runOnUiThread(() -> {
                    btnCheckContent.setEnabled(true);
                    btnCheckContent.setText("📖 内容更新");
                    updateLastCheckTime();

                    new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("📖 内容更新")
                            .setMessage("发现新版本: " + version + "\n\n新内容已就绪，立即下载？")
                            .setPositiveButton("立即下载", (dialog, which) -> {
                                Toast.makeText(SettingsActivity.this,
                                        "正在下载内容包...", Toast.LENGTH_SHORT).show();
                                // 后台下载
                                new Thread(() -> {
                                    List<UpdateManager.GitHubRelease> releases;
                                    try {
                                        releases = UpdateManager.fetchReleases();
                                        if (releases != null) {
                                            int cv = UpdateManager.getLatestContentVersion(releases);
                                            String url = UpdateManager.getContentBundleUrl(
                                                    releases, cv);
                                            if (url != null) {
                                                UpdateManager.downloadContentBundle(
                                                        SettingsActivity.this, url, cv);
                                                runOnUiThread(() -> Toast.makeText(
                                                        SettingsActivity.this,
                                                        "内容更新完成，重启后生效",
                                                        Toast.LENGTH_LONG).show());
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                }).start();
                            })
                            .setNegativeButton("稍后", null)
                            .show();
                });
            }

            @Override
            public void onNoUpdate() {
                runOnUiThread(() -> {
                    btnCheckContent.setEnabled(true);
                    btnCheckContent.setText("📖 内容更新");
                    updateLastCheckTime();
                    Toast.makeText(SettingsActivity.this,
                            "内容已是最新版本", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnCheckContent.setEnabled(true);
                    btnCheckContent.setText("📖 内容更新");
                    Toast.makeText(SettingsActivity.this,
                            "检查失败: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 更新最后检查时间显示
     */
    private void updateLastCheckTime() {
        long lastCheck = UpdateManager.getManualCheckTime(this);
        if (lastCheck > 0) {
            long diff = System.currentTimeMillis() - lastCheck;
            String relative;
            if (diff < 60000) {
                relative = "刚刚";
            } else if (diff < 3600000) {
                relative = (diff / 60000) + " 分钟前";
            } else {
                relative = (diff / 3600000) + " 小时前";
            }
            tvLastCheck.setText("最后检查: " + relative);
        } else {
            tvLastCheck.setText("尚未检查更新");
        }
    }
}
