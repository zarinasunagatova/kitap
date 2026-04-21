package com.tatar.learn.services;

import com.google.gson.reflect.TypeToken;
import com.tatar.learn.models.*;
import com.tatar.learn.utils.JsonUtils;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.prefs.Preferences;

public class TopicsService {
    private static TopicsService instance;
    private Preferences prefs;
    private List<Topic> allTopics;
    private Set<String> completedTopicNames;
    private DatabaseService dbService;
    
    private TopicsService() {
        prefs = Preferences.userNodeForPackage(TopicsService.class);
        loadTopicsFromJson();
        loadProgress();
        initDatabaseService();
    }
    
    public static TopicsService getInstance() {
        if (instance == null) {
            instance = new TopicsService();
        }
        return instance;
    }
    
    private void initDatabaseService() {
        try {
            dbService = DatabaseService.getInstance();
        } catch (Exception e) {
            System.err.println("Failed to init DatabaseService: " + e.getMessage());
        }
    }
    
    private void loadTopicsFromJson() {
        allTopics = new ArrayList<>();
        
        // Загружаем слова из words.json
        Map<String, List<Word>> wordsByCategory = loadWordsFromJson();
        
        // Загружаем грамматику из grammar.json
        Map<String, GrammarRule> grammarByCategory = loadGrammarFromJson();
        
        // Загружаем упражнения из exercises.json
        Map<String, List<GrammarExercise>> exercisesByCategory = loadExercisesFromJson();
        
        // Порядок категорий
        String[] categoriesOrder = {"Приветствия", "Вежливость", "Семья", "Числа", "Цвета", 
                                    "Еда", "Животные", "Одежда", "Дом", "Время", "Погода", "Человек"};
        
        // Карта иконок для категорий
        Map<String, String> categoryIcons = getCategoryIcons();
        Map<String, String> categoryTatarNames = getCategoryTatarNames();
        
        int order = 1;
        for (String categoryName : categoriesOrder) {
            List<Word> categoryWords = wordsByCategory.get(categoryName);
            if (categoryWords != null && !categoryWords.isEmpty()) {
                Topic topic = new Topic(order, categoryName, 
                    categoryTatarNames.getOrDefault(categoryName, categoryName),
                    categoryIcons.getOrDefault(categoryName, "📚"), order);
                topic.getWords().addAll(categoryWords);
                
                // Добавляем грамматику для темы
                GrammarRule grammar = grammarByCategory.get(categoryName);
                if (grammar != null) {
                    topic.setGrammar(grammar);
                }
                
                // Добавляем упражнения для темы
                List<GrammarExercise> exercises = exercisesByCategory.get(categoryName);
                if (exercises != null) {
                    topic.getExercises().addAll(exercises);
                }
                
                allTopics.add(topic);
                order++;
            }
        }
        
        System.out.println("=== Загружено тем из JSON: " + allTopics.size() + " ===");
        for (Topic topic : allTopics) {
            System.out.println("  - " + topic.getName() + ": " + topic.getWords().size() + " слов, " +
                (topic.getGrammar() != null ? "грамматика есть" : "грамматики нет") + ", " +
                topic.getExercises().size() + " упражнений");
        }
    }
    
