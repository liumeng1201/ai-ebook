package com.ebook.reader.util;

import android.content.res.AssetManager;

import com.ebook.reader.model.TocNode;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 书本 JSON 解析与 TOC 树构建
 */
public class BookJsonParser {

    /**
     * 从 assets 中读取 JSON 并解析 book 字段
     */
    public static String parseBookName(AssetManager am, String jsonFileName) {
        try {
            InputStream is = am.open(jsonFileName);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            JsonElement book = root.get("book");
            return book != null ? book.getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从本地文件目录中读取 JSON 并解析 book 字段
     */
    public static String parseBookName(AssetManager am, String jsonFileName, File contentDir) {
        // 优先从 contentDir 读取
        try {
            File localFile = new File(contentDir, jsonFileName);
            if (localFile.exists()) {
                InputStream is = new FileInputStream(localFile);
                JsonObject root = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                JsonElement book = root.get("book");
                return book != null ? book.getAsString() : null;
            }
        } catch (Exception ignored) {}
        // fallback 到 assets
        return parseBookName(am, jsonFileName);
    }

    /**
     * 解析目录树结构
     */
    public static List<TocNode> parseTocTree(InputStream is) {
        List<TocNode> partNodes = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();

            // 解析 part 数组
            var parts = root.getAsJsonArray("part");
            if (parts == null) return partNodes;

            int index = 0;
            for (var partElem : parts) {
                JsonObject partObj = partElem.getAsJsonObject();
                String partName = getString(partObj, "name");
                String partHerf = getString(partObj, "herf");
                TocNode partNode = new TocNode(partName, partHerf, 0);

                // 解析 chapter
                var chapters = partObj.getAsJsonArray("chapter");
                if (chapters != null) {
                    for (var chElem : chapters) {
                        JsonObject chObj = chElem.getAsJsonObject();
                        String chName = getString(chObj, "name");
                        String chHerf = getString(chObj, "herf");
                        TocNode chNode = new TocNode(chName, chHerf, 1);

                        // 解析 section
                        var sections = chObj.getAsJsonArray("section");
                        if (sections != null) {
                            for (var secElem : sections) {
                                JsonObject secObj = secElem.getAsJsonObject();
                                String secName = getString(secObj, "name");
                                String secHerf = getString(secObj, "herf");
                                TocNode secNode = new TocNode(secName, secHerf, 2);
                                chNode.getChildren().add(secNode);
                            }
                        }
                        partNode.getChildren().add(chNode);
                    }
                }
                partNodes.add(partNode);
                index++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return partNodes;
    }

    /**
     * 从 assets 中读取 JSON 并解析目录树（向后兼容）
     */
    public static List<TocNode> parseTocTree(AssetManager am, String jsonFileName) {
        try {
            InputStream is = am.open(jsonFileName);
            return parseTocTree(is);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 获取第一个叶子节点的 herf
     */
    public static String getFirstLeafHerf(List<TocNode> nodes) {
        for (TocNode node : nodes) {
            if (node.isLeaf() && node.getHerf() != null && !node.getHerf().isEmpty()) {
                return node.getHerf();
            }
            String found = getFirstLeafHerf(node.getChildren());
            if (found != null) return found;
        }
        return null;
    }

    private static String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : null;
    }
}
