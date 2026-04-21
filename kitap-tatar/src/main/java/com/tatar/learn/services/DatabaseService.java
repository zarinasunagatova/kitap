package com.tatar.learn.services;

import com.tatar.learn.models.Word;
import com.tatar.learn.utils.JsonUtils;
import com.tatar.learn.utils.WordsExport;

import java.sql.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.LocalDate;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.*;

public class DatabaseService {
    private static final Logger LOGGER = Logger.getLogger(DatabaseService.class.getName());
    private static volatile DatabaseService instance;
    private static volatile boolean initFailed = false;
    private static String initErrorMessage = null;
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Connection connection;
    private final Object connectionLock = new Object();
    
    private DatabaseService() throws SQLException {
        connect();
        createTables();
        LOGGER.info("Database service initialized");
    }
    
    public static DatabaseService getInstance() throws SQLException {
        if (initFailed) {
            throw new SQLException("Database initialization failed previously: " + initErrorMessage);
        }
        
        if (instance == null) {
            synchronized (DatabaseService.class) {
                if (instance == null) {
                    try {
                        instance = new DatabaseService();
                    } catch (SQLException e) {
                        initFailed = true;
                        initErrorMessage = e.getMessage();
                        LOGGER.log(Level.SEVERE, "Failed to initialize DatabaseService", e);
                        throw e; // Пробрасываем дальше
                    }
                }
            }
        }
        return instance;
    }

    public static boolean isInitialized() {
        return instance != null && !initFailed;
    }

    public static String getInitErrorMessage() {
        return initErrorMessage;
    }
    
