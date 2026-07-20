package com.ebook.reader.util;

import com.ebook.reader.model.TocNode;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class BookJsonParser {

    public static String parseBookName(File contentDir, String jsonFileName) {
        File localFile = new File(contentDir, jsonFileName);
        try (InputStream is = new FileInputStream(localFile)) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            JsonElement book = root.get("book");
            return book != null ? book.getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<TocNode> parseTocTree(InputStream is) {
        List<TocNode> partNodes = new ArrayList<>();
        try (InputStream input = is) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(input)).getAsJsonObject();
            var parts = root.getAsJsonArray("part");
            if (parts == null) return partNodes;

            for (var partElem : parts) {
                JsonObject partObj = partElem.getAsJsonObject();
                TocNode partNode = new TocNode(
                        getString(partObj, "name"), getString(partObj, "herf"), 0);
                var chapters = partObj.getAsJsonArray("chapter");
                if (chapters != null) {
                    for (var chElem : chapters) {
                        JsonObject chObj = chElem.getAsJsonObject();
                        TocNode chNode = new TocNode(
                                getString(chObj, "name"), getString(chObj, "herf"), 1);
                        var sections = chObj.getAsJsonArray("section");
                        if (sections != null) {
                            for (var secElem : sections) {
                                JsonObject secObj = secElem.getAsJsonObject();
                                chNode.getChildren().add(new TocNode(
                                        getString(secObj, "name"),
                                        getString(secObj, "herf"), 2));
                            }
                        }
                        partNode.getChildren().add(chNode);
                    }
                }
                partNodes.add(partNode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return partNodes;
    }

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
        JsonElement element = obj.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : null;
    }
}
