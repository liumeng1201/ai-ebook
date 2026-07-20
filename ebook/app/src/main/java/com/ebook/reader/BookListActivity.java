package com.ebook.reader;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ebook.reader.util.UpdateManager;
import com.ebook.reader.util.VersionInfo;

import java.util.ArrayList;
import java.util.List;

public class BookListActivity extends AppCompatActivity {

    private final List<String> jsonFiles = new ArrayList<>();
    private final List<String> bookIds = new ArrayList<>();
    private final List<String> bookNames = new ArrayList<>();
    private RecyclerView bookList;
    private View emptyState;

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
        emptyState = findViewById(R.id.empty_content_state);
        Button downloadButton = findViewById(R.id.btn_download_content);
        downloadButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        bookIds.clear();
        bookNames.clear();

        VersionInfo manifest = UpdateManager.getLocalVersion(this);
        if (manifest != null && manifest.books != null) {
            for (VersionInfo.BookVersion book : manifest.books) {
                if (UpdateManager.isBookInstalled(this, book)) {
                    bookIds.add(book.id);
                    jsonFiles.add(book.jsonFile);
                    bookNames.add(book.name);
                }
            }
        }

        boolean hasBooks = !jsonFiles.isEmpty();
        bookList.setVisibility(hasBooks ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(hasBooks ? View.GONE : View.VISIBLE);
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
            String bookId = bookIds.get(position);
            String bookName = bookNames.get(position);
            holder.title.setText(bookName);
            holder.subtitle.setText(jsonFile);
            holder.card.setOnClickListener(v -> {
                Intent intent = new Intent(BookListActivity.this, ReaderActivity.class);
                intent.putExtra("jsonFile", jsonFile);
                intent.putExtra("bookId", bookId);
                intent.putExtra("bookName", bookName);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return jsonFiles.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final View card;
            final TextView title;
            final TextView subtitle;

            ViewHolder(View itemView) {
                super(itemView);
                card = itemView;
                title = itemView.findViewById(R.id.book_title);
                subtitle = itemView.findViewById(R.id.book_subtitle);
            }
        }
    }
}
