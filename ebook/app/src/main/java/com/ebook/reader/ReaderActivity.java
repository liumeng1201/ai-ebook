package com.ebook.reader;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.ebook.reader.model.ReadingRecord;
import com.ebook.reader.model.TocNode;
import com.ebook.reader.util.BookJsonParser;
import com.ebook.reader.util.MarkdownCleaner;
import com.ebook.reader.util.ProgressStore;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.image.file.FileSchemeHandler;

public class ReaderActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_TOC = 1001;

    private String jsonFile;
    private String bookName;
    private String currentHerf;
    private TextView contentView;
    private ScrollView scrollView;
    private ProgressBar progressBar;
    private List<TocNode> tocTree;
    private Markwon markwon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        jsonFile = getIntent().getStringExtra("jsonFile");
        bookName = getIntent().getStringExtra("bookName");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        contentView = findViewById(R.id.reader_content);
        scrollView = findViewById(R.id.reader_scroll);
        progressBar = findViewById(R.id.reader_progress);

        // 初始化 Markwon
        //   - ImagesPlugin: 图片加载，配置 FileSchemeHandler 支持 android_asset 路径
        //   - TablePlugin: 表格渲染
        markwon = Markwon.builder(this)
                .usePlugin(ImagesPlugin.create(new ImagesPlugin.ImagesConfigure() {
                    @Override
                    public void configureImages(ImagesPlugin plugin) {
                        plugin.addSchemeHandler(
                                FileSchemeHandler.createWithAssets(ReaderActivity.this));
                    }
                }))
                .usePlugin(TablePlugin.create(this))
                .build();

        // 解析目录
        tocTree = BookJsonParser.parseTocTree(getAssets(), jsonFile);

        // 确定初始章节
        ReadingRecord record = ProgressStore.getReadingRecord(this, bookName);
        if (record != null && record.herf != null) {
            currentHerf = record.herf;
        } else {
            currentHerf = BookJsonParser.getFirstLeafHerf(tocTree);
        }

        if (currentHerf == null || currentHerf.isEmpty()) {
            Toast.makeText(this, "无法找到可阅读的内容", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadChapter(currentHerf);

        // 目录按钮
        findViewById(R.id.btn_toc).setOnClickListener(v -> {
            Intent intent = new Intent(ReaderActivity.this, TocActivity.class);
            intent.putExtra("jsonFile", jsonFile);
            intent.putExtra("bookName", bookName);
            intent.putExtra("currentHerf", currentHerf);
            startActivityForResult(intent, REQUEST_CODE_TOC);
        });

        // 恢复滚动位置
        if (record != null && record.herf.equals(currentHerf)) {
            final int restoreY = record.scrollY;
            scrollView.post(() -> scrollView.scrollTo(0, restoreY));
        }

        // 保存进度 + 更新进度指示器
        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            int scrollY = scrollView.getScrollY();
            int maxScroll = Math.max(1, scrollView.getChildAt(0).getHeight() - scrollView.getHeight());
            int progress = (int) (((float) scrollY / maxScroll) * 100);
            progressBar.setProgress(progress);

            ReadingRecord r = new ReadingRecord(bookName, currentHerf, scrollY);
            ProgressStore.saveReadingRecord(ReaderActivity.this, r);
        });
    }

    private void loadChapter(String herf) {
        if (herf == null) return;
        currentHerf = herf;

        // 更新工具栏标题
        String chapterName = findChapterNameByHerf(herf);
        TextView titleView = findViewById(R.id.toolbar_title);
        if (titleView != null) {
            titleView.setText(chapterName != null ? chapterName : "阅读");
        }

        // 进度条置零
        progressBar.setProgress(0);

        // 读取 MD 文件
        String mdContent = readAssetContent(herf);
        if (mdContent == null) {
            contentView.setText("无法加载内容: " + herf);
            return;
        }

        // 去除 YAML front matter
        String cleaned = MarkdownCleaner.removeYamlFrontMatter(mdContent);

        // 将相对图片路径转为 file:///android_asset/ 路径
        String assetDir = herf.substring(0, herf.lastIndexOf('/') + 1);
        cleaned = resolveImagePaths(cleaned, assetDir);

        // Markwon 渲染
        markwon.setMarkdown(contentView, cleaned);

        // 滚动到顶部
        scrollView.post(() -> scrollView.scrollTo(0, 0));
    }

    private String readAssetContent(String assetPath) {
        try {
            InputStream is = getAssets().open(assetPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String findChapterNameByHerf(String herf) {
        return findNameRecursive(tocTree, herf);
    }

    private String findNameRecursive(List<TocNode> nodes, String herf) {
        for (TocNode node : nodes) {
            if (herf.equals(node.getHerf())) {
                return node.getName();
            }
            String found = findNameRecursive(node.getChildren(), herf);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * 将 markdown 中的相对图片路径替换为 file:///android_asset/ 路径
     * 匹配 ![](relative-path) 但不匹配 http:// / https:// / /(绝对路径) / data: / file:
     */
    private String resolveImagePaths(String markdown, String assetDir) {
        Pattern pattern = Pattern.compile(
                "!\\[([^\\]]*)\\]\\(((?!https?://|/|data:|file:)[^)]+)\\)"
        );
        Matcher matcher = pattern.matcher(markdown);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String alt = matcher.group(1);
            String path = matcher.group(2);
            String resolved = resolveRelativePath(assetDir, path);
            matcher.appendReplacement(sb,
                    Matcher.quoteReplacement("![" + alt + "](file:///android_asset/" + resolved + ")"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 将 baseDir 与相对路径合并，解析 ../ 和 ./ */
    private String resolveRelativePath(String baseDir, String relativePath) {
        return java.nio.file.Paths.get(baseDir, relativePath)
                .normalize()
                .toString()
                .replace('\\', '/');
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_TOC && resultCode == RESULT_OK && data != null) {
            String selectedHerf = data.getStringExtra("selectedHerf");
            if (selectedHerf != null && !selectedHerf.equals(currentHerf)) {
                loadChapter(selectedHerf);
                ReadingRecord r = new ReadingRecord(bookName, currentHerf, 0);
                ProgressStore.saveReadingRecord(this, r);
            }
        }
    }
}
