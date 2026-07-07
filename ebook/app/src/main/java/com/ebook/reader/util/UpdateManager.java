package com.ebook.reader.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 版本管理与更新检测。
 *
 * 职责：
 *   1. 读取本地 version.json 获取版本信息
 *   2. 请求 GitHub Releases API 检测更新
 *   3. 下载内容包（原子切换：staging → rename）
 *   4. 下载并安装 APK
 *   5. 内容文件读取（优先本地下载内容，fallback 到 bundled assets）
 *   6. 30 分钟限流
 */
public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String GITHUB_API = "https://api.github.com/repos/liumeng1201/ai-ebook/releases";
    private static final String PREFS_NAME = "update_prefs";
    private static final String KEY_LAST_CHECK = "last_check_time";
    private static final String KEY_CURRENT_VERSION = "current_content_version";
    private static final long THROTTLE_MS = 30 * 60 * 1000L;

    private static final String STAGING_DIR = "content-staging";
    private static final String CURRENT_DIR = "content-current";
    private static final String VERSION_FILE = "version.json";

    public enum UpdateType { APP, CONTENT, BOTH }

    public interface UpdateCallback {
        void onChecking();
        void onUpdateAvailable(UpdateType type, String version, String releaseNotes);
        void onNoUpdate();
        void onError(String message);
    }

    // ============================================================
    // 1. 版本信息读取
    // ============================================================

    public static VersionInfo getLocalVersion(Context context) {
        try {
            InputStream is = context.getAssets().open(VERSION_FILE);
            String json = readStream(is);
            return new Gson().fromJson(json, VersionInfo.class);
        } catch (Exception e) {
            Log.w(TAG, "无法读取本地 version.json", e);
            return null;
        }
    }

    // ============================================================
    // 2. 更新检测
    // ============================================================

    /**
     * 静默检查内容更新（后台，不弹窗，受限流控制）
     */
    public static void checkContentUpdate(Context context) {
        if (shouldThrottle(context)) {
            Log.d(TAG, "距上次检查不足 30 分钟，跳过");
            return;
        }
        saveLastCheckTime(context);

        new Thread(() -> {
            try {
                List<GitHubRelease> releases = fetchReleases();
                if (releases == null || releases.isEmpty()) return;

                int latestContent = getLatestContentVersionFromReleases(releases);
                if (latestContent > getStoredContentVersion(context)) {
                    String downloadUrl = getContentDownloadUrlFromReleases(releases, latestContent);
                    if (downloadUrl != null) {
                        downloadContentBundle(context, downloadUrl, latestContent);
                        setStoredContentVersion(context, latestContent);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "静默检查失败", e);
            }
        }).start();
    }

    /**
     * 手动检查所有更新（App + 内容），弹窗反馈
     */
    public static void checkAllUpdates(Context context, UpdateCallback callback) {
        callback.onChecking();

        new Thread(() -> {
            try {
                VersionInfo local = getLocalVersion(context);
                if (local == null) {
                    callback.onError("无法读取本地版本信息");
                    return;
                }

                List<GitHubRelease> releases = fetchReleases();
                if (releases == null || releases.isEmpty()) {
                    callback.onError("无法连接到 GitHub");
                    return;
                }

                boolean hasAppUpdate = false;
                boolean hasContentUpdate = false;
                String appVersion = "";
                String contentVersion = "";
                String releaseNotes = "";

                // --- 检查 App 更新 ---
                for (GitHubRelease release : releases) {
                    if (release.tagName != null && release.tagName.startsWith("v1.0.")) {
                        try {
                            int remoteCode = Integer.parseInt(release.tagName.substring(4));
                            if (remoteCode > local.appVersionCode) {
                                hasAppUpdate = true;
                                appVersion = release.tagName;
                                releaseNotes = release.body != null ? release.body : "";
                                break;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }

                // --- 检查内容更新 ---
                int latestContentVersion = getLatestContentVersionFromReleases(releases);
                int storedContent = getStoredContentVersion(context);
                if (latestContentVersion > storedContent) {
                    hasContentUpdate = true;
                    contentVersion = "content-" + latestContentVersion;
                }

                recordManualCheckTime(context);

                if (hasAppUpdate && hasContentUpdate) {
                    callback.onUpdateAvailable(UpdateType.BOTH,
                            appVersion + " + " + contentVersion, releaseNotes);
                } else if (hasAppUpdate) {
                    callback.onUpdateAvailable(UpdateType.APP, appVersion, releaseNotes);
                } else if (hasContentUpdate) {
                    callback.onUpdateAvailable(UpdateType.CONTENT, contentVersion, "");
                } else {
                    callback.onNoUpdate();
                }

            } catch (Exception e) {
                callback.onError("检查失败: " + e.getMessage());
            }
        }).start();
    }

    // ============================================================
    // 3. 内容下载与安装
    // ============================================================

    public static void downloadContentBundle(Context context, String url, int version) {
        try {
            Log.d(TAG, "下载内容包: " + url);

            File cacheDir = new File(context.getCacheDir(), "downloads");
            cacheDir.mkdirs();
            File zipFile = new File(cacheDir, "content-bundle-" + version + ".zip");

            downloadFile(url, zipFile);

            File stagingDir = new File(context.getFilesDir(), STAGING_DIR);
            if (stagingDir.exists()) deleteDir(stagingDir);
            stagingDir.mkdirs();

            unzip(zipFile, stagingDir);

            File currentDir = new File(context.getFilesDir(), CURRENT_DIR);
            File currentDirBackup = new File(context.getFilesDir(), CURRENT_DIR + "-backup");

            if (currentDir.exists()) {
                if (currentDirBackup.exists()) deleteDir(currentDirBackup);
                currentDir.renameTo(currentDirBackup);
            }

            stagingDir.renameTo(currentDir);

            if (currentDirBackup.exists()) deleteDir(currentDirBackup);
            zipFile.delete();

            Log.d(TAG, "内容更新安装完成");
        } catch (Exception e) {
            Log.e(TAG, "内容更新下载/安装失败", e);
        }
    }

    public static void downloadAndInstallApk(Context context, String downloadUrl) {
        try {
            Log.d(TAG, "下载 APK: " + downloadUrl);

            File cacheDir = new File(context.getCacheDir(), "downloads");
            cacheDir.mkdirs();
            File apkFile = new File(cacheDir, "ebook-update.apk");

            downloadFile(downloadUrl, apkFile);

            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW);
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", apkFile);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "APK 下载失败", e);
        }
    }

    /**
     * 手动触发内容更新（已知版本号和下载 URL）
     */
    public static void installContentUpdate(Context context, int version, String downloadUrl) {
        new Thread(() -> {
            downloadContentBundle(context, downloadUrl, version);
            setStoredContentVersion(context, version);
        }).start();
    }

    // ============================================================
    // 4. 内容文件读取
    // ============================================================

    public static InputStream openContent(Context context, String assetPath) throws IOException {
        File localFile = new File(new File(context.getFilesDir(), CURRENT_DIR), assetPath);
        if (localFile.exists()) {
            return new FileInputStream(localFile);
        }
        return context.getAssets().open(assetPath);
    }

    public static File getContentDir(Context context) {
        File current = new File(context.getFilesDir(), CURRENT_DIR);
        if (current.exists()) return current;
        return null;
    }

    // ============================================================
    // 5. 内部工具方法
    // ============================================================

    private static boolean shouldThrottle(Context context) {
        long lastCheck = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_CHECK, 0);
        return (System.currentTimeMillis() - lastCheck) < THROTTLE_MS;
    }

    private static void saveLastCheckTime(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
    }

    private static void recordManualCheckTime(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong("manual_check_time", System.currentTimeMillis()).apply();
    }

    public static long getManualCheckTime(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong("manual_check_time", 0);
    }

    private static int getStoredContentVersion(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_CURRENT_VERSION, 0);
    }

    private static void setStoredContentVersion(Context context, int version) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_CURRENT_VERSION, version).apply();
    }

    /**
     * 从 releases 列表中提取最新 content 版本号
     */
    private static int getLatestContentVersionFromReleases(List<GitHubRelease> releases) {
        int max = 0;
        for (GitHubRelease r : releases) {
            if (r.tagName != null && r.tagName.startsWith("content-")) {
                try {
                    int v = Integer.parseInt(r.tagName.substring(8));
                    if (v > max) max = v;
                } catch (NumberFormatException ignored) {}
            }
        }
        return max;
    }

    /**
     * 获取 content release 的下载链接
     */
    private static String getContentDownloadUrlFromReleases(
            List<GitHubRelease> releases, int version) {
        String targetTag = "content-" + version;
        for (GitHubRelease r : releases) {
            if (targetTag.equals(r.tagName)) {
                if (r.assets != null && !r.assets.isEmpty()) {
                    return r.assets.get(0).browserDownloadUrl;
                }
            }
        }
        return null;
    }

    // ============================================================
    // 6. HTTP / 文件工具
    // ============================================================

    private static List<GitHubRelease> fetchReleases() throws IOException {
        String json = httpGet(GITHUB_API + "?per_page=10");
        Type listType = new TypeToken<List<GitHubRelease>>(){}.getType();
        return new Gson().fromJson(json, listType);
    }

    private static String httpGet(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int code = conn.getResponseCode();
        if (code == 403) {
            // 被限流了
            throw new IOException("GitHub API 限流，请稍后再试");
        }
        if (code != 200) {
            throw new IOException("GitHub API 返回 " + code);
        }
        return readStream(conn.getInputStream());
    }

    private static String readStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private static void downloadFile(String urlString, File dest) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        long total = 0;
        try (InputStream is = conn.getInputStream();
             OutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
                total += len;
            }
        }
        Log.d(TAG, "下载完成: " + dest.getName() + " (" + total + " bytes)");
    }

    private static void unzip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File target = new File(destDir, entry.getName());

                // 防止 Zip Slip
                String canonicalTarget = target.getCanonicalPath();
                String canonicalDir = destDir.getCanonicalPath();
                if (!canonicalTarget.startsWith(canonicalDir + File.separator)
                        && !canonicalTarget.equals(canonicalDir)) {
                    throw new IOException("非法 ZIP 条目: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    target.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
        Log.d(TAG, "解压完成: " + destDir.getAbsolutePath());
    }

    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    // ============================================================
    // GitHub API 响应模型
    // ============================================================

    public static class GitHubRelease {
        @SerializedName("tag_name")
        public String tagName;
        public String name;
        public String body;
        public List<GitHubAsset> assets;
    }

    public static class GitHubAsset {
        public String name;
        @SerializedName("browser_download_url")
        public String browserDownloadUrl;
    }
}
