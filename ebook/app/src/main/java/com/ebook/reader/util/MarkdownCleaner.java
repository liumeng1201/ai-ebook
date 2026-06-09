package com.ebook.reader.util;

/**
 * 去除 MD 文件开头的 YAML 头部 (--- ... ---)
 */
public class MarkdownCleaner {

    /**
     * 移除 Markdown 文件开头的 YAML front matter
     */
    public static String removeYamlFrontMatter(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String trimmed = content.trim();
        if (!trimmed.startsWith("---")) {
            return content;
        }

        // 找到第二个 ---
        int secondDelimiter = trimmed.indexOf("---", 3);
        if (secondDelimiter == -1) {
            return content;
        }

        // 移除从开头到第二个 ---（含）之间的内容
        String rest = trimmed.substring(secondDelimiter + 3).trim();
        return rest.isEmpty() ? content : rest;
    }
}
