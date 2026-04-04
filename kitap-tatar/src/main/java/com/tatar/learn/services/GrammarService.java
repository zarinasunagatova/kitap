package com.tatar.learn.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tatar.learn.models.GrammarExercise;
import com.tatar.learn.models.GrammarRule;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class GrammarService {
    private static GrammarService instance;
    private List<GrammarRule> allRules;
    private List<GrammarExercise> allExercises;
    private Set<String> categories;
    private final Gson gson = new Gson();
    
    // Класс для парсинга JSON структуры упражнений
    private static class ExerciseJson {
        private int id;
        private int ruleId;
        private String type;
        private String question;
        private List<String> options;
        private Integer correct;
        private String correctAnswer;
        private String hint;
        private String explanation;
        private List<Pair> pairs;
        
        public int getId() { return id; }
        public int getRuleId() { return ruleId; }
        public String getType() { return type; }
        public String getQuestion() { return question; }
        public List<String> getOptions() { return options; }
        public Integer getCorrect() { return correct; }
        public String getCorrectAnswer() { return correctAnswer; }
        public String getHint() { return hint; }
        public String getExplanation() { return explanation; }
        public List<Pair> getPairs() { return pairs; }
    }
    
    private static class Pair {
        private String tatar;
        private String russian;
        
        public String getTatar() { return tatar; }
        public String getRussian() { return russian; }
    }
    
    private static class ExercisesWrapper {
        private List<ExerciseJson> exercises;
        
        public List<ExerciseJson> getExercises() { return exercises; }
    }
    
    // Класс для парсинга правил из grammar.json
    private static class GrammarJsonRule {
        private int id;
        private String title;
        private String category;
        private String level;
        private String explanation;
        private List<Example> examples;
        private String notes;
        private List<Integer> exercises;
        
        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getCategory() { return category; }
        public String getLevel() { return level; }
        public String getExplanation() { return explanation; }
        public List<Example> getExamples() { return examples; }
        public String getNotes() { return notes; }
        public List<Integer> getExercises() { return exercises; }
    }
    
    private static class Example {
        private String tatar;
        private String russian;
        
        public String getTatar() { return tatar; }
        public String getRussian() { return russian; }
    }
    
    private static class GrammarWrapper {
        private List<String> categories;
        private List<GrammarJsonRule> rules;
        
        public List<String> getCategories() { return categories; }
        public List<GrammarJsonRule> getRules() { return rules; }
    }
    
    private GrammarService() {
        loadRules();
        loadExercises();
        extractCategories();
    }
    
    public static GrammarService getInstance() {
        if (instance == null) {
            instance = new GrammarService();
        }
        return instance;
    }
    
    private void loadRules() {
        try {
            // Загружаем из ресурсов /data/grammar/grammar.json
            InputStream is = getClass().getResourceAsStream("/data/grammar/grammar.json");
            
            if (is != null) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                GrammarWrapper wrapper = gson.fromJson(json, GrammarWrapper.class);
                
                if (wrapper != null && wrapper.getRules() != null && !wrapper.getRules().isEmpty()) {
                    allRules = new ArrayList<>();
                    for (GrammarJsonRule jsonRule : wrapper.getRules()) {
                        GrammarRule rule = new GrammarRule();
                        rule.setId(jsonRule.getId());
                        rule.setTitle(jsonRule.getTitle());
                        rule.setCategory(jsonRule.getCategory());
                        rule.setExplanation(formatExplanation(jsonRule));
                        rule.setExamples(formatExamples(jsonRule.getExamples()));
                        allRules.add(rule);
                    }
                    System.out.println("Загружено правил из /data/grammar/grammar.json: " + allRules.size());
                    return;
                }
            }
            
            // Если не нашли в ресурсах, пробуем в файловой системе
            java.nio.file.Path rulesPath = java.nio.file.Paths.get("data", "grammar", "grammar.json");
            if (java.nio.file.Files.exists(rulesPath)) {
                String json = new String(java.nio.file.Files.readAllBytes(rulesPath), StandardCharsets.UTF_8);
                GrammarWrapper wrapper = gson.fromJson(json, GrammarWrapper.class);
                
                if (wrapper != null && wrapper.getRules() != null && !wrapper.getRules().isEmpty()) {
                    allRules = new ArrayList<>();
                    for (GrammarJsonRule jsonRule : wrapper.getRules()) {
                        GrammarRule rule = new GrammarRule();
                        rule.setId(jsonRule.getId());
                        rule.setTitle(jsonRule.getTitle());
                        rule.setCategory(jsonRule.getCategory());
                        rule.setExplanation(formatExplanation(jsonRule));
                        rule.setExamples(formatExamples(jsonRule.getExamples()));
                        allRules.add(rule);
                    }
                    System.out.println("Загружено правил из data/grammar/grammar.json: " + allRules.size());
                    return;
                }
            }
            
            // Если не нашли, создаем дефолтные
            System.out.println("Файл grammar.json не найден, использую дефолтные правила");
            createDefaultRules();
            
        } catch (Exception e) {
            System.err.println("Ошибка загрузки грамматики: " + e.getMessage());
            e.printStackTrace();
            createDefaultRules();
        }
    }
    
    private String formatExplanation(GrammarJsonRule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append(rule.getExplanation());
        if (rule.getNotes() != null && !rule.getNotes().isEmpty()) {
            sb.append("\n\nПримечание: ").append(rule.getNotes());
        }
        return sb.toString();
    }
    
    private String formatExamples(List<Example> examples) {
        if (examples == null || examples.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Example ex : examples) {
            sb.append("• ").append(ex.getTatar()).append(" — ").append(ex.getRussian()).append("\n");
        }
        return sb.toString();
    }
    
    private void loadExercises() {
        try {
            // Загружаем из ресурсов /data/grammar/exercises.json
            InputStream is = getClass().getResourceAsStream("/data/grammar/exercises.json");
            
            if (is != null) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                ExercisesWrapper wrapper = gson.fromJson(json, ExercisesWrapper.class);
                
                if (wrapper != null && wrapper.getExercises() != null && !wrapper.getExercises().isEmpty()) {
                    allExercises = convertExercises(wrapper.getExercises());
                    System.out.println("Загружено упражнений из /data/grammar/exercises.json: " + allExercises.size());
                    
                    // Выводим список загруженных упражнений для проверки
                    for (GrammarExercise ex : allExercises) {
                        String shortQuestion = ex.getQuestion().length() > 40 ? 
                            ex.getQuestion().substring(0, 40) + "..." : ex.getQuestion();
                        System.out.println("  - ID: " + ex.getId() + 
                                         ", Type: " + ex.getType() + 
                                         ", Category: " + ex.getCategory() +
                                         ", Q: " + shortQuestion);
                    }
                    return;
                }
            }
            
            // Если не нашли в ресурсах, пробуем в файловой системе
            java.nio.file.Path exercisesPath = java.nio.file.Paths.get("data", "grammar", "exercises.json");
            if (java.nio.file.Files.exists(exercisesPath)) {
                String json = new String(java.nio.file.Files.readAllBytes(exercisesPath), StandardCharsets.UTF_8);
                ExercisesWrapper wrapper = gson.fromJson(json, ExercisesWrapper.class);
                
                if (wrapper != null && wrapper.getExercises() != null && !wrapper.getExercises().isEmpty()) {
                    allExercises = convertExercises(wrapper.getExercises());
                    System.out.println("Загружено упражнений из data/grammar/exercises.json: " + allExercises.size());
                    return;
                }
            }
            
            // Если не нашли, создаем дефолтные
            System.out.println("Файл exercises.json не найден, использую дефолтные упражнения");
            createDefaultExercises();
            
        } catch (Exception e) {
            System.err.println("Ошибка загрузки упражнений: " + e.getMessage());
            e.printStackTrace();
            createDefaultExercises();
        }
    }
    
    private List<GrammarExercise> convertExercises(List<ExerciseJson> exercises) {
        List<GrammarExercise> converted = new ArrayList<>();
        
        for (ExerciseJson ex : exercises) {
            GrammarExercise newEx = new GrammarExercise();
            newEx.setId(ex.getId());
            newEx.setRuleId(ex.getRuleId());
            newEx.setQuestion(ex.getQuestion());
            newEx.setType(ex.getType());  // Сохраняем оригинальный тип!
            newEx.setExplanation(ex.getExplanation());
            
            // Определяем категорию
            String category = "Общая";
            if (ex.getRuleId() > 0 && allRules != null) {
                GrammarRule rule = getRuleById(ex.getRuleId());
                if (rule != null && rule.getCategory() != null) {
                    category = rule.getCategory();
                }
            }
            newEx.setCategory(category);
            newEx.setDifficulty("beginner");
            
            // Для разных типов упражнений
            if ("multiple_choice".equals(ex.getType())) {
                newEx.setOptions(ex.getOptions());
                if (ex.getCorrect() != null && ex.getOptions() != null && ex.getCorrect() < ex.getOptions().size()) {
                    newEx.setCorrectAnswer(ex.getOptions().get(ex.getCorrect()));
                } else {
                    newEx.setCorrectAnswer("");
                }
                converted.add(newEx);
                
            } else if ("typing".equals(ex.getType())) {
                newEx.setCorrectAnswer(ex.getCorrectAnswer() != null ? ex.getCorrectAnswer() : "");
                newEx.setOptions(null);
                converted.add(newEx);
                
            } else if ("matching".equals(ex.getType())) {
                // Для matching - сохраняем пары в специальном поле
                // Пока сохраняем как строку JSON, потом нужно будет парсить
                if (ex.getPairs() != null) {
                    // Сохраняем пары в explanation или создаем отдельное поле
                    StringBuilder pairsStr = new StringBuilder();
                    for (Pair pair : ex.getPairs()) {
                        pairsStr.append(pair.getTatar()).append(":").append(pair.getRussian()).append(";");
                    }
                    newEx.setExplanation(pairsStr.toString());
                }
                converted.add(newEx);
                System.out.println("Добавлено matching упражнение ID: " + ex.getId() + " с " + 
                    (ex.getPairs() != null ? ex.getPairs().size() : 0) + " парами");
            }
        }
        
        System.out.println("Всего сконвертировано упражнений: " + converted.size());
        return converted;
    }
    
    private void extractCategories() {
        categories = new TreeSet<>();
        
        if (allRules != null) {
            categories.addAll(allRules.stream()
                .map(GrammarRule::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        }
        
        if (allExercises != null) {
            categories.addAll(allExercises.stream()
                .map(GrammarExercise::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        }
        
        // Если категорий нет, добавляем дефолтные
        if (categories.isEmpty()) {
            categories.add("Существительные");
            categories.add("Глаголы");
            categories.add("Местоимения");
            categories.add("Синтаксис");
        }
        
        System.out.println("Категории грамматики: " + categories);
    }
    
    private void createDefaultRules() {
        allRules = new ArrayList<>();
        
        GrammarRule rule1 = new GrammarRule();
        rule1.setId(1);
        rule1.setTitle("Множественное число");
        rule1.setCategory("Существительные");
        rule1.setExplanation("В татарском языке множественное число образуется путем добавления окончаний:\n• -лар/-ләр (после звонких согласных и гласных)\n• -нар/-нәр (после носовых согласных м, н, ң)");
        rule1.setExamples("китап → китаплар (книга → книги)\nөй → өйләр (дом → дома)");
        allRules.add(rule1);
        
        GrammarRule rule2 = new GrammarRule();
        rule2.setId(2);
        rule2.setTitle("Притяжательные аффиксы");
        rule2.setCategory("Существительные");
        rule2.setExplanation("Притяжательные аффиксы указывают на принадлежность предмета");
        rule2.setExamples("китабым - моя книга\nкитабың - твоя книга");
        allRules.add(rule2);
        
        GrammarRule rule3 = new GrammarRule();
        rule3.setId(3);
        rule3.setTitle("Глаголы");
        rule3.setCategory("Глаголы");
        rule3.setExplanation("Спряжение глаголов в настоящем времени");
        rule3.setExamples("укый - читает\nбара - идет");
        allRules.add(rule3);
        
        GrammarRule rule4 = new GrammarRule();
        rule4.setId(4);
        rule4.setTitle("Местоимения");
        rule4.setCategory("Местоимения");
        rule4.setExplanation("Личные местоимения в татарском языке");
        rule4.setExamples("мин - я\nсин - ты\nул - он/она");
        allRules.add(rule4);
        
        GrammarRule rule5 = new GrammarRule();
        rule5.setId(5);
        rule5.setTitle("Вопросительные частицы");
        rule5.setCategory("Синтаксис");
        rule5.setExplanation("Вопросительные частицы -мы/-ме");
        rule5.setExamples("Син киләсеңме? - Ты придешь?\nБу китапмы? - Это книга?");
        allRules.add(rule5);
        
        System.out.println("Созданы правила по умолчанию: " + allRules.size());
    }
    
    private void createDefaultExercises() {
        allExercises = new ArrayList<>();
        
        GrammarExercise ex1 = new GrammarExercise();
        ex1.setId(1);
        ex1.setQuestion("Как образуется множественное число у слова 'китап' (книга)?");
        ex1.setCorrectAnswer("китаплар");
        ex1.setExplanation("К основе 'китап' добавляется окончание '-лар' (после твердых гласных)");
        ex1.setCategory("Существительные");
        ex1.setType("multiple_choice");
        ex1.setOptions(Arrays.asList("китаплар", "китапнар", "китап", "китаплар"));
        allExercises.add(ex1);
        
        GrammarExercise ex2 = new GrammarExercise();
        ex2.setId(2);
        ex2.setQuestion("Как образуется множественное число у слова 'өй' (дом)?");
        ex2.setCorrectAnswer("өйләр");
        ex2.setExplanation("К основе 'өй' добавляется окончание '-ләр' (после мягких гласных)");
        ex2.setCategory("Существительные");
        ex2.setType("multiple_choice");
        ex2.setOptions(Arrays.asList("өйләр", "өйнар", "өй", "өйләр"));
        allExercises.add(ex2);
        
        System.out.println("Созданы упражнения по умолчанию: " + allExercises.size());
    }
    
    // Методы для правил
    public List<GrammarRule> getAllRules() {
        return allRules != null ? allRules : new ArrayList<>();
    }
    
    public List<GrammarRule> getRulesByCategory(String category) {
        if (category == null || category.equals("Все категории")) {
            return getAllRules();
        }
        return getAllRules().stream()
            .filter(rule -> category.equals(rule.getCategory()))
            .collect(Collectors.toList());
    }
    
    public Set<String> getCategories() {
        return categories != null ? categories : new TreeSet<>();
    }
    
    public GrammarRule getRuleById(int id) {
        return getAllRules().stream()
            .filter(rule -> rule.getId() == id)
            .findFirst()
            .orElse(null);
    }
    
    // Методы для упражнений
    public List<GrammarExercise> getAllExercises() {
        return allExercises != null ? allExercises : new ArrayList<>();
    }
    
    public List<GrammarExercise> getExercisesByCategory(String category) {
        if (category == null || category.equals("Все категории")) {
            return getAllExercises();
        }
        return getAllExercises().stream()
            .filter(ex -> category.equals(ex.getCategory()))
            .collect(Collectors.toList());
    }
    
    public GrammarExercise getExerciseById(int id) {
        return getAllExercises().stream()
            .filter(ex -> ex.getId() == id)
            .findFirst()
            .orElse(null);
    }
}