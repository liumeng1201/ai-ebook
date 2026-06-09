package com.ebook.reader.model;

/**
 * 书本元数据
 */
public class BookMeta {
    private String jsonFileName;
    private String bookName;

    public BookMeta(String jsonFileName, String bookName) {
        this.jsonFileName = jsonFileName;
        this.bookName = bookName;
    }

    public String getJsonFileName() {
        return jsonFileName;
    }

    public String getBookName() {
        return bookName;
    }
}
