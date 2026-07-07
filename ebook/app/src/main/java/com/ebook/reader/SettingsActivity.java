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

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvAppVersion;
    private LinearLayout containerBookVersions;
    private Button btnCheckUpdate;
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
        btnCheckUpdate = findViewById(R.id.btn_check_update);
        tvLastCheck = findViewById(R.id.tv_last_check);

        // 1. 加载版本信息
        loadVersionInfo();

        // 2. 检查更新按钮
        btnCheckUpdate.setOnClickListener(v -> checkForUpdates());

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
                tv.setTextColor(getColor(
                    android.R.color.darker_gray));
                containerBookVersions.addView(tv);
            }
        }
    }

    /**
     * 检查更新
     */
    private void checkForUpdates() {
        btnCheckUpdate.setEnabled(false);
        btnCheckUpdate.setText("检查中...");

        UpdateManager.checkAllUpdates(this, new UpdateManager.UpdateCallback() {
            @Override
            public void onChecking() {
                runOnUiThread(() -> {
                    btnCheckUpdate.setText("检查中...");
                });
            }

            @Override
            public void onUpdateAvailable(UpdateManager.UpdateType type, String version, String releaseNotes) {
                runOnUiThread(() -> {
                    btnCheckUpdate.setEnabled(true);
                    btnCheckUpdate.setText("  🔄  检查更新");
                    updateLastCheckTime();

                    String typeLabel;
                    switch (type) {
                        case APP:
                            typeLabel = "📱 App 更新";
                            break;
                        case CONTENT:
                            typeLabel = "📖 内容更新";
                            break;
                        default:
                            typeLabel = "📱📖 App + 内容更新";
                            break;
                    }

                    new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle(typeLabel)
                            .setMessage("发现新版本: " + version + "\n\n" + releaseNotes)
                            .setPositiveButton("立即更新", (dialog, which) -> {
                                Toast.makeText(SettingsActivity.this,
                                        "正在下载更新...", Toast.LENGTH_SHORT).show();
                                // 触发下载（后台静默下载内容包）
                                UpdateManager.checkContentUpdate(SettingsActivity.this);
                            })
                            .setNegativeButton("稍后", null)
                            .show();
                });
            }

            @Override
            public void onNoUpdate() {
                runOnUiThread(() -> {
                    btnCheckUpdate.setEnabled(true);
                    btnCheckUpdate.setText("  🔄  检查更新");
                    updateLastCheckTime();
                    Toast.makeText(SettingsActivity.this,
                            "已是最新版本", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnCheckUpdate.setEnabled(true);
                    btnCheckUpdate.setText("  🔄  检查更新");
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
