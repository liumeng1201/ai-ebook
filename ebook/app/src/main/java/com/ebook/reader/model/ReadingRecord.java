package com.ebook.reader.model;

/**
 * 阅读记录
 */
public class ReadingRecord {
    public String bookName;
    public String herf;
    public int scrollY;
    public long timestamp;

    public ReadingRecord(String bookName, String herf, int scrollY) {
        this.bookName = bookName;
        this.herf = herf;
        this.scrollY = scrollY;
        this.timestamp = System.currentTimeMillis();
    }
}
