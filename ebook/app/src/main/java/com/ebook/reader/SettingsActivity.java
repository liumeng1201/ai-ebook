package com.ebook.reader;

import android.os.Bundle;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {

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
    }
}
