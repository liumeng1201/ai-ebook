package com.ebook.reader;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ebook.reader.model.TocNode;
import com.ebook.reader.util.BookJsonParser;

import java.util.ArrayList;
import java.util.List;

public class TocActivity extends AppCompatActivity {

    private RecyclerView tocList;
    private List<TocNode> tocTree = new ArrayList<>();
    private List<TocNode> visibleNodes = new ArrayList<>();
    private String currentHerf;
    private String jsonFile;
    private TocAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toc);

        jsonFile = getIntent().getStringExtra("jsonFile");
        currentHerf = getIntent().getStringExtra("currentHerf");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("目录");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tocList = findViewById(R.id.toc_list);
        tocList.setLayoutManager(new LinearLayoutManager(this));

        // 解析目录树
        tocTree = BookJsonParser.parseTocTree(getAssets(), jsonFile);
        rebuildVisible();

        adapter = new TocAdapter();
        tocList.setAdapter(adapter);

        // 滚动到当前阅读章节
        scrollToCurrentChapter();
    }

    private void rebuildVisible() {
        visibleNodes.clear();
        collectVisible(tocTree);
    }

    private void collectVisible(List<TocNode> nodes) {
        for (TocNode node : nodes) {
            visibleNodes.add(node);
            if (node.isExpanded() && !node.isLeaf()) {
                collectVisible(node.getChildren());
            }
        }
    }

    private void scrollToCurrentChapter() {
        if (currentHerf == null) return;
        for (int i = 0; i < visibleNodes.size(); i++) {
            TocNode node = visibleNodes.get(i);
            if (currentHerf.equals(node.getHerf())) {
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
            TocNode node = visibleNodes.get(position);
            String name = node.getName();
            if (name == null || name.isEmpty()) {
                name = "未命名";
            }
            holder.textView.setText(name);

            // 层级缩进 + 样式
            int basePadding = (int) (16 * getResources().getDisplayMetrics().density);
            int indentPadding = node.getLevel() * (int) (20 * getResources().getDisplayMetrics().density);
            holder.textView.setPadding(basePadding + indentPadding, 12, basePadding, 12);

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

            // 展开/收起箭头
            if (!node.isLeaf()) {
                holder.arrow.setVisibility(View.VISIBLE);
                holder.arrow.setImageResource(
                        node.isExpanded() ? R.drawable.ic_expand_less : R.drawable.ic_expand_more
                );
            } else {
                holder.arrow.setVisibility(View.INVISIBLE);
            }

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
            return visibleNodes.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ImageView arrow;
            View divider;

            ViewHolder(View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.toc_node_text);
                arrow = itemView.findViewById(R.id.toc_node_arrow);
                divider = itemView.findViewById(R.id.toc_divider);
            }
        }
    }
}
