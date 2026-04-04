package com.tatar.learn.services;

import com.google.gson.reflect.TypeToken;
import com.tatar.learn.models.Word;
import com.tatar.learn.utils.JsonUtils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImportService {
    private static ImportService instance;
    private DatabaseService dbService;
    
    private ImportService() {
        dbService = DatabaseService.getInstance();
    }
    
    public static ImportService getInstance() {
        if (instance == null) {
            instance = new ImportService();
        }
        return instance;
    }
    
    /**
     * Импорт слов из JSON файла с проверкой дубликатов
     */
    public ImportResult importFromJson(String filePath) {
        ImportResult result = new ImportResult();
        
        try {
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
                    // Создаем новое слово с правильными полями
                    Word newWord = new Word(
                        word.getTatar(),
                        word.getRussian(),
                        word.getCategory() != null ? word.getCategory() : "Общее"
                    );
                    
                    // Если есть примеры, их нужно сохранить отдельно (дополнительно)
                    
                    dbService.addWord(newWord);
                    added++;
                    existingTatars.add(tatar); // добавляем в множество чтобы не дублировать в этом же импорте
                } else {
                    skipped++;
                }
            }
            
            result.setSuccess(true);
            result.setAdded(added);
            result.setSkipped(skipped);
            result.setMessage(String.format("✅ Добавлено: %d, Пропущено (дубликаты): %d", added, skipped));
            
        } catch (IOException e) {
            result.setMessage("❌ Ошибка при чтении файла: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * Импорт из JSON с категориями (формат как в вашем words.json)
     */
    public ImportResult importFromWordsJson(String filePath) {
        ImportResult result = new ImportResult();
        
        try {
            // Загружаем данные из JSON в специальный формат
            Type type = new TypeToken<WordsJsonFormat>(){}.getType();
            WordsJsonFormat data = JsonUtils.loadFromFile(filePath, WordsJsonFormat.class);
            
            if (data == null || data.getWords() == null || data.getWords().isEmpty()) {
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
            for (WordJson wordJson : data.getWords()) {
                String tatar = wordJson.getTatar().toLowerCase().trim();
                
                if (!existingTatars.contains(tatar)) {
                    Word newWord = new Word(
                        wordJson.getTatar(),
                        wordJson.getRussian(),
                        wordJson.getCategory()
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
            
        } catch (IOException e) {
            result.setMessage("❌ Ошибка при чтении файла: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    // Вспомогательный класс для формата words.json
    public static class WordsJsonFormat {
        private List<String> categories;
        private List<WordJson> words;
        
        public List<String> getCategories() { return categories; }
        public void setCategories(List<String> categories) { this.categories = categories; }
        
        public List<WordJson> getWords() { return words; }
        public void setWords(List<WordJson> words) { this.words = words; }
    }
    
    // Вспомогательный класс для слова с примерами
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
    
    // Класс для результата импорта
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