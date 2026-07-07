package com.ebook.reader.util;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import io.noties.markwon.image.SchemeHandler;

/**
 * 自定义文件方案处理器。
 * 处理 file:// 路径时，优先从本地下载内容读取图片，fallback 到 bundled assets。
 */
public class LocalFileSchemeHandler extends SchemeHandler {

    private final Context context;

    public LocalFileSchemeHandler(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public SchemeHandlerDef handle(@NonNull Raw raw) {
        String uriString = raw.uri().toString();
        SchemeHandlerDef def = new SchemeHandlerDef();

        // 只处理 file:///android_asset/ 开头的路径
        if (uriString.startsWith("file:///android_asset/")) {
            String assetPath = uriString.substring("file:///android_asset/".length());

            // 1. 先尝试从本地下载内容读取
            File contentDir = UpdateManager.getContentDir(context);
            if (contentDir != null) {
                File localFile = new File(contentDir, assetPath);
                if (localFile.exists()) {
                    try {
                        def.setInputStream(new FileInputStream(localFile));
                        return def;
                    } catch (IOException e) {
                        // fall through to assets
                    }
                }
            }

            // 2. Fallback 到 bundled assets
            try {
                InputStream is = context.getAssets().open(assetPath);
                def.setInputStream(is);
            } catch (IOException e) {
                // 文件不存在，返回空
            }
        }

        return def;
    }
}
