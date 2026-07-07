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

    // 用 GITHUB_TOKEN 提高 API 速率限制（如果有的话）
    private static final String GITHUB_API = "https://api.github.com/repos/liumeng1201/ai-ebook/releases";
    private static final String RAW_BASE = "https://raw.githubusercontent.com/liumeng1201/ai-ebook/master";

    private static final String PREFS_NAME = "update_prefs";
    private static final String KEY_LAST_CHECK = "last_check_time";
    private static final String KEY_CURRENT_VERSION = "current_content_version";
    private static final String THROTTLE_MS = 30 * 60 * 1000L; // 30 分钟

    private static final String STAGING_DIR = "content-staging";
    private static final String CURRENT_DIR = "content-current";
    private static final String VERSION_FILE = "version.json";

    // 更新类型
    public enum UpdateType { APP, CONTENT, BOTH }

    // 回调接口
    public interface UpdateCallback {
        void onChecking();
        void onUpdateAvailable(UpdateType type, String version, String releaseNotes);
        void onNoUpdate();
        void onError(String message);
    }

    // ============================================================
    // 1. 版本信息读取
    // ============================================================

    /**
     * 从 bundled assets 读取本地版本信息
     */
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
     * 在 MyApp.onCreate() 中调用
     */
    public static void checkContentUpdate(Context context) {
        if (shouldThrottle(context)) {
            Log.d(TAG, "距上次检查不足 30 分钟，跳过静默检查");
            return;
        }

        new Thread(() -> {
            try {
                VersionInfo local = getLocalVersion(context);
                if (local == null) return;

                // 获取最新 content release
                int latestContent = getLatestContentVersion();
                if (latestContent > getStoredContentVersion(context)) {
                    String downloadUrl = getContentDownloadUrl(latestContent);
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

                // 获取 GitHub Releases
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

                for (GitHubRelease release : releases) {
                    if (release.tagName != null && release.tagName.startsWith("v1.0.")) {
                        // App Release
                        try {
                            int remoteCode = Integer.parseInt(release.tagName.substring(4));
                            if (remoteCode > local.appVersionCode) {
                                hasAppUpdate = true;
                                appVersion = release.tagName;
                                releaseNotes = release.body != null ? release.body : "";
                                break; // 最新 app 版本
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }

                int latestContentVersion = getLatestContentVersionFromReleases(releases);
                int storedContent = getStoredContentVersion(context);
                if (latestContentVersion > storedContent) {
                    hasContentUpdate = true;
                    contentVersion = "content-" + latestContentVersion;
                }

                // 手动检查的结果用以显示，不影响限流
                recordManualCheckTime(context);

                if (hasAppUpdate && hasContentUpdate) {
                    callback.onUpdateAvailable(UpdateType.BOTH, appVersion + " + " + contentVersion, releaseNotes);
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

    /**
     * 下载内容包并安装
     */
    public static void downloadContentBundle(Context context, String url, int version) {
        try {
            Log.d(TAG, "下载内容包: " + url);

            // 下载到 cache 目录
            File cacheDir = new File(context.getCacheDir(), "downloads");
            cacheDir.mkdirs();
            File zipFile = new File(cacheDir, "content-bundle-" + version + ".zip");

            downloadFile(url, zipFile);

            // 解压到 staging 目录
            File stagingDir = new File(context.getFilesDir(), STAGING_DIR);
            if (stagingDir.exists()) {
                deleteDir(stagingDir);
            }
            stagingDir.mkdirs();

            unzip(zipFile, stagingDir);

            // 原子切换：重命名 staging → current
            File currentDir = new File(context.getFilesDir(), CURRENT_DIR);
            File currentDirBackup = new File(context.getFilesDir(), CURRENT_DIR + "-backup");

            if (currentDir.exists()) {
                // 先重命名旧的为备份（瞬间完成，同一分区）
                if (currentDirBackup.exists()) deleteDir(currentDirBackup);
                currentDir.renameTo(currentDirBackup);
            }

            // staging → current 原子重命名
            stagingDir.renameTo(currentDir);

            // 删除备份
            if (currentDirBackup.exists()) deleteDir(currentDirBackup);

            // 清理下载的 zip
            zipFile.delete();

            Log.d(TAG, "内容更新安装完成");
        } catch (Exception e) {
            Log.e(TAG, "内容更新下载/安装失败", e);
        }
    }

    /**
     * 下载 APK 并触发安装
     */
    public static void downloadAndInstallApk(Context context, String downloadUrl) {
        try {
            Log.d(TAG, "下载 APK: " + downloadUrl);

            File cacheDir = new File(context.getCacheDir(), "downloads");
            cacheDir.mkdirs();
            File apkFile = new File(cacheDir, "ebook-update.apk");

            downloadFile(downloadUrl, apkFile);

            // 触发安装
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    apkFile);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "APK 下载失败", e);
        }
    }

    // ============================================================
    // 4. 内容文件读取
    // ============================================================

    /**
     * 打开内容文件：优先读取本地下载内容，fallback 到 bundled assets
     */
    public static InputStream openContent(Context context, String assetPath) throws IOException {
        File localFile = new File(new File(context.getFilesDir(), CURRENT_DIR), assetPath);
        if (localFile.exists()) {
            return new FileInputStream(localFile);
        }
        return context.getAssets().open(assetPath);
    }

    /**
     * 获取内容根目录（用于图片加载）
     */
    public static File getContentDir(Context context) {
        File current = new File(context.getFilesDir(), CURRENT_DIR);
        if (current.exists()) {
            return current;
        }
        return null;
    }

    // ============================================================
    // 5. 内部工具方法
    // ============================================================

    private static boolean shouldThrottle(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(KEY_LAST_CHECK, 0);
        return (System.currentTimeMillis() - lastCheck) < THROTTLE_MS;
    }

    private static void saveLastCheckTime(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply();
    }

    /**
     * 保存手动检查时间（仅用于显示，不影响限流阈值）
     */
    private static void recordManualCheckTime(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong("manual_check_time", System.currentTimeMillis())
                .apply();
    }

    /**
     * 获取手动检查时间（毫秒）
     */
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
                .edit()
                .putInt(KEY_CURRENT_VERSION, version)
                .apply();
    }

    /**
     * 获取最新的 content release 编号
     */
    private static int getLatestContentVersion() {
        try {
            String json = httpGet(GITHUB_API + "/latest");
            JsonObject release = JsonParser.parseString(json).getAsJsonObject();
            String tag = release.get("tag_name").getAsString();
            if (tag.startsWith("content-")) {
                return Integer.parseInt(tag.substring(8));
            }
        } catch (Exception e) {
            Log.w(TAG, "获取最新内容版本失败", e);
        }
        return 0;
    }

    /**
     * 获取 content release 的下载链接
     */
    private static String getContentDownloadUrl(int version) {
        try {
            String json = httpGet(GITHUB_API + "/tags/content-" + version);
            JsonObject release = JsonParser.parseString(json).getAsJsonObject();
            JsonArray assets = release.getAsJsonArray("assets");
            if (assets != null && assets.size() > 0) {
                return assets.get(0).getAsJsonObject()
                        .get("browser_download_url").getAsString();
            }
        } catch (Exception e) {
            Log.w(TAG, "获取下载链接失败", e);
        }
        return null;
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
    private static String getContentDownloadUrlFromReleases(List<GitHubRelease> releases, int version) {
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

    /**
     * 获取 App Release 的 APK 下载链接
     */
    private static String getAppDownloadUrl(List<GitHubRelease> releases, String tagName) {
        for (GitHubRelease r : releases) {
            if (tagName.equals(r.tagName)) {
                if (r.assets != null) {
                    for (GitHubAsset asset : r.assets) {
                        if (asset.name != null && asset.name.endsWith(".apk")) {
                            return asset.browserDownloadUrl;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 调用 GitHub Releases API，带认证（如果 GITHUB_TOKEN 环境变量存在）
     */
    private static List<GitHubRelease> fetchReleases() throws IOException {
        String json = httpGet(GITHUB_API + "?per_page=5");
        Type listType = new TypeToken<List<GitHubRelease>>(){}.getType();
        return new Gson().fromJson(json, listType);
    }

    private static String httpGet(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        // 如果环境变量有 GITHUB_TOKEN，自动附加认证以提高限流
        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "token " + token);
        }

        int code = conn.getResponseCode();
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

        try (InputStream is = conn.getInputStream();
             OutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int len;
            long total = 0;
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

                // 防止 Zip Slip 安全漏洞
                String canonicalPath = target.getCanonicalPath();
                if (!canonicalPath.startsWith(destDir.getCanonicalPath() + File.separator)) {
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
                if (f.isDirectory()) {
                    deleteDir(f);
                } else {
                    f.delete();
                }
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
