package ru.mcs.llmrobot.core.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite индекс одного проекта.
 * Файл: ~/.llm-robot/projects/<projectId>/index.db
 */
public class ProjectIndex implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ProjectIndex.class);
    private final Connection conn;

    public ProjectIndex(Path dbPath) throws SQLException {
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (Exception e) {
            throw new SQLException("Cannot create index dir", e);
        }
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        conn.createStatement().execute("PRAGMA journal_mode=WAL");
        conn.createStatement().execute("PRAGMA busy_timeout=3000");
        initSchema();
    }

    private void initSchema() throws SQLException {
        try (var st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS files (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    path       TEXT UNIQUE NOT NULL,
                    language   TEXT,
                    size       INTEGER,
                    hash       TEXT,
                    indexed_at TEXT
                )
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS symbols (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_id     INTEGER REFERENCES files(id) ON DELETE CASCADE,
                    name        TEXT NOT NULL,
                    kind        TEXT NOT NULL,
                    signature   TEXT,
                    parent_name TEXT,
                    line_start  INTEGER,
                    line_end    INTEGER
                )
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS dependencies (
                    from_file INTEGER REFERENCES files(id) ON DELETE CASCADE,
                    to_name   TEXT,
                    kind      TEXT
                )
            """);
            // FTS5 для полнотекстового поиска по коду
            st.executeUpdate("""
                CREATE VIRTUAL TABLE IF NOT EXISTS fts
                USING fts5(path, content, content='', tokenize='trigram')
            """);
            // Индексы
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_symbols_name ON symbols(name)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_symbols_kind ON symbols(kind)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_symbols_parent ON symbols(parent_name)");
        }
    }

    // ── Files ─────────────────────────────────────────────────────────────────

    public Optional<String> getFileHash(String path) throws SQLException {
        try (var ps = conn.prepareStatement("SELECT hash FROM files WHERE path = ?")) {
            ps.setString(1, path);
            var rs = ps.executeQuery();
            return rs.next() ? Optional.of(rs.getString("hash")) : Optional.empty();
        }
    }

    public long upsertFile(FileRecord file) throws SQLException {
        try (var ps = conn.prepareStatement("""
            INSERT INTO files(path, language, size, hash, indexed_at)
            VALUES(?,?,?,?,datetime('now'))
            ON CONFLICT(path) DO UPDATE SET
                language=excluded.language,
                size=excluded.size,
                hash=excluded.hash,
                indexed_at=excluded.indexed_at
            RETURNING id
        """)) {
            ps.setString(1, file.path);
            ps.setString(2, file.language);
            ps.setLong(3, file.size);
            ps.setString(4, file.hash);
            var rs = ps.executeQuery();
            return rs.getLong(1);
        }
    }

    public void deleteSymbolsForFile(long fileId) throws SQLException {
        try (var ps = conn.prepareStatement("DELETE FROM symbols WHERE file_id = ?")) {
            ps.setLong(1, fileId);
            ps.executeUpdate();
        }
    }

    // ── Symbols ───────────────────────────────────────────────────────────────

    public void insertSymbol(long fileId, SymbolRecord s) throws SQLException {
        try (var ps = conn.prepareStatement("""
            INSERT INTO symbols(file_id, name, kind, signature, parent_name, line_start, line_end)
            VALUES(?,?,?,?,?,?,?)
        """)) {
            ps.setLong(1, fileId);
            ps.setString(2, s.name);
            ps.setString(3, s.kind.name());
            ps.setString(4, s.signature);
            ps.setString(5, s.parentName);
            ps.setInt(6, s.lineStart);
            ps.setInt(7, s.lineEnd);
            ps.executeUpdate();
        }
    }

    /** Найти символы по имени (частичное совпадение) */
    public List<SymbolRecord> findSymbols(String name) throws SQLException {
        var results = new ArrayList<SymbolRecord>();
        try (var ps = conn.prepareStatement("""
            SELECT s.*, f.path FROM symbols s
            JOIN files f ON f.id = s.file_id
            WHERE s.name LIKE ? LIMIT 50
        """)) {
            ps.setString(1, "%" + name + "%");
            var rs = ps.executeQuery();
            while (rs.next()) results.add(mapSymbol(rs));
        }
        return results;
    }

    /** Все символы файла */
    public List<SymbolRecord> getSymbolsForFile(String path) throws SQLException {
        var results = new ArrayList<SymbolRecord>();
        try (var ps = conn.prepareStatement("""
            SELECT s.*, f.path FROM symbols s
            JOIN files f ON f.id = s.file_id
            WHERE f.path = ? ORDER BY s.line_start
        """)) {
            ps.setString(1, path);
            var rs = ps.executeQuery();
            while (rs.next()) results.add(mapSymbol(rs));
        }
        return results;
    }

    /** FTS полнотекстовый поиск */
    public List<String> searchContent(String query, int limit) throws SQLException {
        var results = new ArrayList<String>();
        try (var ps = conn.prepareStatement(
                "SELECT path FROM fts WHERE fts MATCH ? LIMIT ?")) {
            ps.setString(1, query);
            ps.setInt(2, limit);
            var rs = ps.executeQuery();
            while (rs.next()) results.add(rs.getString("path"));
        }
        return results;
    }

    /** Вставить контент файла в FTS */
    public void upsertFts(String path, String content) throws SQLException {
        try (var ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO fts(path, content) VALUES(?,?)")) {
            ps.setString(1, path);
            ps.setString(2, content);
            ps.executeUpdate();
        }
    }

    /** Статистика индекса */
    public IndexStats stats() throws SQLException {
        try (var st = conn.createStatement()) {
            var rs = st.executeQuery("SELECT COUNT(*) FROM files");
            int files = rs.getInt(1);
            rs = st.executeQuery("SELECT COUNT(*) FROM symbols");
            int symbols = rs.getInt(1);
            return new IndexStats(files, symbols);
        }
    }

    public record IndexStats(int files, int symbols) {}

    private SymbolRecord mapSymbol(ResultSet rs) throws SQLException {
        var s = new SymbolRecord();
        s.id         = rs.getLong("id");
        s.name       = rs.getString("name");
        s.kind       = SymbolKind.valueOf(rs.getString("kind"));
        s.signature  = rs.getString("signature");
        s.parentName = rs.getString("parent_name");
        s.lineStart  = rs.getInt("line_start");
        s.lineEnd    = rs.getInt("line_end");
        return s;
    }

    @Override
    public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}