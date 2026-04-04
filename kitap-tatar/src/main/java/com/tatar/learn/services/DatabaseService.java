package com.tatar.learn.services;

import com.tatar.learn.models.Word;
import java.sql.*;
import java.io.File;
import java.util.*;
import java.time.LocalDate;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.*;

public class DatabaseService {
    private static final Logger LOGGER = Logger.getLogger(DatabaseService.class.getName());
    private static DatabaseService instance;
    
    // Потокобезопасность
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Соединение с БД
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
        // Сохраняем данные в домашнюю папку пользователя (важно для установщика!)
        File dataDir = new File(System.getProperty("user.home"), ".tatar-learn");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new SQLException("Cannot create data directory: " + dataDir.getAbsolutePath());
        }
        
        String url = "jdbc:sqlite:" + new File(dataDir, "kitap.db").getAbsolutePath();
        
        synchronized (connectionLock) {
            connection = DriverManager.getConnection(url);
            
            // Включаем WAL режим и внешние ключи
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
                stmt.execute("PRAGMA synchronous = NORMAL");
                LOGGER.info("Database PRAGMA configured: WAL mode, foreign keys ON");
            }
        }
        
        LOGGER.info("Connected to database: " + url);
    }
    
    /**
     * Получение валидного соединения (переподключается при необходимости)
     */
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
            
            // Индексы для производительности
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_words_category ON words(category)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_progress_word ON user_progress(word_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_progress_last_reviewed ON user_progress(last_reviewed)");
            
            LOGGER.info("Tables and indexes created/verified");
        }
        
        // Миграция старых данных
        migrateIfNeeded();
        
        // Добавляем примеры слов, если таблица пуста
        if (getWordsCount() == 0) {
            addSampleWords();
        }
    }
    
    private int getWordsCount() {
        lock.readLock().lock();
        try {
            Connection conn = getValidConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM words")) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to count words", e);
            return 0;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private void migrateIfNeeded() throws SQLException {
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
                // Создаём временную таблицу
                stmt.execute("""
                    CREATE TABLE words_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        tatar TEXT NOT NULL,
                        russian TEXT NOT NULL,
                        category TEXT DEFAULT 'Общее',
                        created_at INTEGER DEFAULT (strftime('%s', 'now'))
                    )
                """);
                
                // Копируем данные
                stmt.execute("""
                    INSERT INTO words_new (id, tatar, russian, category, created_at)
                    SELECT id, tatar, russian, category, created_at FROM words
                """);
                
                // Удаляем старую и переименовываем новую
                stmt.execute("DROP TABLE words");
                stmt.execute("ALTER TABLE words_new RENAME TO words");
                conn.commit();
                LOGGER.info("Migration completed: progress column removed");
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
        lock.writeLock().lock();
        try {
            Connection conn = getValidConnection();
            String[][] samples = {
                {"Исәнме", "Здравствуйте", "Приветствия"},
                {"Сау бул", "До свидания", "Приветствия"},
                {"Рәхмәт", "Спасибо", "Вежливость"},
                {"Әйе", "Да", "Основное"},
                {"Юк", "Нет", "Основное"},
                {"Су", "Вода", "Еда"},
                {"Ипи", "Хлеб", "Еда"},
                {"Сөт", "Молоко", "Еда"},
                {"Әти", "Папа", "Семья"},
                {"Әни", "Мама", "Семья"},
                {"Бер", "Один", "Числа"},
                {"Ике", "Два", "Числа"},
                {"Өч", "Три", "Числа"},
                {"Кызыл", "Красный", "Цвета"},
                {"Зәңгәр", "Синий", "Цвета"},
                {"Яшел", "Зеленый", "Цвета"}
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
                                int wordId = rs.getInt(1);
                                initProgressForWord(conn, wordId);
                            }
                        }
                    }
                }
                conn.commit();
                LOGGER.info("Added " + samples.length + " sample words");
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
    
    private void initProgressForWord(Connection conn, int wordId) throws SQLException {
        String sql = "INSERT INTO user_progress (word_id) VALUES (?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, wordId);
            pstmt.executeUpdate();
        }
    }
    
    // ========== ПУБЛИЧНЫЕ МЕТОДЫ (БЕЗ THROWS, СОВМЕСТИМЫЕ) ==========
    
    public void addWord(Word word) {
        lock.writeLock().lock();
        try {
            Connection conn = getValidConnection();
            String sql = "INSERT INTO words (tatar, russian, category) VALUES (?, ?, ?)";
            
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, word.getTatar());
                pstmt.setString(2, word.getRussian());
                pstmt.setString(3, word.getCategory());
                pstmt.executeUpdate();
                
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        word.setId(rs.getInt(1));
                        initProgressForWord(conn, word.getId());
                    }
                }
                conn.commit();
                LOGGER.info("Word added: " + word.getTatar());
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to add word: " + word.getTatar(), e);
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
            // Возвращаем пустой список вместо исключения
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
                    times_correct = ?,
                    times_wrong = ?,
                    last_reviewed = ?,
                    ease_factor = ?
                WHERE word_id = ?
            """;
            
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt1 = conn.prepareStatement(updateWord);
                 PreparedStatement pstmt2 = conn.prepareStatement(updateProgress)) {
                
                pstmt1.setString(1, word.getTatar());
                pstmt1.setString(2, word.getRussian());
                pstmt1.setString(3, word.getCategory());
                pstmt1.setInt(4, word.getId());
                pstmt1.executeUpdate();
                
                pstmt2.setInt(1, word.getTimesCorrect());
                pstmt2.setInt(2, word.getTimesWrong());
                pstmt2.setString(3, word.getLastReviewed() != null ? 
                             word.getLastReviewed().toString() : null);
                pstmt2.setDouble(4, word.getEaseFactor());
                pstmt2.setInt(5, word.getId());
                pstmt2.executeUpdate();
                
                conn.commit();
                LOGGER.info("Word updated: " + word.getTatar());
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update word: " + word.getTatar(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void deleteWord(int id) {
        lock.writeLock().lock();
        try {
            Connection conn = getValidConnection();
            String sql = "DELETE FROM words WHERE id = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, id);
                pstmt.executeUpdate();
                LOGGER.info("Word deleted: id=" + id);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete word: " + id, e);
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
            LOGGER.log(Level.SEVERE, "Failed to save attempt for word: " + wordId, e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public List<Word> searchWords(String query) {
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
                WHERE w.tatar LIKE ? OR w.russian LIKE ?
                ORDER BY w.tatar
                LIMIT 100
            """;
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                String pattern = "%" + query + "%";
                pstmt.setString(1, pattern);
                pstmt.setString(2, pattern);
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        words.add(mapRowToWord(rs));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to search words", e);
        } finally {
            lock.readLock().unlock();
        }
        return words;
    }
    
    public List<String> getCategories() {
        lock.readLock().lock();
        List<String> categories = new ArrayList<>();
        try {
            Connection conn = getValidConnection();
            String sql = "SELECT DISTINCT category FROM words WHERE category IS NOT NULL ORDER BY category";
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
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
    
    public List<Word> getWordsForReview() {
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
                WHERE up.last_reviewed IS NULL 
                   OR date(up.last_reviewed) < date('now', '-' || 
                       CASE COALESCE(up.times_correct, 0)
                           WHEN 0 THEN '1 day'
                           WHEN 1 THEN '3 days'
                           WHEN 2 THEN '7 days'
                           WHEN 3 THEN '14 days'
                           WHEN 4 THEN '30 days'
                           ELSE '60 days'
                       END)
                ORDER BY up.last_reviewed IS NULL DESC, up.last_reviewed ASC
                LIMIT 50
            """;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    words.add(mapRowToWord(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get review words", e);
        } finally {
            lock.readLock().unlock();
        }
        return words;
    }
    
    private Word mapRowToWord(ResultSet rs) throws SQLException {
        Word word = new Word();
        word.setId(rs.getInt("id"));
        word.setTatar(rs.getString("tatar"));
        word.setRussian(rs.getString("russian"));
        word.setCategory(rs.getString("category"));
        word.setTimesCorrect(rs.getInt("times_correct"));
        word.setTimesWrong(rs.getInt("times_wrong"));
        
        String lastReviewedStr = rs.getString("last_reviewed");
        if (lastReviewedStr != null && !lastReviewedStr.isEmpty()) {
            word.setLastReviewed(LocalDate.parse(lastReviewedStr));
        }
        
        word.setEaseFactor(rs.getDouble("ease_factor"));
        return word;
    }
    
    public void close() {
        lock.writeLock().lock();
        try {
            synchronized (connectionLock) {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    LOGGER.info("Database connection closed");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error closing connection", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
} 