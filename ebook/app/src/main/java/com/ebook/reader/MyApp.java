package com.ebook.reader;

import android.app.Application;

import com.ebook.reader.util.UpdateManager;

public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.applyTheme(this);
        // 后台静默检查内容更新（受限流控制，最多每 30 分钟一次）
        UpdateManager.checkContentUpdate(this);
    }
}
