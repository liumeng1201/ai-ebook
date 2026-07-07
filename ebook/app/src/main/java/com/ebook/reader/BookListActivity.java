package com.ebook.reader;

import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ebook.reader.util.BookJsonParser;
import com.ebook.reader.util.UpdateManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BookListActivity extends AppCompatActivity {

    private RecyclerView bookList;
    private List<String> jsonFiles = new ArrayList<>();
    private List<String> bookNameCache = new ArrayList<>();
    private List<String> jsonSources = new ArrayList<>(); // "assets" or "local"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("Ebook 阅读");
        }

        bookList = findViewById(R.id.book_list);
        bookList.setLayoutManager(new LinearLayoutManager(this));

        loadBooks();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_book_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadBooks() {
        jsonFiles.clear();
        bookNameCache.clear();
        jsonSources.clear();
        Set<String> seen = new HashSet<>();

        // 1. 从 bundled assets 扫描
        AssetManager am = getAssets();
        try {
            String[] files = am.list("");
            if (files != null) {
                Arrays.sort(files);
                for (String file : files) {
                    if (file.endsWith(".json") && !file.equals("images")
                            && !file.equals("version.json")) {
                        String bookName = BookJsonParser.parseBookName(am, file);
                        if (bookName != null && !bookName.isEmpty()) {
                            seen.add(file);
                            jsonFiles.add(file);
                            bookNameCache.add(bookName);
                            jsonSources.add("assets");
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 2. 从本地下载内容扫描（补充 assets 中没有的书，或覆盖已有）
        File localContent = UpdateManager.getContentDir(this);
        if (localContent != null) {
            String[] localFiles = localContent.list();
            if (localFiles != null) {
                Arrays.sort(localFiles);
                for (String file : localFiles) {
                    if (file.endsWith(".json") && !file.equals("version.json")) {
                        // 已存在则更新名称（本地内容可能更新了 book 名称）
                        int idx = jsonFiles.indexOf(file);
                        if (idx >= 0) {
                            // 更新 jsonSources 标记为 local 表示优先
                            jsonSources.set(idx, "local");
                        } else {
                            // 新增的本地书籍
                            String bookName = BookJsonParser.parseBookName(
                                    am, file, localContent);
                            if (bookName != null && !bookName.isEmpty()) {
                                jsonFiles.add(file);
                                bookNameCache.add(bookName);
                                jsonSources.add("local");
                            }
                        }
                    }
                }
            }
        }

        bookList.setAdapter(new BookAdapter());
    }

    private class BookAdapter extends RecyclerView.Adapter<BookAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_book, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            String jsonFile = jsonFiles.get(position);
            String bookName = bookNameCache.get(position);
            String source = jsonSources.get(position);

            holder.title.setText(bookName != null ? bookName : jsonFile);
            String subtitle = jsonFile;
            if ("local".equals(source)) {
                subtitle += " (已更新)";
            }
            holder.subtitle.setText(subtitle);

            holder.card.setOnClickListener(v -> {
                Intent intent = new Intent(BookListActivity.this, ReaderActivity.class);
                intent.putExtra("jsonFile", jsonFile);
                intent.putExtra("bookName", bookName);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return jsonFiles.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            View card;
            TextView title;
            TextView subtitle;

            ViewHolder(View itemView) {
                super(itemView);
                card = itemView;
                title = itemView.findViewById(R.id.book_title);
                subtitle = itemView.findViewById(R.id.book_subtitle);
            }
        }
    }
}
