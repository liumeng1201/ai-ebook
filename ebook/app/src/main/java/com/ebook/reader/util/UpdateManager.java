package com.ebook.reader.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 *   5. 内容文件读取（仅已下载内容包）
 *   6. 30 分钟限流
 */
public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String GITHUB_API = "https://api.github.com/repos/liumeng1201/ai-ebook/releases";
    private static final String GITHUB_PROXY = "https://gh-proxy.com/";
    private static final String DOWNLOAD_CHANNEL_ID = "download_progress";
    static final String ACTION_CANCEL_DOWNLOAD = "com.ebook.reader.action.CANCEL_DOWNLOAD";
    static final String EXTRA_NOTIFICATION_ID = "notification_id";
    private static final String PREFS_NAME = "update_prefs";
    private static final String KEY_BOOK_VERSION_PREFIX = "book_version_";
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 30000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 120000;
    private static final int DOWNLOAD_RETRY_COUNT = 3;
    private static final int NOTIFICATION_ID_CONTENT = 1001;
    private static final int NOTIFICATION_ID_APK = 1002;

    private static final Set<Integer> CANCELLED_DOWNLOADS = Collections.newSetFromMap(
            new ConcurrentHashMap<Integer, Boolean>());
    private static final ConcurrentHashMap<Integer, HttpURLConnection> ACTIVE_DOWNLOADS =
            new ConcurrentHashMap<>();

    private static final String CONTENT_DIR = "content";
    private static final String BOOKS_DIR = "books";
    private static final String MANIFEST_FILE = "manifest.json";

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
        File localVersion = new File(getContentRoot(context), MANIFEST_FILE);
        if (localVersion.exists()) {
            try (InputStream is = new FileInputStream(localVersion)) {
                String json = readStream(is);
                return new Gson().fromJson(json, VersionInfo.class);
            } catch (Exception e) {
                Log.w(TAG, "无法读取已下载内容的 manifest.json", e);
            }
        }
        return null;
    }

    public static long getInstalledAppVersionCode(Context context) {
        try {
            android.content.pm.PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? packageInfo.getLongVersionCode()
                    : packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Unable to read installed app version code", e);
            return 0;
        }
    }

    public static String getInstalledAppVersionName(Context context) {
        try {
            android.content.pm.PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName != null ? packageInfo.versionName : "unknown";
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Unable to read installed app version name", e);
            return "unknown";
        }
    }

    // ============================================================
    // 2. 更新检测
    // ============================================================

    /**
     * 手动检查 App 更新
     */
    public static void checkAppUpdate(Context context, UpdateCallback callback) {
        callback.onChecking();

        new Thread(() -> {
            try {
                long installedVersionCode = getInstalledAppVersionCode(context);
                if (installedVersionCode <= 0) {
                    callback.onError("无法读取本地版本信息");
                    return;
                }

                List<GitHubRelease> releases = fetchReleases();
                if (releases == null || releases.isEmpty()) {
                    callback.onError("无法连接到 GitHub");
                    return;
                }

                for (GitHubRelease release : releases) {
                    if (release.tagName != null && release.tagName.startsWith("v")) {
                        String[] parts = release.tagName.split("\\.");
                        if (parts.length >= 3) {
                            try {
                                int remoteCode = Integer.parseInt(parts[parts.length - 1]);
                                if (remoteCode > installedVersionCode) {
                                    callback.onUpdateAvailable(UpdateType.APP,
                                            release.tagName,
                                            release.body != null ? release.body : "");
                                    return;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }

                recordManualCheckTime(context);
                callback.onNoUpdate();

            } catch (Exception e) {
                callback.onError("检查失败: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 手动检查内容更新
     */
    public static void checkContentUpdateManual(Context context, UpdateCallback callback) {
        callback.onChecking();

        new Thread(() -> {
            try {
                List<GitHubRelease> releases = fetchReleases();
                if (releases == null || releases.isEmpty()) {
                    callback.onError("无法连接到 GitHub");
                    return;
                }

                VersionInfo manifest = fetchLatestManifest(releases);
                recordManualCheckTime(context);
                if (manifest != null && hasContentUpdates(context, manifest)) {
                    callback.onUpdateAvailable(UpdateType.CONTENT,
                            "content-" + manifest.generatedAt, "");
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

    /** 下载最新配置并仅安装版本变化的书籍。单本失败不会回滚其他已成功书籍。 */
    public static ContentUpdateResult downloadContentUpdates(Context context) {
        ContentUpdateResult result = new ContentUpdateResult();
        try {
            VersionInfo manifest = fetchLatestManifest(fetchReleases());
            if (manifest == null || manifest.books == null) {
                throw new IOException("内容配置文件无效");
            }
            saveManifest(context, manifest);
            for (VersionInfo.BookVersion book : manifest.books) {
                if (book.id == null || book.jsonFile == null || book.downloadUrl == null) continue;
                if (isBookInstalled(context, book) && !needsBookUpdate(context, book)) continue;
                if (downloadBookBundle(context, book)) result.updatedBookIds.add(book.id);
                else result.failedBookIds.add(book.id);
            }
            recordManualCheckTime(context);
        } catch (Exception e) {
            Log.e(TAG, "内容更新失败", e);
            result.errorMessage = e.getMessage();
        }
        return result;
    }

    private static boolean downloadBookBundle(Context context, VersionInfo.BookVersion book) {
        File currentDir = getBookDir(context, book.id);
        File backupDir = new File(currentDir.getParentFile(), book.id + "-backup");
        try {
            File cacheDir = new File(context.getCacheDir(), "downloads");
            cacheDir.mkdirs();
            File zipFile = new File(cacheDir, book.id + ".zip");
            downloadFile(context, book.downloadUrl, zipFile, "下载 " + book.name,
                    NOTIFICATION_ID_CONTENT);

            File booksDir = new File(getContentRoot(context), BOOKS_DIR);
            booksDir.mkdirs();
            File stagingDir = new File(booksDir, book.id + "-staging");
            if (stagingDir.exists()) deleteDir(stagingDir);
            stagingDir.mkdirs();
            unzip(zipFile, stagingDir);
            if (!new File(stagingDir, book.jsonFile).isFile()) {
                throw new IOException("书籍内容包缺少目录文件: " + book.jsonFile);
            }
            currentDir = new File(booksDir, book.id);
            backupDir = new File(booksDir, book.id + "-backup");
            if (backupDir.exists()) deleteDir(backupDir);
            if (currentDir.exists() && !currentDir.renameTo(backupDir)) {
                throw new IOException("无法备份旧书籍内容");
            }
            if (!stagingDir.renameTo(currentDir)) throw new IOException("无法切换书籍内容");
            if (backupDir.exists()) deleteDir(backupDir);
            zipFile.delete();
            setBookVersion(context, book.id, book.contentVersion);
            return true;
        } catch (Exception e) {
            if (!currentDir.exists() && backupDir.exists()) {
                backupDir.renameTo(currentDir);
            }
            Log.e(TAG, "书籍更新失败: " + book.id, e);
            return false;
        }
    }

    public static void downloadAndInstallApk(Context context, String downloadUrl) {
        try {
            Log.d(TAG, "下载 APK: " + downloadUrl);

            File cacheDir = new File(context.getCacheDir(), "downloads");
            cacheDir.mkdirs();
            File apkFile = new File(cacheDir, "ebook-update.apk");

            downloadFile(context, downloadUrl, apkFile, "APK 下载", NOTIFICATION_ID_APK);

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

    static void cancelDownload(Context context, int notificationId) {
        CANCELLED_DOWNLOADS.add(notificationId);
        HttpURLConnection connection = ACTIVE_DOWNLOADS.remove(notificationId);
        if (connection != null) {
            connection.disconnect();
        }
        NotificationManager manager = getNotificationManager(context);
        if (manager != null) {
            manager.cancel(notificationId);
        }
    }

    /**
     * 手动触发内容更新（已知版本号和下载 URL）
     */
    public static void installContentUpdate(Context context, int version, String downloadUrl) {
        new Thread(() -> {
            downloadContentUpdates(context);
        }).start();
    }

    // ============================================================
    // 4. 内容文件读取
    // ============================================================

    public static InputStream openContent(Context context, String bookId, String assetPath) throws IOException {
        File localFile = new File(getBookDir(context, bookId), assetPath);
        if (localFile.exists()) {
            return new FileInputStream(localFile);
        }
        throw new IOException("内容包未下载或内容文件不存在: " + assetPath);
    }

    public static File getBookDir(Context context, String bookId) {
        return new File(new File(getContentRoot(context), BOOKS_DIR), bookId);
    }

    public static boolean isBookInstalled(Context context, VersionInfo.BookVersion book) {
        return book != null && book.id != null
                && getBookDir(context, book.id).isDirectory()
                && new File(getBookDir(context, book.id), book.jsonFile).isFile();
    }

    // ============================================================
    // 5. 内部工具方法
    // ============================================================

    private static void recordManualCheckTime(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong("manual_check_time", System.currentTimeMillis()).apply();
    }

    public static long getManualCheckTime(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong("manual_check_time", 0);
    }

    private static File getContentRoot(Context context) {
        return new File(context.getFilesDir(), CONTENT_DIR);
    }

    private static String getBookVersion(Context context, String bookId) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BOOK_VERSION_PREFIX + bookId, null);
    }

    private static void setBookVersion(Context context, String bookId, String version) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_BOOK_VERSION_PREFIX + bookId, version).apply();
    }

    private static boolean needsBookUpdate(Context context, VersionInfo.BookVersion book) {
        return book.contentVersion == null || !book.contentVersion.equals(getBookVersion(context, book.id));
    }

    private static boolean hasContentUpdates(Context context, VersionInfo manifest) {
        for (VersionInfo.BookVersion book : manifest.books) {
            if (!isBookInstalled(context, book) || needsBookUpdate(context, book)) return true;
        }
        return false;
    }

    /**
     * 从 releases 列表中提取最新 content 版本号
     */
    private static VersionInfo fetchLatestManifest(List<GitHubRelease> releases) throws IOException {
        GitHubRelease newest = null;
        int max = -1;
        for (GitHubRelease r : releases) {
            if (r.tagName != null && r.tagName.startsWith("content-")) {
                try {
                    int v = Integer.parseInt(r.tagName.substring(8));
                    if (v > max) { max = v; newest = r; }
                } catch (NumberFormatException ignored) {}
            }
        }
        if (newest == null || newest.assets == null) return null;
        for (GitHubAsset asset : newest.assets) {
            if ("manifest.json".equals(asset.name)) {
                VersionInfo manifest = new Gson().fromJson(
                        httpGet(withGitHubProxy(asset.browserDownloadUrl)), VersionInfo.class);
                if (manifest == null || manifest.schemaVersion < 2 || manifest.books == null) {
                    throw new IOException("manifest.json 格式不受支持");
                }
                return manifest;
            }
        }
        return null;
    }

    /**
     * 获取 content release 的下载链接
     */
    private static void saveManifest(Context context, VersionInfo manifest) throws IOException {
        File root = getContentRoot(context);
        root.mkdirs();
        File staging = new File(root, MANIFEST_FILE + ".staging");
        try (OutputStream os = new FileOutputStream(staging)) {
            os.write(new Gson().toJson(manifest).getBytes("UTF-8"));
        }
        File dest = new File(root, MANIFEST_FILE);
        if (dest.exists() && !dest.delete()) throw new IOException("无法替换本地内容配置");
        if (!staging.renameTo(dest)) throw new IOException("无法保存本地内容配置");
    }

    /**
     * 从 releases 中查找指定 App 版本标签的 APK 下载链接
     */
    public static String getAppApkUrl(List<GitHubRelease> releases, String tagName) {
        for (GitHubRelease r : releases) {
            if (tagName.equals(r.tagName) && r.assets != null) {
                for (GitHubAsset asset : r.assets) {
                    if (asset.name != null && asset.name.endsWith(".apk")) {
                        return asset.browserDownloadUrl;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 从 releases 中获取指定内容版本的下载 URL
     */
    public static class ContentUpdateResult {
        public final List<String> updatedBookIds = new ArrayList<>();
        public final List<String> failedBookIds = new ArrayList<>();
        public String errorMessage;

        public boolean isSuccess() {
            return errorMessage == null && failedBookIds.isEmpty();
        }
    }

    // ============================================================
    // 6. HTTP / 文件工具
    // ============================================================

    public static List<GitHubRelease> fetchReleases() throws IOException {
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

    private static void downloadFile(
            Context context,
            String urlString,
            File dest,
            String notificationTitle,
            int notificationId) throws IOException {
        CANCELLED_DOWNLOADS.remove(notificationId);
        IOException lastError = null;
        for (int attempt = 1; attempt <= DOWNLOAD_RETRY_COUNT; attempt++) {
            try {
                throwIfDownloadCancelled(notificationId);
                downloadFileOnce(context, urlString, dest, notificationTitle, notificationId);
                return;
            } catch (IOException e) {
                closeActiveDownload(notificationId);
                if (isDownloadCancelled(notificationId)) {
                    deletePartFile(dest);
                    Log.i(TAG, "Download cancelled: " + dest.getName());
                    throw new DownloadCancelledException();
                }
                lastError = e;
                Log.w(TAG, "下载失败，准备重试: " + attempt + "/" + DOWNLOAD_RETRY_COUNT, e);
                if (attempt < DOWNLOAD_RETRY_COUNT) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("下载被中断", interrupted);
                    }
                }
            }
        }
        notifyDownloadFailed(context, notificationId, notificationTitle);
        throw lastError != null ? lastError : new IOException("下载失败");
    }

    private static void downloadFileOnce(
            Context context,
            String urlString,
            File dest,
            String notificationTitle,
            int notificationId) throws IOException {
        File partFile = new File(dest.getAbsolutePath() + ".part");
        if (partFile.exists() && !partFile.delete()) {
            throw new IOException("无法删除未完成下载文件: " + partFile.getName());
        }

        URL url = new URL(withGitHubProxy(urlString));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        ACTIVE_DOWNLOADS.put(notificationId, conn);
        conn.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);
        throwIfDownloadCancelled(notificationId);

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("下载地址返回 " + code);
        }

        long contentLength = conn.getContentLengthLong();
        long total = 0;
        int lastProgress = -1;
        long lastNotifyTime = 0;
        notifyDownloadProgress(context, notificationId, notificationTitle, 0,
                contentLength, false);
        try (InputStream is = conn.getInputStream();
             OutputStream os = new FileOutputStream(partFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                throwIfDownloadCancelled(notificationId);
                os.write(buffer, 0, len);
                total += len;
                long now = System.currentTimeMillis();
                int progress = contentLength > 0
                        ? (int) Math.min(100, (total * 100) / contentLength)
                        : 0;
                if (contentLength <= 0 || progress != lastProgress || now - lastNotifyTime > 1000) {
                    notifyDownloadProgress(context, notificationId, notificationTitle, total,
                            contentLength, false);
                    lastProgress = progress;
                    lastNotifyTime = now;
                }
            }
        }
        throwIfDownloadCancelled(notificationId);
        if (dest.exists() && !dest.delete()) {
            throw new IOException("无法覆盖旧下载文件: " + dest.getName());
        }
        if (!partFile.renameTo(dest)) {
            throw new IOException("无法保存下载文件: " + dest.getName());
        }
        throwIfDownloadCancelled(notificationId);
        closeActiveDownload(notificationId);
        notifyDownloadProgress(context, notificationId, notificationTitle, total,
                contentLength, true);
        Log.d(TAG, "下载完成: " + dest.getName() + " (" + total + " bytes)");
    }

    private static boolean isDownloadCancelled(int notificationId) {
        return CANCELLED_DOWNLOADS.contains(notificationId);
    }

    private static void throwIfDownloadCancelled(int notificationId)
            throws DownloadCancelledException {
        if (isDownloadCancelled(notificationId)) {
            throw new DownloadCancelledException();
        }
    }

    private static void closeActiveDownload(int notificationId) {
        HttpURLConnection connection = ACTIVE_DOWNLOADS.remove(notificationId);
        if (connection != null) {
            connection.disconnect();
        }
    }

    private static void deletePartFile(File dest) {
        File partFile = new File(dest.getAbsolutePath() + ".part");
        if (partFile.exists() && !partFile.delete()) {
            Log.w(TAG, "Could not delete cancelled download: " + partFile.getName());
        }
    }

    private static class DownloadCancelledException extends IOException {
        DownloadCancelledException() {
            super("Download cancelled");
        }
    }

    private static String withGitHubProxy(String urlString) {
        if (urlString == null || urlString.startsWith(GITHUB_PROXY)) {
            return urlString;
        }
        return GITHUB_PROXY + urlString;
    }

    private static void notifyDownloadProgress(
            Context context,
            int notificationId,
            String title,
            long downloaded,
            long total,
            boolean complete) {
        NotificationManager manager = getNotificationManager(context);
        if (manager == null) return;

        String text;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(complete
                        ? android.R.drawable.stat_sys_download_done
                        : android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setOnlyAlertOnce(true)
                .setOngoing(false)
                .setAutoCancel(complete)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (complete) {
            text = "下载完成";
            builder.setContentText(text)
                    .setProgress(0, 0, false);
        } else if (total > 0) {
            int progress = (int) Math.min(100, (downloaded * 100) / total);
            text = "已下载 " + progress + "%";
            builder.setContentText(text)
                    .setProgress(100, progress, false);
        } else {
            text = "正在下载...";
            builder.setContentText(text)
                    .setProgress(0, 0, true);
        }

        if (!complete) {
            Intent cancelIntent = new Intent(context, DownloadCancelReceiver.class)
                    .setAction(ACTION_CANCEL_DOWNLOAD)
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId);
            PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,
                    "取消下载", cancelPendingIntent);
        }

        manager.notify(notificationId, builder.build());
    }

    private static void notifyDownloadFailed(Context context, int notificationId, String title) {
        NotificationManager manager = getNotificationManager(context);
        if (manager == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText("下载失败")
                .setOnlyAlertOnce(true)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        manager.notify(notificationId, builder.build());
    }

    private static NotificationManager getNotificationManager(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    DOWNLOAD_CHANNEL_ID,
                    "下载进度",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("显示 APK 和内容包下载进度");
            manager.createNotificationChannel(channel);
        }
        return manager;
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
