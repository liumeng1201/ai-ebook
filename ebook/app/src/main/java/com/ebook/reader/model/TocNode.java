package com.ebook.reader.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录节点，支持递归层级结构
 */
public class TocNode {
    private String name;
    private String herf;
    private int level;          // 0=part, 1=chapter, 2=section
    private boolean expanded;   // 是否展开
    private List<TocNode> children;

    public TocNode(String name, String herf, int level) {
        this.name = name;
        this.herf = herf;
        this.level = level;
        this.expanded = true;   // 默认展开
        this.children = new ArrayList<>();
    }

    public String getName() { return name; }
    public String getHerf() { return herf; }
    public int getLevel() { return level; }
    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }
    public List<TocNode> getChildren() { return children; }
    public boolean isLeaf() { return children.isEmpty(); }
}
