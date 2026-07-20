package com.ebook.reader;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ebook.reader.util.UpdateManager;
import com.ebook.reader.util.VersionInfo;

import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_POST_NOTIFICATIONS = 1001;

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

        loadVersionInfo();

        btnCheckApp.setOnClickListener(v -> checkAppUpdate());
        btnCheckContent.setOnClickListener(v -> checkContentUpdate());

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

        updateLastCheckTime();
    }

    private void loadVersionInfo() {
        VersionInfo info = UpdateManager.getLocalVersion(this);
        tvAppVersion.setText(String.format(Locale.CHINA, "%s  (build %d)",
                UpdateManager.getInstalledAppVersionName(this),
                UpdateManager.getInstalledAppVersionCode(this)));
        containerBookVersions.removeAllViews();
        if (info == null) {
            TextView emptyView = new TextView(this);
            emptyView.setText("尚未下载内容包");
            emptyView.setTextSize(15f);
            emptyView.setTextColor(getColor(android.R.color.darker_gray));
            containerBookVersions.addView(emptyView);
            return;
        }

        if (info.books != null) {
            for (VersionInfo.BookVersion book : info.books) {
                TextView tv = new TextView(this);
                String sha = book.contentSha != null ? book.contentSha : "unknown";
                tv.setText(String.format(Locale.CHINA, "  • %s  %s", book.name, sha));
                tv.setTextSize(15f);
                tv.setPadding(0, 4, 0, 4);
                tv.setTextColor(getColor(android.R.color.darker_gray));
                containerBookVersions.addView(tv);
            }
        }
    }

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
                                requestNotificationPermissionIfNeeded();
                                Toast.makeText(SettingsActivity.this,
                                        "正在下载 APK...", Toast.LENGTH_SHORT).show();
                                new Thread(() -> {
                                    try {
                                        List<UpdateManager.GitHubRelease> releases =
                                                UpdateManager.fetchReleases();
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
                                    } catch (Exception ignored) {
                                    }
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
                                requestNotificationPermissionIfNeeded();
                                Toast.makeText(SettingsActivity.this,
                                        "正在下载内容包...", Toast.LENGTH_SHORT).show();
                                new Thread(() -> {
                                    UpdateManager.ContentUpdateResult result =
                                            UpdateManager.downloadContentUpdates(SettingsActivity.this);
                                    runOnUiThread(() -> {
                                        loadVersionInfo();
                                        updateLastCheckTime();
                                        if (result.isSuccess()) {
                                            Toast.makeText(SettingsActivity.this,
                                                    "内容更新完成：" + result.updatedBookIds.size() + " 本书",
                                                    Toast.LENGTH_LONG).show();
                                        } else {
                                            Toast.makeText(SettingsActivity.this,
                                                    "部分内容更新失败，请稍后重试",
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
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
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_POST_NOTIFICATIONS);
    }
}