    public boolean isHealthy() {
        lock.readLock().lock();
        try {
            Connection conn = getValidConnection();
            if (conn == null || conn.isClosed()) {
                return false;
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.warning("Health check failed: " + e.getMessage());
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public static synchronized void reset() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
        initFailed = false;
        initErrorMessage = null;
        LOGGER.info("DatabaseService reset");
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
                examples TEXT,
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
     // НЕ загружаем слова автоматически - они будут добавляться при прохождении уроков
	    if (wordCount == 0) {
	    	System.out.println("=== Database is empty. Words will be added as lessons are completed ===");
	        //loadWordsFromFile(); // ЗАКОММЕНТИРОВАНО
	    }
        
        migrateIfNeeded();
    }
    
    private void loadWordsFromFile() {
        System.out.println("=== loadWordsFromFile() started ===");
        
        // Пробуем разные места для файла
        String[] possiblePaths = {
            "words.json",
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
            
            com.google.gson.JsonArray categoriesArray = jsonObject.getAsJsonArray("categories");
            if (categoriesArray != null && categoriesArray.size() > 0) {
                System.out.println("Found " + categoriesArray.size() + " categories in JSON");
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
                String sql = "INSERT INTO words (tatar, russian, category, examples) VALUES (?, ?, ?, ?)";
                String progressSql = "INSERT INTO user_progress (word_id) VALUES (?)";
                
                try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement progressPstmt = conn.prepareStatement(progressSql)) {
                    
                    int added = 0;
                    for (int i = 0; i < wordsArray.size(); i++) {
                        com.google.gson.JsonObject wordObj = wordsArray.get(i).getAsJsonObject();
                        
                        String tatar = wordObj.get("tatar").getAsString();
                        String russian = wordObj.get("russian").getAsString();
                        String category = wordObj.has("category") ? wordObj.get("category").getAsString() : "Общее";
                        
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
                                int wordId = rs.getInt(1);
                                progressPstmt.setInt(1, wordId);
                                progressPstmt.executeUpdate();
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
    
    /**
     * Экспортирует все слова в JSON файл с сохранением примеров
     */
    public void exportAllWords(String filePath) throws IOException {
        lock.readLock().lock();
        try {
            List<Word> words = getAllWords();
            
            // Создаем объект для экспорта
            WordsExport export = new WordsExport();
            export.setWords(words);
            export.setExportDate(LocalDate.now());
            export.setTotalWords(words.size());
            
            // Собираем уникальные категории
            Set<String> categorySet = new HashSet<>();
            for (Word word : words) {
                if (word.getCategory() != null && !word.getCategory().isEmpty()) {
                    categorySet.add(word.getCategory());
                }
            }
            export.setCategories(new ArrayList<>(categorySet));
            
            // Сохраняем в JSON
            JsonUtils.saveToFile(export, filePath);
            
            LOGGER.info("Exported " + words.size() + " words to " + filePath);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to export words", e);
            throw new IOException("Export failed: " + e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
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
                        examples TEXT,
                        created_at INTEGER DEFAULT (strftime('%s', 'now'))
                    )
                """);
                stmt.execute("""
                    INSERT INTO words_new (id, tatar, russian, category, examples, created_at)
                    SELECT id, tatar, russian, category, examples, created_at FROM words
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
                String sql = "INSERT INTO words (tatar, russian, category, examples) VALUES (?, ?, ?, ?)";
                String progressSql = "INSERT INTO user_progress (word_id) VALUES (?)";
                
                try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement progressPstmt = conn.prepareStatement(progressSql)) {
                    
                    for (String[] sample : samples) {
                        pstmt.setString(1, sample[0]);
                        pstmt.setString(2, sample[1]);
                        pstmt.setString(3, sample[2]);
                        pstmt.setString(4, null);
                        pstmt.executeUpdate();
                        
                        try (ResultSet rs = pstmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                int wordId = rs.getInt(1);
                                progressPstmt.setInt(1, wordId);
                                progressPstmt.executeUpdate();
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
    
    /**
     * Добавляет слово и инициализирует прогресс в одной транзакции
     */
    public void addWord(Word word) {
        lock.writeLock().lock();
        try {
            Connection conn = getValidConnection();
            conn.setAutoCommit(false);
            
            try {
                String sql = "INSERT INTO words (tatar, russian, category, examples) VALUES (?, ?, ?, ?)";
                
                try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setString(1, word.getTatar());
                    pstmt.setString(2, word.getRussian());
                    pstmt.setString(3, word.getCategory());
                    
                    // Сохраняем examples как JSON
                    String examplesJson = null;
                    if (word.getExamples() != null && !word.getExamples().isEmpty()) {
                        com.google.gson.JsonArray jsonArray = new com.google.gson.JsonArray();
                        for (String example : word.getExamples()) {
                            jsonArray.add(example);
                        }
                        examplesJson = jsonArray.toString();
                    }
                    pstmt.setString(4, examplesJson);
                    pstmt.executeUpdate();
                    
                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            int wordId = rs.getInt(1);
                            word.setId(wordId);
                            
                            // Инициализируем прогресс в той же транзакции
                            try (PreparedStatement progressPstmt = conn.prepareStatement(
                                    "INSERT INTO user_progress (word_id) VALUES (?)")) {
                                progressPstmt.setInt(1, wordId);
                                progressPstmt.executeUpdate();
                            }
                        } else {
                            throw new SQLException("Failed to get generated key for word");
                        }
                    }
                }
                
                conn.commit();
                LOGGER.info("Word added successfully with ID: " + word.getId());
                
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Failed to add word, transaction rolled back", e);
                throw e;
            } finally {
                conn.setAutoCommit(true);
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
                    words.add(mapRowToWord(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get all words", e);
        } finally {
            lock.readLock().unlock();
        }
        return words;
    }
    
    /**
     * Обновляет слово и прогресс в одной транзакции
     */
    public void updateWord(Word word) {
        lock.writeLock().lock();
        try {
            Connection conn = getValidConnection();
            conn.setAutoCommit(false);
            
            try {
                String updateWord = "UPDATE words SET tatar = ?, russian = ?, category = ?, examples = ? WHERE id = ?";
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
                    
                    // Сохраняем examples как JSON
                    String examplesJson = null;
                    if (word.getExamples() != null && !word.getExamples().isEmpty()) {
                        com.google.gson.JsonArray jsonArray = new com.google.gson.JsonArray();
                        for (String example : word.getExamples()) {
                            jsonArray.add(example);
                        }
                        examplesJson = jsonArray.toString();
                    }
                    pstmt1.setString(4, examplesJson);
                    pstmt1.setInt(5, word.getId());
                    int wordsUpdated = pstmt1.executeUpdate();
                    
                    if (wordsUpdated == 0) {
                        throw new SQLException("Word with ID " + word.getId() + " not found");
                    }
                    
                    pstmt2.setInt(1, word.getTimesCorrect());
                    pstmt2.setInt(2, word.getTimesWrong());
                    pstmt2.setString(3, word.getLastReviewed() != null ? word.getLastReviewed().toString() : null);
                    pstmt2.setDouble(4, word.getEaseFactor());
                    pstmt2.setInt(5, word.getId());
                    pstmt2.executeUpdate();
                }
                
                conn.commit();
                LOGGER.info("Word updated successfully with ID: " + word.getId());
                
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Failed to update word, transaction rolled back", e);
                throw e;
            } finally {
                conn.setAutoCommit(true);
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
            // Из-за FOREIGN KEY ON DELETE CASCADE, прогресс удалится автоматически
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
    
    /**
     * Сохраняет попытку ответа в одной транзакции
     */
    public void saveWordAttempt(int wordId, boolean isCorrect) {
        lock.writeLock().lock();
        try {
            Connection conn = getValidConnection();
            conn.setAutoCommit(false);
            
            try {
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
                
                conn.commit();
                
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Failed to save attempt, transaction rolled back", e);
                throw e;
            } finally {
                conn.setAutoCommit(true);
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