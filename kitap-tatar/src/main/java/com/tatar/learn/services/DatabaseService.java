package com.tatar.learn.services;

import com.tatar.learn.models.Word;
import com.tatar.learn.utils.JsonUtils;
import java.sql.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.LocalDate;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.*;

public class DatabaseService {
    private static final Logger LOGGER = Logger.getLogger(DatabaseService.class.getName());
    private static DatabaseService instance;
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Connection connection;
    private final Object connectionLock = new Object();
    
    private DatabaseService() {
        try {
            connect();
            createTables();
            LOGGER.info("Database service initialized");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize database", e);
        }
    }
    
    public static DatabaseService getInstance() {
        if (instance == null) {
            synchronized (DatabaseService.class) {
                if (instance == null) {
                    instance = new DatabaseService();
                }
            }
        }
        return instance;
    }
    
    private void connect() throws SQLException {
        File dataDir = new File(System.getProperty("user.home"), ".tatar-learn");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new SQLException("Cannot create data directory: " + dataDir.getAbsolutePath());
        }
        
        String url = "jdbc:sqlite:" + new File(dataDir, "kitap.db").getAbsolutePath();
        
        synchronized (connectionLock) {
            connection = DriverManager.getConnection(url);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
                stmt.execute("PRAGMA synchronous = NORMAL");
            }
        }
        LOGGER.info("Connected to database: " + url);
    }
    
    private Connection getValidConnection() throws SQLException {
        synchronized (connectionLock) {
            if (connection == null || connection.isClosed()) {
                connect();
            }
            return connection;
        }
    }
    
    private void createTables() throws SQLException {
        Connection conn = getValidConnection();
        
        String wordsTable = """
            CREATE TABLE IF NOT EXISTS words (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tatar TEXT NOT NULL,
                russian TEXT NOT NULL,
                category TEXT DEFAULT 'Общее',
                examples TEXT,  -- НОВОЕ ПОЛЕ: храним JSON строку с примерами
                created_at INTEGER DEFAULT (strftime('%s', 'now'))
            )
        """;
        

        
        String progressTable = """
            CREATE TABLE IF NOT EXISTS user_progress (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word_id INTEGER UNIQUE,
                times_correct INTEGER DEFAULT 0,
                times_wrong INTEGER DEFAULT 0,
                last_reviewed TEXT,
                ease_factor REAL DEFAULT 2.5,
                FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
            )
        """;
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(wordsTable);
            stmt.execute(progressTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_words_category ON words(category)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_progress_word ON user_progress(word_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_progress_last_reviewed ON user_progress(last_reviewed)");
        }
        
        // Проверяем, есть ли слова
        int wordCount = 0;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM words")) {
            wordCount = rs.getInt(1);
        }
        
        System.out.println("=== Current word count: " + wordCount + " ===");
        
        // Если слов нет - загружаем
        if (wordCount == 0) {
            System.out.println("=== No words found, loading from words.json ===");
            loadWordsFromFile();
        } else {
            System.out.println("=== Words already exist, skipping load ===");
        }
        
        migrateIfNeeded();
    }
    
    private void loadWordsFromFile() {
        System.out.println("=== loadWordsFromFile() started ===");
        
        // Пробуем разные места для файла
        String[] possiblePaths = {
            "words.json",  // корень проекта
            "./words.json",
            "src/main/resources/data/words/words.json",
            "data/words/words.json",
            "../words.json"
        };
        
        File jsonFile = null;
        for (String path : possiblePaths) {
            File f = new File(path);
            if (f.exists()) {
                jsonFile = f;
                System.out.println("✓ Found words.json at: " + f.getAbsolutePath());
                break;
            }
        }
        
        // Пробуем из resources
        if (jsonFile == null) {
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/words/words.json");
            if (is == null) {
                is = getClass().getClassLoader().getResourceAsStream("words.json");
            }
            if (is != null) {
                System.out.println("✓ Found words.json in resources");
                try {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    importWordsFromJsonContent(content);
                    return;
                } catch (Exception e) {
                    System.err.println("Failed to read from resources: " + e.getMessage());
                }
            }
        }
        
        // Если нашли как файл
        if (jsonFile != null) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
                importWordsFromJsonContent(content);
            } catch (Exception e) {
                System.err.println("Failed to read file: " + e.getMessage());
                addSampleWords();
            }
        } else {
            System.err.println("✗ words.json NOT FOUND anywhere! Using sample words.");
            addSampleWords();
        }
    }
    
    private void importWordsFromJsonContent(String jsonContent) {
        System.out.println("=== Importing words from JSON content ===");
        
        try {
            com.google.gson.JsonObject jsonObject = JsonUtils.fromJson(jsonContent, com.google.gson.JsonObject.class);
            
            // Сохраняем категории (можно использовать позже)
            com.google.gson.JsonArray categoriesArray = jsonObject.getAsJsonArray("categories");
            if (categoriesArray != null && categoriesArray.size() > 0) {
                System.out.println("Found " + categoriesArray.size() + " categories in JSON");
                // Можно сохранить категории в отдельную таблицу или использовать для фильтрации
            }
            
            com.google.gson.JsonArray wordsArray = jsonObject.getAsJsonArray("words");
            
            if (wordsArray == null || wordsArray.size() == 0) {
                System.err.println("No words array in JSON");
                addSampleWords();
                return;
            }
            
            System.out.println("Found " + wordsArray.size() + " words in JSON");
            
            Connection conn = getValidConnection();
            conn.setAutoCommit(false);
            
            try {
                // Обновленный SQL с полем examples
                String sql = "INSERT INTO words (tatar, russian, category, examples) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    int added = 0;
                    for (int i = 0; i < wordsArray.size(); i++) {
                        com.google.gson.JsonObject wordObj = wordsArray.get(i).getAsJsonObject();
                        
                        String tatar = wordObj.get("tatar").getAsString();
                        String russian = wordObj.get("russian").getAsString();
                        String category = wordObj.has("category") ? wordObj.get("category").getAsString() : "Общее";
                        
                        // Сохраняем примеры как JSON строку
                        String examplesJson = null;
                        if (wordObj.has("examples") && wordObj.get("examples").isJsonArray()) {
                            examplesJson = wordObj.get("examples").toString();
                            System.out.println("  Word '" + tatar + "' has " + 
                                wordObj.get("examples").getAsJsonArray().size() + " examples");
                        }
                        
                        pstmt.setString(1, tatar);
                        pstmt.setString(2, russian);
                        pstmt.setString(3, category);
                        pstmt.setString(4, examplesJson);
                        pstmt.executeUpdate();
                        
                        try (ResultSet rs = pstmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                initProgressForWord(conn, rs.getInt(1));
                                added++;
                            }
                        }
                    }
                    conn.commit();
                    System.out.println("✓✓✓ Successfully added " + added + " words from JSON!");
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            
        } catch (Exception e) {
            System.err.println("Failed to import JSON: " + e.getMessage());
            e.printStackTrace();
            addSampleWords();
        }
    }
    
    private Word mapRowToWord(ResultSet rs) throws SQLException {
        Word word = new Word();
        word.setId(rs.getInt("id"));
        word.setTatar(rs.getString("tatar"));
        word.setRussian(rs.getString("russian"));
        word.setCategory(rs.getString("category"));
        word.setTimesCorrect(rs.getInt("times_correct"));
        word.setTimesWrong(rs.getInt("times_wrong"));
        
        // Загружаем примеры из JSON строки
        String examplesJson = rs.getString("examples");
        if (examplesJson != null && !examplesJson.isEmpty()) {
            try {
                com.google.gson.JsonArray examplesArray = com.google.gson.JsonParser.parseString(examplesJson).getAsJsonArray();
                List<String> examples = new ArrayList<>();
                for (int i = 0; i < examplesArray.size(); i++) {
                    examples.add(examplesArray.get(i).getAsString());
                }
                word.setExamples(examples);
            } catch (Exception e) {
                LOGGER.warning("Failed to parse examples for word " + word.getId());
            }
        }
        
        String lastReviewedStr = rs.getString("last_reviewed");
        if (lastReviewedStr != null && !lastReviewedStr.isEmpty()) {
            word.setLastReviewed(LocalDate.parse(lastReviewedStr));
        }
        
        word.setEaseFactor(rs.getDouble("ease_factor"));
        return word;
    }
    
    private void migrateIfNeeded() throws SQLException {
        // Добавляем колонку examples, если её нет
        if (!columnExists("examples")) {
            System.out.println("=== Adding examples column to words table ===");
            try (Statement stmt = getValidConnection().createStatement()) {
                stmt.execute("ALTER TABLE words ADD COLUMN examples TEXT");
            }
        }
        
        if (columnExists("progress")) {
            removeProgressColumn();
        }
    }
    
    private boolean columnExists(String columnName) throws SQLException {
        Connection conn = getValidConnection();
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(null, null, "words", columnName)) {
            return rs.next();
        }
    }
    
    private void removeProgressColumn() throws SQLException {
        lock.writeLock().lock();
        try {
            Connection conn = getValidConnection();
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE words_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        tatar TEXT NOT NULL,
                        russian TEXT NOT NULL,
                        category TEXT DEFAULT 'Общее',
                        created_at INTEGER DEFAULT (strftime('%s', 'now'))
                    )
                """);
                stmt.execute("""
                    INSERT INTO words_new (id, tatar, russian, category, created_at)
                    SELECT id, tatar, russian, category, created_at FROM words
                """);
                stmt.execute("DROP TABLE words");
                stmt.execute("ALTER TABLE words_new RENAME TO words");
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void addSampleWords() {
        System.out.println("=== Adding sample words ===");
        lock.writeLock().lock();
        try {
            Connection conn = getValidConnection();
            String[][] samples = {
                {"Исәнме", "Здравствуйте", "Приветствия"},
                {"Сау бул", "До свидания", "Приветствия"},
                {"Рәхмәт", "Спасибо", "Вежливость"},
            };
            
            conn.setAutoCommit(false);
            try {
                String sql = "INSERT INTO words (tatar, russian, category) VALUES (?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    for (String[] sample : samples) {
                        pstmt.setString(1, sample[0]);
                        pstmt.setString(2, sample[1]);
                        pstmt.setString(3, sample[2]);
                        pstmt.executeUpdate();
                        
                        try (ResultSet rs = pstmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                try (PreparedStatement pstmt2 = conn.prepareStatement(
                                        "INSERT INTO user_progress (word_id) VALUES (?)")) {
                                    pstmt2.setInt(1, rs.getInt(1));
                                    pstmt2.executeUpdate();
                                }
                            }
                        }
                    }
                }
                conn.commit();
                System.out.println("Added sample words");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to add sample words", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ========== ПУБЛИЧНЫЕ МЕТОДЫ ==========
    
    public void addWord(Word word) {
        lock.writeLock().lock();
        try {
            Connection conn = getValidConnection();
            String sql = "INSERT INTO words (tatar, russian, category) VALUES (?, ?, ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, word.getTatar());
                pstmt.setString(2, word.getRussian());
                pstmt.setString(3, word.getCategory());
                pstmt.executeUpdate();
                
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        word.setId(rs.getInt(1));
                        try (PreparedStatement pstmt2 = conn.prepareStatement(
                                "INSERT INTO user_progress (word_id) VALUES (?)")) {
                            pstmt2.setInt(1, word.getId());
                            pstmt2.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to add word", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public List<Word> getAllWords() {
        lock.readLock().lock();
        List<Word> words = new ArrayList<>();
        try {
            Connection conn = getValidConnection();
            String sql = """
                SELECT w.*, 
                       COALESCE(up.times_correct, 0) as times_correct,
                       COALESCE(up.times_wrong, 0) as times_wrong,
                       up.last_reviewed,
                       COALESCE(up.ease_factor, 2.5) as ease_factor
                FROM words w
                LEFT JOIN user_progress up ON w.id = up.word_id
                ORDER BY w.tatar
            """;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    words.add(mapRowToWord(rs));  // ← ИСПОЛЬЗУЕМ mapRowToWord
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get all words", e);
        } finally {
            lock.readLock().unlock();
        }
        return words;
    }
    
    public void updateWord(Word word) {
        lock.writeLock().lock();
        try {
            Connection conn = getValidConnection();
            String updateWord = "UPDATE words SET tatar = ?, russian = ?, category = ? WHERE id = ?";
            String updateProgress = """
                UPDATE user_progress SET 
                    times_correct = ?, times_wrong = ?, last_reviewed = ?, ease_factor = ?
                WHERE word_id = ?
            """;
            
            try (PreparedStatement pstmt1 = conn.prepareStatement(updateWord);
                 PreparedStatement pstmt2 = conn.prepareStatement(updateProgress)) {
                
                pstmt1.setString(1, word.getTatar());
                pstmt1.setString(2, word.getRussian());
                pstmt1.setString(3, word.getCategory());
                pstmt1.setInt(4, word.getId());
                pstmt1.executeUpdate();
                
                pstmt2.setInt(1, word.getTimesCorrect());
                pstmt2.setInt(2, word.getTimesWrong());
                pstmt2.setString(3, word.getLastReviewed() != null ? word.getLastReviewed().toString() : null);
                pstmt2.setDouble(4, word.getEaseFactor());
                pstmt2.setInt(5, word.getId());
                pstmt2.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update word", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void deleteWord(int id) {
        lock.writeLock().lock();
        try {
            Connection conn = getValidConnection();
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM words WHERE id = ?")) {
                pstmt.setInt(1, id);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete word", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void saveWordAttempt(int wordId, boolean isCorrect) {
        lock.writeLock().lock();
        try {
            Connection conn = getValidConnection();
            LocalDate today = LocalDate.now();
            
            String sql = """
                INSERT INTO user_progress (word_id, times_correct, times_wrong, last_reviewed, ease_factor)
                VALUES (?, ?, ?, ?, 2.5)
                ON CONFLICT(word_id) DO UPDATE SET
                    times_correct = times_correct + ?,
                    times_wrong = times_wrong + ?,
                    last_reviewed = ?
            """;
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                if (isCorrect) {
                    pstmt.setInt(1, wordId);
                    pstmt.setInt(2, 1);
                    pstmt.setInt(3, 0);
                    pstmt.setString(4, today.toString());
                    pstmt.setInt(5, 1);
                    pstmt.setInt(6, 0);
                    pstmt.setString(7, today.toString());
                } else {
                    pstmt.setInt(1, wordId);
                    pstmt.setInt(2, 0);
                    pstmt.setInt(3, 1);
                    pstmt.setString(4, today.toString());
                    pstmt.setInt(5, 0);
                    pstmt.setInt(6, 1);
                    pstmt.setString(7, today.toString());
                }
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to save attempt", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public List<String> getCategories() {
        lock.readLock().lock();
        List<String> categories = new ArrayList<>();
        try {
            Connection conn = getValidConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT DISTINCT category FROM words WHERE category IS NOT NULL ORDER BY category")) {
                while (rs.next()) {
                    categories.add(rs.getString("category"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get categories", e);
        } finally {
            lock.readLock().unlock();
        }
        return categories;
    }
    
    public void close() {
        lock.writeLock().lock();
        try {
            synchronized (connectionLock) {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error closing connection", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void initProgressForWord(Connection conn, int wordId) throws SQLException {
        String sql = "INSERT INTO user_progress (word_id) VALUES (?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, wordId);
            pstmt.executeUpdate();
        }
    }
}