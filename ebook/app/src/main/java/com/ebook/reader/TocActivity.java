package com.ebook.reader;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ebook.reader.model.TocNode;
import com.ebook.reader.util.BookJsonParser;
import com.ebook.reader.util.UpdateManager;

import java.util.ArrayList;
import java.util.List;

public class TocActivity extends AppCompatActivity {

    private RecyclerView tocList;
    private List<TocNode> tocTree = new ArrayList<>();
    private List<TocDisplayItem> visibleItems = new ArrayList<>();
    private String currentHerf;
    private String jsonFile;
    private String bookId;
    private TocAdapter adapter;
    private int verticalPaddingPx;

    /**
     * 包装 TocNode 并与分组信息关联
     */
    private static class TocDisplayItem {
        final TocNode node;
        final int groupId;     // 同一 groupId 的连续 item 属于同一个色块组
        final boolean isPart;  // true = part 色块, false = chapter 色块

        TocDisplayItem(TocNode node, int groupId, boolean isPart) {
            this.node = node;
            this.groupId = groupId;
            this.isPart = isPart;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toc);

        jsonFile = getIntent().getStringExtra("jsonFile");
        bookId = getIntent().getStringExtra("bookId");
        currentHerf = getIntent().getStringExtra("currentHerf");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("目录");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        verticalPaddingPx = (int) (16 * getResources().getDisplayMetrics().density);

        tocList = findViewById(R.id.toc_list);
        tocList.setLayoutManager(new LinearLayoutManager(this));

        try {
            tocTree = BookJsonParser.parseTocTree(UpdateManager.openContent(this, bookId, jsonFile));
        } catch (Exception e) {
            finish();
            return;
        }
        rebuildVisible();

        adapter = new TocAdapter();
        tocList.setAdapter(adapter);

        // 滚动到当前阅读章节
        scrollToCurrentChapter();
    }

    private void rebuildVisible() {
        visibleItems.clear();
        collectVisibleItems(tocTree, 0, 0);
    }

    /**
     * 递归展平可见节点，同时为每个节点分配 groupId
     *
     * @param nodes      当前层级节点列表
     * @param partIdx    当前已遍历到的 part 序号（从 1 开始）
     * @param chapterIdx 当前 part 内已遍历到的 chapter 序号（从 1 开始）
     */
    private void collectVisibleItems(List<TocNode> nodes, int partIdx, int chapterIdx) {
        int localPart = partIdx;
        int localChapter = chapterIdx;

        for (TocNode node : nodes) {
            int groupId;
            boolean isPart;

            if (node.getLevel() == 0) {
                // Part 节点：自成一个 part 色块组
                localPart++;
                localChapter = 0;
                groupId = localPart;
                isPart = true;
            } else if (node.getLevel() == 1) {
                // Chapter 节点：开启新的 chapter 色块组（含其下 sections）
                localChapter++;
                groupId = localPart * 10000 + localChapter;
                isPart = false;
            } else {
                // Section 节点：归入父 chapter 色块组
                groupId = localPart * 10000 + localChapter;
                isPart = false;
            }

            visibleItems.add(new TocDisplayItem(node, groupId, isPart));

            if (node.isExpanded() && !node.isLeaf()) {
                collectVisibleItems(node.getChildren(), localPart, localChapter);
            }
        }
    }

    private void scrollToCurrentChapter() {
        if (currentHerf == null) return;
        for (int i = 0; i < visibleItems.size(); i++) {
            TocDisplayItem item = visibleItems.get(i);
            if (currentHerf.equals(item.node.getHerf())) {
                int finalI = i;
                tocList.post(() -> {
                    LinearLayoutManager lm = (LinearLayoutManager) tocList.getLayoutManager();
                    if (lm != null) lm.scrollToPositionWithOffset(finalI, 100);
                });
                break;
            }
        }
    }

    private class TocAdapter extends RecyclerView.Adapter<TocAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_toc_node, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            TocDisplayItem item = visibleItems.get(position);
            TocNode node = item.node;

            String name = node.getName();
            if (name == null || name.isEmpty()) {
                name = "未命名";
            }
            holder.textView.setText(name);

            // 层级缩进
            int basePadding = (int) (16 * getResources().getDisplayMetrics().density);
            int indentPadding = node.getLevel() * (int) (20 * getResources().getDisplayMetrics().density);
            holder.textView.setPadding(basePadding + indentPadding, verticalPaddingPx, basePadding, verticalPaddingPx);

            // 根据层级设置不同样式
            switch (node.getLevel()) {
                case 0: // part
                    holder.textView.setTextSize(16);
                    holder.textView.setTypeface(null, Typeface.BOLD);
                    break;
                case 1: // chapter
                    holder.textView.setTextSize(15);
                    holder.textView.setTypeface(null, Typeface.NORMAL);
                    break;
                case 2: // section
                    holder.textView.setTextSize(14);
                    holder.textView.setTypeface(null, Typeface.NORMAL);
                    break;
            }

            // 当前阅读章节高亮
            if (node.getHerf() != null && node.getHerf().equals(currentHerf)) {
                holder.textView.setTextColor(getResources().getColor(R.color.current_chapter, getTheme()));
            } else {
                holder.textView.setTextColor(getResources().getColor(R.color.default_text, getTheme()));
            }

            // ---------- 分组色块背景 ----------
            boolean isNewGroup = position == 0
                    || item.groupId != visibleItems.get(position - 1).groupId;

            // 组间间距（首个 item 不加）
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
            if (isNewGroup && position > 0) {
                lp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
            } else {
                lp.topMargin = 0;
            }
            holder.itemView.setLayoutParams(lp);

            // 设置背景色
            int bgColor = item.isPart
                    ? ContextCompat.getColor(TocActivity.this, R.color.toc_part_bg)
                    : ContextCompat.getColor(TocActivity.this, R.color.toc_chapter_bg);
            holder.itemView.setBackgroundColor(bgColor);

            // 展开/收起图标已移除

            // 分隔线（仅非最后一项显示）
            holder.divider.setVisibility(
                    position < getItemCount() - 1 ? View.VISIBLE : View.GONE
            );

            // 点击处理
            holder.itemView.setOnClickListener(v -> {
                if (node.isLeaf() && node.getHerf() != null && !node.getHerf().isEmpty()) {
                    Intent result = new Intent();
                    result.putExtra("selectedHerf", node.getHerf());
                    setResult(RESULT_OK, result);
                    finish();
                } else {
                    node.setExpanded(!node.isExpanded());
                    rebuildVisible();
                    notifyDataSetChanged();
                }
            });
        }

        @Override
        public int getItemCount() {
            return visibleItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            View divider;

            ViewHolder(View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.toc_node_text);
                divider = itemView.findViewById(R.id.toc_divider);
            }
        }
    }
}
