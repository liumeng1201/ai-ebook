package com.ebook.reader.util;

import java.util.List;

/**
 * version.json 的数据模型
 * 由 sync_assets.py 自动生成，Android 端读取用于版本展示和更新检测
 */
public class VersionInfo {
    public int appVersionCode;
    public String appVersionName;
    public String appCommitSha;
    public List<BookVersion> books;
    public String generatedAt;

    public static class BookVersion {
        public String id;
        public String name;
        public String jsonFile;
        public String sourceRepo;
        public String contentSha;
    }
}
