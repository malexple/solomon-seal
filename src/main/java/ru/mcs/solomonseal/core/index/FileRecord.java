package ru.mcs.solomonseal.core.index;

/**
 * Запись о файле в индексе.
 */
public class FileRecord {
    public long   id;
    public String path;       // абсолютный путь
    public String language;
    public long   size;
    public String hash;       // SHA-256 для инкрементального обновления
    public String indexedAt;

    public FileRecord() {}

    public FileRecord(String path, String language, long size, String hash) {
        this.path     = path;
        this.language = language;
        this.size     = size;
        this.hash     = hash;
    }
}