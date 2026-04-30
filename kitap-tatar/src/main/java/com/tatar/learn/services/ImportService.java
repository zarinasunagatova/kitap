package com.tatar.learn.services;

import com.google.gson.reflect.TypeToken;
import com.tatar.learn.models.Word;
import com.tatar.learn.utils.JsonUtils;
import com.tatar.learn.utils.WordsExport;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportService {
    private static volatile ImportService instance;
    private static volatile boolean initFailed = false;
    private static volatile String initErrorMessage = null;
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private DatabaseService dbService;
    private boolean databaseAvailable = false;
    
    private ImportService() throws SQLException {
        try {
            dbService = DatabaseService.getInstance();
            
            if (dbService == null) {
                throw new SQLException("DatabaseService.getInstance() returned null");
            }
            
            if (!dbService.isHealthy()) {
                throw new SQLException("Database is not healthy");
            }
            
            databaseAvailable = true;
            log.info("✅ ImportService initialized successfully");
            
        } catch (SQLException e) {
            initFailed = true;
            initErrorMessage = e.getMessage();
            log.error("❌ ImportService initialization failed: " + e.getMessage());
            throw e;
        }
    }
    
    public static ImportService getInstance() throws SQLException {
        if (initFailed) {
            throw new SQLException("ImportService initialization failed previously: " + initErrorMessage);
        }
        
        if (instance == null) {
            synchronized (ImportService.class) {
                if (instance == null) {
                    instance = new ImportService();
                }
            }
        }
        return instance;
    }
    
    public static boolean isInitialized() {
        return instance != null && !initFailed && instance.databaseAvailable;
    }
    
    public static void reset() {
        synchronized (ImportService.class) {
            instance = null;
            initFailed = false;
            initErrorMessage = null;
        }
    }
    
    private void checkDatabaseAvailable() throws SQLException {
        if (!databaseAvailable) {
            throw new SQLException("Database service is not available");
        }
    }
    
    /**
     * Импорт слов из JSON файла с проверкой дубликатов
     */
    public ImportResult importFromJson(String filePath) {
        ImportResult result = new ImportResult();
        
        try {
            checkDatabaseAvailable();
            
            // Загружаем данные из JSON
            Type listType = new TypeToken<List<Word>>(){}.getType();
            List<Word> importedWords = JsonUtils.loadListFromFile(filePath, listType);
            
            if (importedWords == null || importedWords.isEmpty()) {
                result.setMessage("Файл пуст или не содержит слов");
                return result;
            }
            
            // Получаем существующие слова из БД
            List<Word> existingWords = dbService.getAllWords();
            Set<String> existingTatars = new HashSet<>();
            for (Word w : existingWords) {
                existingTatars.add(w.getTatar().toLowerCase().trim());
            }
            
            int added = 0;
            int skipped = 0;
            
            // Добавляем только новые слова
            for (Word word : importedWords) {
                String tatar = word.getTatar().toLowerCase().trim();
                
                if (!existingTatars.contains(tatar)) {
                    Word newWord = new Word(
                        word.getTatar(),
                        word.getRussian(),
                        word.getCategory() != null ? word.getCategory() : "Общее",
                        word.getExamples() != null ? word.getExamples() : new ArrayList<>()
                    );
                    
                    dbService.addWord(newWord);
                    added++;
                    existingTatars.add(tatar);
                } else {
                    skipped++;
                }
            }
            
            result.setSuccess(true);
            result.setAdded(added);
            result.setSkipped(skipped);
            result.setMessage(String.format("✅ Добавлено: %d, Пропущено (дубликаты): %d", added, skipped));
            
        } catch (SQLException e) {
            result.setMessage("❌ Ошибка базы данных: " + e.getMessage());
            log.error("Database error in importFromJson: " + e.getMessage());
        } catch (IOException e) {
            result.setMessage("❌ Ошибка при чтении файла: " + e.getMessage());
            log.error("IO error in importFromJson: " + e.getMessage());
        } catch (Exception e) {
            result.setMessage("❌ Непредвиденная ошибка: " + e.getMessage());
            log.error("Ошибка", e);
        }
        
        return result;
    }
    
    /**
     * Импорт из JSON с категориями
     */
    public ImportResult importFromWordsJson(String filePath) {
        ImportResult result = new ImportResult();
        
        try {
            checkDatabaseAvailable();
            
            WordsJsonFormat data = JsonUtils.loadFromFile(filePath, WordsJsonFormat.class);
            
            if (data == null || data.getWords() == null || data.getWords().isEmpty()) {
                result.setMessage("Файл пуст или не содержит слов");
                return result;
            }
            
            List<Word> existingWords = dbService.getAllWords();
            Set<String> existingTatars = new HashSet<>();
            for (Word w : existingWords) {
                existingTatars.add(w.getTatar().toLowerCase().trim());
            }
            
            int added = 0;
            int skipped = 0;
            
            for (WordJson wordJson : data.getWords()) {
                String tatar = wordJson.getTatar().toLowerCase().trim();
                
                if (!existingTatars.contains(tatar)) {
                    Word newWord = new Word(
                        wordJson.getTatar(),
                        wordJson.getRussian(),
                        wordJson.getCategory() != null ? wordJson.getCategory() : "Общее",
                        wordJson.getExamples() != null ? wordJson.getExamples() : new ArrayList<>()
                    );
                    
                    dbService.addWord(newWord);
                    added++;
                    existingTatars.add(tatar);
                } else {
                    skipped++;
                }
            }
            
            result.setSuccess(true);
            result.setAdded(added);
            result.setSkipped(skipped);
            result.setMessage(String.format("✅ Добавлено: %d, Пропущено (дубликаты): %d", added, skipped));
            
        } catch (SQLException e) {
            result.setMessage("❌ Ошибка базы данных: " + e.getMessage());
            log.error("Database error: " + e.getMessage());
        } catch (IOException e) {
            log.error("❌ Ошибка при чтении файла: " + e.getMessage());
            log.error("IO error: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Полный импорт с сохранением прогресса
     */
    public ImportResult importFullWithProgress(String filePath, boolean overwriteExisting) {
        ImportResult result = new ImportResult();
        
        try {
            checkDatabaseAvailable();
            
            WordsExport exportData = JsonUtils.loadFromFile(filePath, WordsExport.class);
            
            if (exportData == null || exportData.getWords() == null || exportData.getWords().isEmpty()) {
                result.setMessage("Файл пуст или не содержит слов");
                return result;
            }
            
            List<Word> importedWords = exportData.getWords();
            List<Word> existingWords = dbService.getAllWords();
            
            int added = 0;
            int updated = 0;
            int skipped = 0;
            
            for (Word importedWord : importedWords) {
                String tatar = importedWord.getTatar().toLowerCase().trim();
                boolean exists = false;
                Word existingWord = null;
                
                for (Word w : existingWords) {
                    if (w.getTatar().toLowerCase().trim().equals(tatar)) {
                        exists = true;
                        existingWord = w;
                        break;
                    }
                }
                
                if (!exists) {
                    Word newWord = new Word(
                        importedWord.getTatar(),
                        importedWord.getRussian(),
                        importedWord.getCategory() != null ? importedWord.getCategory() : "Общее",
                        importedWord.getExamples() != null ? importedWord.getExamples() : new ArrayList<>()
                    );
                    
                    newWord.setTimesCorrect(importedWord.getTimesCorrect());
                    newWord.setTimesWrong(importedWord.getTimesWrong());
                    newWord.setLastReviewed(importedWord.getLastReviewed());
                    newWord.setEaseFactor(importedWord.getEaseFactor());
                    
                    dbService.addWord(newWord);
                    added++;
                    
                } else if (overwriteExisting) {
                    importedWord.setId(existingWord.getId());
                    
                    existingWord.setTatar(importedWord.getTatar());
                    existingWord.setRussian(importedWord.getRussian());
                    existingWord.setCategory(importedWord.getCategory());
                    existingWord.setExamples(importedWord.getExamples());
                    existingWord.setTimesCorrect(importedWord.getTimesCorrect());
                    existingWord.setTimesWrong(importedWord.getTimesWrong());
                    existingWord.setLastReviewed(importedWord.getLastReviewed());
                    existingWord.setEaseFactor(importedWord.getEaseFactor());
                    
                    dbService.updateWord(existingWord);
                    updated++;
                    
                } else {
                    skipped++;
                }
            }
            
            result.setSuccess(true);
            result.setAdded(added);
            result.setSkipped(skipped);
            result.setMessage(String.format("✅ Добавлено: %d, Обновлено: %d, Пропущено: %d", added, updated, skipped));
            
        } catch (SQLException e) {
            result.setMessage("❌ Ошибка базы данных: " + e.getMessage());
            log.error("Database error: " + e.getMessage());
        } catch (IOException e) {
            result.setMessage("❌ Ошибка при чтении файла: " + e.getMessage());
            log.error("IO error: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Импорт только прогресса
     */
    public ImportResult importProgressOnly(String filePath) {
        ImportResult result = new ImportResult();
        
        try {
            checkDatabaseAvailable();
            
            WordsExport exportData = JsonUtils.loadFromFile(filePath, WordsExport.class);
            
            if (exportData == null || exportData.getWords() == null || exportData.getWords().isEmpty()) {
                result.setMessage("Файл пуст или не содержит слов");
                return result;
            }
            
            List<Word> importedWords = exportData.getWords();
            List<Word> existingWords = dbService.getAllWords();
            
            int updated = 0;
            int notFound = 0;
            
            for (Word importedWord : importedWords) {
                String tatar = importedWord.getTatar().toLowerCase().trim();
                boolean found = false;
                
                for (Word existingWord : existingWords) {
                    if (existingWord.getTatar().toLowerCase().trim().equals(tatar)) {
                        existingWord.setTimesCorrect(importedWord.getTimesCorrect());
                        existingWord.setTimesWrong(importedWord.getTimesWrong());
                        existingWord.setLastReviewed(importedWord.getLastReviewed());
                        existingWord.setEaseFactor(importedWord.getEaseFactor());
                        
                        dbService.updateWord(existingWord);
                        updated++;
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    notFound++;
                }
            }
            
            result.setSuccess(true);
            result.setAdded(updated);
            result.setSkipped(notFound);
            result.setMessage(String.format("✅ Обновлен прогресс: %d слов, Не найдено: %d", updated, notFound));
            
        } catch (SQLException e) {
            result.setMessage("❌ Ошибка базы данных: " + e.getMessage());
            log.error("Database error: " + e.getMessage());
        } catch (IOException e) {
            result.setMessage("❌ Ошибка при чтении файла: " + e.getMessage());
            log.error("IO error: " + e.getMessage());
        }
        
        return result;
    }
    
    // Вспомогательные классы (без изменений)
    public static class WordsJsonFormat {
        private List<String> categories;
        private List<WordJson> words;
        
        public List<String> getCategories() { return categories; }
        public void setCategories(List<String> categories) { this.categories = categories; }
        public List<WordJson> getWords() { return words; }
        public void setWords(List<WordJson> words) { this.words = words; }
    }
    
    public static class WordJson {
        private int id;
        private String tatar;
        private String russian;
        private String category;
        private List<String> examples;
        
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getTatar() { return tatar; }
        public void setTatar(String tatar) { this.tatar = tatar; }
        public String getRussian() { return russian; }
        public void setRussian(String russian) { this.russian = russian; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public List<String> getExamples() { return examples; }
        public void setExamples(List<String> examples) { this.examples = examples; }
    }
    
    public static class ImportResult {
        private boolean success = false;
        private int added = 0;
        private int skipped = 0;
        private String message = "";
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public int getAdded() { return added; }
        public void setAdded(int added) { this.added = added; }
        public int getSkipped() { return skipped; }
        public void setSkipped(int skipped) { this.skipped = skipped; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}