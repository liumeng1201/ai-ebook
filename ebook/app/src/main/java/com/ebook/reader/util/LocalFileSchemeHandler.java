package com.ebook.reader.util;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import io.noties.markwon.image.ImageItem;
import io.noties.markwon.image.SchemeHandler;

/**
 * 自定义 URI scheme 处理器，使用 "localfile" scheme。
 *
 * 处理 localfile:// 路径时，从本地文件系统读取图片。
 * file:///android_asset/ 路径仍由 FileSchemeHandler 处理。
 *
 * 用法：
 *   ImagesPlugin.create()
 *     .addSchemeHandler(FileSchemeHandler.createWithAssets(ctx))
 *     .addSchemeHandler(new LocalFileSchemeHandler(ctx))
 *
 * 在 resolveImagePaths() 中：
 *   - 图片在本地内容中 → 输出 localfile:///path/to/image
 *   - 图片仅在 assets   → 输出 file:///android_asset/path
 */
public class LocalFileSchemeHandler extends SchemeHandler {

    private final Context context;

    public LocalFileSchemeHandler(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public Set<String> supportedSchemes() {
        return Collections.singleton("localfile");
    }

    @Nullable
    @Override
    public ImageItem handle(@NonNull String raw, @NonNull Uri uri) {
        final String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }

        try {
            return ImageItem.withDecodingNeeded(null, new FileInputStream(path));
        } catch (IOException e) {
            return null;
        }
    }
}