    private Map<String, List<Word>> loadWordsFromJson() {
        Map<String, List<Word>> wordsByCategory = new HashMap<>();
        
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/words/words.json");
            if (is == null) {
                is = getClass().getClassLoader().getResourceAsStream("words.json");
            }
            
            if (is != null) {
                String jsonContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                Type type = new TypeToken<WordsJsonFormat>(){}.getType();
                WordsJsonFormat data = JsonUtils.fromJson(jsonContent, type);
                
                if (data != null && data.getWords() != null) {
                    for (WordJson wj : data.getWords()) {
                        String category = wj.getCategory();
                        if (category == null || category.isEmpty()) {
                            category = "Общее";
                        }
                        
                        Word word = new Word(
                            wj.getTatar(),
                            wj.getRussian(),
                            category,
                            wj.getExamples() != null ? wj.getExamples() : new ArrayList<>(),
                            1
                        );
                        
                        wordsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(word);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load words from JSON: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("Загружено слов из JSON: " + 
            wordsByCategory.values().stream().mapToInt(List::size).sum());
        return wordsByCategory;
    }

    private Map<String, GrammarRule> loadGrammarFromJson() {
        Map<String, GrammarRule> grammarByCategory = new HashMap<>();
        
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/grammar/grammar.json");
            if (is == null) {
                is = getClass().getClassLoader().getResourceAsStream("grammar.json");
            }
            
            if (is != null) {
                String jsonContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                Type type = new TypeToken<GrammarJsonFormat>(){}.getType();
                GrammarJsonFormat data = JsonUtils.fromJson(jsonContent, type);
                
                if (data != null && data.getRules() != null) {
                    for (GrammarRuleJson grj : data.getRules()) {
                        GrammarRule rule = new GrammarRule();
                        rule.setId(grj.getId());
                        rule.setTitle(grj.getTitle());
                        rule.setCategory(grj.getCategory());
                        rule.setExplanation(grj.getExplanation());
                        rule.setExamples(formatExamples(grj.getExamples()));
                        grammarByCategory.put(grj.getCategory(), rule);
                        
                        System.out.println("  Загружено правило: " + grj.getTitle() + " (" + grj.getCategory() + ")");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load grammar from JSON: " + e.getMessage());
            e.printStackTrace();
        }
        
        return grammarByCategory;
    }

    private Map<String, List<GrammarExercise>> loadExercisesFromJson() {
        Map<String, List<GrammarExercise>> exercisesByCategory = new HashMap<>();
        
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/grammar/exercises.json");
            if (is == null) {
                is = getClass().getClassLoader().getResourceAsStream("exercises.json");
            }
            
            if (is != null) {
                String jsonContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                Type type = new TypeToken<ExercisesJsonFormat>(){}.getType();
                ExercisesJsonFormat data = JsonUtils.fromJson(jsonContent, type);
                
                if (data != null && data.getExercises() != null) {
                    for (ExerciseJson ej : data.getExercises()) {
                        GrammarExercise exercise = new GrammarExercise();
                        exercise.setId(ej.getId());
                        exercise.setRuleId(ej.getRuleId());
                        exercise.setCategory(ej.getCategory());
                        exercise.setType(ej.getType());
                        exercise.setQuestion(ej.getQuestion());
                        exercise.setCorrectAnswer(ej.getCorrectAnswer());
                        exercise.setExplanation(ej.getExplanation());
                        if (ej.getOptions() != null) {
                            exercise.setOptions(ej.getOptions());
                        }
                        
                        exercisesByCategory.computeIfAbsent(ej.getCategory(), k -> new ArrayList<>()).add(exercise);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load exercises from JSON: " + e.getMessage());
            e.printStackTrace();
        }
        
        int total = exercisesByCategory.values().stream().mapToInt(List::size).sum();
        System.out.println("Загружено упражнений из JSON: " + total);
        return exercisesByCategory;
    }
    
    private String formatExamples(List<ExamplePair> examples) {
        if (examples == null || examples.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ExamplePair ex : examples) {
            sb.append(ex.getTatar()).append(" — ").append(ex.getRussian()).append("\n");
        }
        return sb.toString();
    }
    
    private Map<String, String> getCategoryIcons() {
        Map<String, String> icons = new HashMap<>();
        icons.put("Приветствия", "👋");
        icons.put("Вежливость", "💝");
        icons.put("Семья", "👨‍👩‍👧‍👦");
        icons.put("Числа", "🔢");
        icons.put("Цвета", "🎨");
        icons.put("Еда", "🍲");
        icons.put("Животные", "🐕");
        icons.put("Одежда", "👕");
        icons.put("Дом", "🏠");
        icons.put("Время", "⏰");
        icons.put("Погода", "☀️");
        icons.put("Человек", "👤");
        return icons;
    }
    
    private Map<String, String> getCategoryTatarNames() {
        Map<String, String> tatarNames = new HashMap<>();
        tatarNames.put("Приветствия", "Сәламләшү");
        tatarNames.put("Вежливость", "Әдәплелек");
        tatarNames.put("Семья", "Гаилә");
        tatarNames.put("Числа", "Саннар");
        tatarNames.put("Цвета", "Төсләр");
        tatarNames.put("Еда", "Ашамлык");
        tatarNames.put("Животные", "Хайваннар");
        tatarNames.put("Одежда", "Кием");
        tatarNames.put("Дом", "Өй");
        tatarNames.put("Время", "Вакыт");
        tatarNames.put("Погода", "Һава");
        tatarNames.put("Человек", "Кеше");
        return tatarNames;
    }
    
    private void loadProgress() {
        completedTopicNames = new HashSet<>();
        String saved = prefs.get("completed_topics", "");
        if (!saved.isEmpty()) {
            completedTopicNames.addAll(Arrays.asList(saved.split(",")));
        }
        
        for (int i = 0; i < allTopics.size(); i++) {
            Topic topic = allTopics.get(i);
            topic.setCompleted(completedTopicNames.contains(topic.getName()));
            
            if (i == 0) {
                topic.setUnlocked(true);
            } else {
                Topic prevTopic = allTopics.get(i - 1);
                topic.setUnlocked(prevTopic.isCompleted());
            }
        }
    }
    
    public void saveProgress() {
        String saved = String.join(",", completedTopicNames);
        prefs.put("completed_topics", saved);
    }
    
    public List<Topic> getAllTopics() {
        return allTopics;
    }
    
    public List<Topic> getUnlockedTopics() {
        List<Topic> unlocked = new ArrayList<>();
        for (Topic topic : allTopics) {
            if (topic.isUnlocked()) {
                unlocked.add(topic);
            }
        }
        return unlocked;
    }
    
    public Topic getTopicById(int id) {
        for (Topic topic : allTopics) {
            if (topic.getId() == id) {
                return topic;
            }
        }
        return null;
    }
    
    public Topic getTopicByName(String name) {
        for (Topic topic : allTopics) {
            if (topic.getName().equals(name)) {
                return topic;
            }
        }
        return null;
    }
    
    public void completeTopic(String topicName) {
        if (!completedTopicNames.contains(topicName)) {
            completedTopicNames.add(topicName);
            saveProgress();
            
            for (int i = 0; i < allTopics.size(); i++) {
                Topic topic = allTopics.get(i);
                if (topic.getName().equals(topicName)) {
                    topic.setCompleted(true);
                    if (i + 1 < allTopics.size()) {
                        allTopics.get(i + 1).setUnlocked(true);
                    }
                    break;
                }
            }
            
            // Синхронизируем слова темы с основной БД
            syncTopicWordsToDatabase(topicName);
        }
    }
    /**
     * Принудительно синхронизирует все слова из всех тем с БД (для отладки)
     */
    public void syncAllWordsToDatabase() {
        if (dbService == null) return;
        
        for (Topic topic : allTopics) {
            for (Word word : topic.getWords()) {
                boolean exists = false;
                for (Word existing : dbService.getAllWords()) {
                    if (existing.getTatar().equalsIgnoreCase(word.getTatar())) {
                        exists = true;
                        word.setId(existing.getId());
                        break;
                    }
                }
                if (!exists) {
                    dbService.addWord(word);
                    System.out.println("✅ Добавлено слово в БД: " + word.getTatar());
                }
            }
        }
    }
    private void syncTopicWordsToDatabase(String topicName) {
        if (dbService == null) return;
        
        for (Topic topic : allTopics) {
            if (topic.getName().equals(topicName)) {
                for (Word word : topic.getWords()) {
                    boolean exists = false;
                    for (Word existing : dbService.getAllWords()) {
                        if (existing.getTatar().equalsIgnoreCase(word.getTatar())) {
                            exists = true;
                            word.setId(existing.getId()); // ← ВАЖНО: копируем ID из существующего слова
                            word.setTimesCorrect(existing.getTimesCorrect());
                            word.setTimesWrong(existing.getTimesWrong());
                            word.setLastReviewed(existing.getLastReviewed());
                            word.setEaseFactor(existing.getEaseFactor());
                            break;
                        }
                    }
                    if (!exists) {
                        dbService.addWord(word);
                        // После addWord у слова появится ID
                        System.out.println("Добавлено слово в БД: " + word.getTatar() + " (ID: " + word.getId() + ")");
                    }
                }
                break;
            }
        }
    }
    
    public List<String> getCompletedTopicNames() {
        return new ArrayList<>(completedTopicNames);
    }
    
    public List<Word> getAllLearnedWords() {
        List<Word> learned = new ArrayList<>();
        for (Topic topic : allTopics) {
            if (completedTopicNames.contains(topic.getName())) {
                learned.addAll(topic.getWords());
            }
        }
        return learned;
    }
    
    public boolean isTopicCompleted(String topicName) {
        return completedTopicNames.contains(topicName);
    }
    
    public int getTotalProgress() {
        if (allTopics.isEmpty()) return 0;
        int completed = 0;
        for (Topic topic : allTopics) {
            if (topic.isCompleted()) completed++;
        }
        return (completed * 100) / allTopics.size();
    }
    
    // ========== ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ ДЛЯ JSON ==========
    
    static class WordsJsonFormat {
        private List<String> categories;
        private List<WordJson> words;
        public List<String> getCategories() { return categories; }
        public List<WordJson> getWords() { return words; }
    }
    
    static class WordJson {
        private int id;
        private String tatar;
        private String russian;
        private String category;
        private List<String> examples;
        public String getTatar() { return tatar; }
        public String getRussian() { return russian; }
        public String getCategory() { return category; }
        public List<String> getExamples() { return examples; }
    }
    
    static class GrammarJsonFormat {
        private List<String> categories;
        private List<GrammarRuleJson> rules;
        public List<GrammarRuleJson> getRules() { return rules; }
    }
    
    static class GrammarRuleJson {
        private int id;
        private String title;
        private String category;
        private String level;
        private String explanation;
        private List<ExamplePair> examples;
        private String notes;
        private List<Integer> exercises;
        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getCategory() { return category; }
        public String getExplanation() { return explanation; }
        public List<ExamplePair> getExamples() { return examples; }
    }
    
    static class ExamplePair {
        private String tatar;
        private String russian;
        public String getTatar() { return tatar; }
        public String getRussian() { return russian; }
    }
    
    static class ExercisesJsonFormat {
        private List<ExerciseJson> exercises;
        public List<ExerciseJson> getExercises() { return exercises; }
    }
    
    static class ExerciseJson {
        private int id;
        private int ruleId;
        private String category;
        private String type;
        private String question;
        private List<String> options;
        private String correctAnswer;
        private String explanation;
        public int getId() { return id; }
        public int getRuleId() { return ruleId; }
        public String getCategory() { return category; }
        public String getType() { return type; }
        public String getQuestion() { return question; }
        public List<String> getOptions() { return options; }
        public String getCorrectAnswer() { return correctAnswer; }
        public String getExplanation() { return explanation; }
    }
    
    /**
     * Возвращает все слова из ПРОЙДЕННЫХ тем (для словаря и карточек)
     */
    public List<Word> getWordsFromCompletedTopics() {
        List<Word> result = new ArrayList<>();
        for (Topic topic : allTopics) {
            if (topic.isCompleted() || completedTopicNames.contains(topic.getName())) {
                result.addAll(topic.getWords());
            }
        }
        return result;
    }

    /**
     * Возвращает все грамматические правила из ПРОЙДЕННЫХ тем
     */
    public List<GrammarRule> getGrammarFromCompletedTopics() {
        List<GrammarRule> result = new ArrayList<>();
        for (Topic topic : allTopics) {
            if (topic.isCompleted() || completedTopicNames.contains(topic.getName())) {
                GrammarRule grammar = topic.getGrammar();
                if (grammar != null) {
                    result.add(grammar);
                }
            }
        }
        return result;
    }

    /**
     * Проверяет, есть ли слово в пройденных темах
     */
    public boolean isWordUnlocked(Word word) {
        for (Topic topic : allTopics) {
            if (topic.isCompleted() || completedTopicNames.contains(topic.getName())) {
                for (Word w : topic.getWords()) {
                    if (w.getTatar().equalsIgnoreCase(word.getTatar())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Сбрасывает весь прогресс (для отладки)
     */
    public void resetAllProgress() {
        completedTopicNames.clear();
        saveProgress();
        
        for (int i = 0; i < allTopics.size(); i++) {
            Topic topic = allTopics.get(i);
            topic.setCompleted(false);
            topic.setUnlocked(i == 0);
        }
    }
}