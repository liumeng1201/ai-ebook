package com.ebook.reader.util;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * version.json 的数据模型
 * 由 sync_assets.py 自动生成，Android 端读取用于版本展示和更新检测
 */
public class VersionInfo {
    @SerializedName("app_version_code")
    public int appVersionCode;

    @SerializedName("app_version_name")
    public String appVersionName;

    @SerializedName("app_commit_sha")
    public String appCommitSha;

    @SerializedName("books")
    public List<BookVersion> books;

    @SerializedName("generated_at")
    public String generatedAt;

    public static class BookVersion {
        public String id;
        public String name;

        @SerializedName("json_file")
        public String jsonFile;

        @SerializedName("source_repo")
        public String sourceRepo;

        @SerializedName("content_sha")
        public String contentSha;
    }
}
