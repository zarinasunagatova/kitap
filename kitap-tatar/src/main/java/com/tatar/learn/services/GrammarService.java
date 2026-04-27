package com.tatar.learn.services;

import com.google.gson.Gson;
import com.tatar.learn.models.GrammarExercise;
import com.tatar.learn.models.GrammarRule;
import java.io.*;
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
        private String explanation;
        private List<Pair> pairs;
        
        public int getId() { return id; }
        public int getRuleId() { return ruleId; }
        public String getType() { return type; }
        public String getQuestion() { return question; }
        public List<String> getOptions() { return options; }
        public Integer getCorrect() { return correct; }
        public String getCorrectAnswer() { return correctAnswer; }
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
        private String explanation;
        private List<Example> examples;
        private String notes;    
        
        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getCategory() { return category; }
        public String getExplanation() { return explanation; }
        public List<Example> getExamples() { return examples; }
        public String getNotes() { return notes; }
    }
    
    private static class Example {
        private String tatar;
        private String russian;
        
        public String getTatar() { return tatar; }
        public String getRussian() { return russian; }
    }
    
    private static class GrammarWrapper {
        private List<GrammarJsonRule> rules;
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
            // ПРАВИЛЬНЫЙ ПУТЬ: /data/grammar/grammar.json
            InputStream is = getClass().getResourceAsStream("/data/grammar/grammar.json");
            
            if (is == null) {
                is = getClass().getClassLoader().getResourceAsStream("data/grammar/grammar.json");
            }
            
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
            
            System.out.println("Файл grammar.json не найден, использую дефолтные правила");
            createDefaultRules();
            
        } catch (Exception e) {
            System.err.println("Ошибка загрузки грамматики: " + e.getMessage());
            e.printStackTrace();
            createDefaultRules();
        }
    }

    private void loadExercises() {
        try {
            // ПРАВИЛЬНЫЙ ПУТЬ: /data/grammar/exercises.json
            InputStream is = getClass().getResourceAsStream("/data/grammar/exercises.json");
            
            if (is == null) {
                is = getClass().getClassLoader().getResourceAsStream("data/grammar/exercises.json");
            }
            
            if (is != null) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                ExercisesWrapper wrapper = gson.fromJson(json, ExercisesWrapper.class);
                
                if (wrapper != null && wrapper.getExercises() != null && !wrapper.getExercises().isEmpty()) {
                    allExercises = convertExercises(wrapper.getExercises());
                    System.out.println("Загружено упражнений из /data/grammar/exercises.json: " + allExercises.size());
                    
                    for (GrammarExercise ex : allExercises) {
                        System.out.println("  - ID: " + ex.getId() + 
                                         ", Type: " + ex.getType() + 
                                         ", Category: " + ex.getCategory());
                    }
                    return;
                }
            }
            
            System.out.println("Файл exercises.json не найден, использую дефолтные упражнения");
            createDefaultExercises();
            
        } catch (Exception e) {
            System.err.println("Ошибка загрузки упражнений: " + e.getMessage());
            e.printStackTrace();
            createDefaultExercises();
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
    
    
    
    private List<GrammarExercise> convertExercises(List<ExerciseJson> exercises) {
        List<GrammarExercise> converted = new ArrayList<>();
        int skippedCount = 0;
        
        for (ExerciseJson ex : exercises) {
            GrammarExercise newEx = new GrammarExercise();
            newEx.setId(ex.getId());
            newEx.setRuleId(ex.getRuleId());
            newEx.setQuestion(ex.getQuestion());
            newEx.setType(ex.getType());
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
            
            // Конвертируем в зависимости от типа
            boolean conversionSuccess = false;
            
            if ("multiple_choice".equals(ex.getType())) {
                conversionSuccess = convertMultipleChoice(ex, newEx);
            } else if ("typing".equals(ex.getType())) {
                conversionSuccess = convertTyping(ex, newEx);
            } else if ("matching".equals(ex.getType())) {
                conversionSuccess = convertMatching(ex, newEx);
            }
            
            // ВАЖНО: добавляем только если конвертация успешна
            if (conversionSuccess) {
                converted.add(newEx);
            } else {
                skippedCount++;
                System.err.println("❌ Упражнение ID " + ex.getId() + " пропущено (не прошло валидацию)");
            }
        }
        
        System.out.println("Всего сконвертировано упражнений: " + converted.size());
        if (skippedCount > 0) {
            System.out.println("⚠️ Пропущено некорректных упражнений: " + skippedCount);
        }
        return converted;
    }

    private boolean convertMultipleChoice(ExerciseJson ex, GrammarExercise newEx) {
        // Проверяем опции
        if (ex.getOptions() == null || ex.getOptions().isEmpty()) {
            System.err.println("  - Нет options");
            return false;
        }
        
        newEx.setOptions(ex.getOptions());
        
        // Проверяем правильный ответ
        if (ex.getCorrect() == null) {
            System.err.println("  - Нет correct индекса");
            return false;
        }
        
        if (ex.getCorrect() >= ex.getOptions().size()) {
            System.err.println("  - correct индекс " + ex.getCorrect() + 
                             " выходит за пределы options (size=" + ex.getOptions().size() + ")");
            return false;
        }
        
        String correctAnswer = ex.getOptions().get(ex.getCorrect());
        if (correctAnswer == null || correctAnswer.trim().isEmpty()) {
            System.err.println("  - Правильный ответ пустой");
            return false;
        }
        
        newEx.setCorrectAnswer(correctAnswer);
        return true;
    }

    private boolean convertTyping(ExerciseJson ex, GrammarExercise newEx) {
        String correctAnswer = ex.getCorrectAnswer();
        
        if (correctAnswer == null || correctAnswer.trim().isEmpty()) {
            System.err.println("  - Нет correctAnswer или он пустой");
            return false;
        }
        
        newEx.setCorrectAnswer(correctAnswer);
        newEx.setOptions(null);
        return true;
    }

    private boolean convertMatching(ExerciseJson ex, GrammarExercise newEx) {
        if (ex.getPairs() == null || ex.getPairs().isEmpty()) {
            System.err.println("  - Нет pairs для matching");
            return false;
        }
        
        StringBuilder pairsStr = new StringBuilder();
        int validPairs = 0;
        
        for (Pair pair : ex.getPairs()) {
            if (pair.getTatar() != null && !pair.getTatar().trim().isEmpty() &&
                pair.getRussian() != null && !pair.getRussian().trim().isEmpty()) {
                pairsStr.append(pair.getTatar().trim()).append(":")
                        .append(pair.getRussian().trim()).append(";");
                validPairs++;
            } else {
                System.err.println("  - Пропущена некорректная пара: " + pair);
            }
        }
        
        if (validPairs < 2) {
            System.err.println("  - Недостаточно корректных пар (нужно минимум 2, найдено " + validPairs + ")");
            return false;
        }
        
        newEx.setExplanation(pairsStr.toString());
        System.out.println("  - Matching упражнение ID " + ex.getId() + 
                           ": " + validPairs + " корректных пар");
        return true;
    }
    
  
    public List<GrammarExercise> getValidExercises() {
        if (allExercises == null) {
            return new ArrayList<>();
        }
        return allExercises.stream()
            .filter(this::isValidExercise)
            .collect(Collectors.toList());
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
    
    
    public Set<String> getExerciseCategories() {
        Set<String> exerciseCategories = new TreeSet<>();
        if (allExercises != null) {
            exerciseCategories.addAll(allExercises.stream()
                .map(GrammarExercise::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        }
        return exerciseCategories;
    }

    public Set<String> getRuleCategories() {
        Set<String> ruleCategories = new TreeSet<>();
        if (allRules != null) {
            ruleCategories.addAll(allRules.stream()
                .map(GrammarRule::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        }
        return ruleCategories;
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
    
    private boolean isValidExercise(GrammarExercise exercise) {
        if (exercise == null) {
            System.err.println("❌ Валидация не пройдена: упражнение null");
            return false;
        }
        
        // Проверяем наличие ID
        if (exercise.getId() <= 0) {
            System.err.println("❌ Упражнение ID " + exercise.getId() + " пропущено: неверный ID");
            return false;
        }
        
        // Проверяем наличие вопроса
        if (exercise.getQuestion() == null || exercise.getQuestion().trim().isEmpty()) {
            System.err.println("❌ Упражнение ID " + exercise.getId() + " пропущено: отсутствует вопрос");
            return false;
        }
        
        // Проверяем тип упражнения
        String type = exercise.getType();
        if (type == null || type.trim().isEmpty()) {
            System.err.println("❌ Упражнение ID " + exercise.getId() + " пропущено: отсутствует тип");
            return false;
        }
        
        // Валидация в зависимости от типа
        switch (type) {
            case "multiple_choice":
                return validateMultipleChoice(exercise);
            case "typing":
                return validateTyping(exercise);
            case "matching":
                return validateMatching(exercise);
            default:
                System.err.println("❌ Упражнение ID " + exercise.getId() + 
                                 " пропущено: неизвестный тип '" + type + "'");
                return false;
        }
    }
    private boolean validateMultipleChoice(GrammarExercise exercise) {
        // Проверяем наличие опций
        if (exercise.getOptions() == null || exercise.getOptions().isEmpty()) {
            System.err.println("❌ Multiple choice упражнение ID " + exercise.getId() + 
                             " пропущено: нет вариантов ответа (options)");
            return false;
        }
        
        // Проверяем, что опций достаточно (минимум 2, лучше 4)
        if (exercise.getOptions().size() < 2) {
            System.err.println("❌ Multiple choice упражнение ID " + exercise.getId() + 
                             " пропущено: недостаточно вариантов ответа (нужно минимум 2, есть " + 
                             exercise.getOptions().size() + ")");
            return false;
        }
        
        // Проверяем наличие правильного ответа
        if (exercise.getCorrectAnswer() == null || exercise.getCorrectAnswer().trim().isEmpty()) {
            System.err.println("❌ Multiple choice упражнение ID " + exercise.getId() + 
                             " пропущено: нет правильного ответа (correctAnswer)");
            return false;
        }
        
        // Проверяем, что правильный ответ есть среди опций
        if (!exercise.getOptions().contains(exercise.getCorrectAnswer())) {
            System.err.println("❌ Multiple choice упражнение ID " + exercise.getId() + 
                             " пропущено: правильный ответ '" + exercise.getCorrectAnswer() + 
                             "' отсутствует в списке опций");
            return false;
        }
        
        return true;
    }

    private boolean validateTyping(GrammarExercise exercise) {
        // Проверяем наличие правильного ответа
        if (exercise.getCorrectAnswer() == null || exercise.getCorrectAnswer().trim().isEmpty()) {
            System.err.println("❌ Typing упражнение ID " + exercise.getId() + 
                             " пропущено: нет правильного ответа (correctAnswer)");
            return false;
        }
        
        // Проверяем, что ответ не слишком короткий (опционально)
        if (exercise.getCorrectAnswer().trim().length() < 1) {
            System.err.println("❌ Typing упражнение ID " + exercise.getId() + 
                             " пропущено: правильный ответ слишком короткий");
            return false;
        }
        
        return true;
    }

    private boolean validateMatching(GrammarExercise exercise) {
        // Проверяем наличие пар
        String pairsStr = exercise.getExplanation();
        if (pairsStr == null || pairsStr.trim().isEmpty()) {
            System.err.println("❌ Matching упражнение ID " + exercise.getId() + 
                             " пропущено: нет пар для сопоставления");
            return false;
        }
        
        // Парсим и проверяем пары
        String[] pairs = pairsStr.split(";");
        int validPairs = 0;
        
        for (String pair : pairs) {
            if (pair.contains(":")) {
                String[] parts = pair.split(":");
                if (parts.length == 2 && 
                    !parts[0].trim().isEmpty() && 
                    !parts[1].trim().isEmpty()) {
                    validPairs++;
                } else {
                    System.err.println("⚠️ Matching упражнение ID " + exercise.getId() + 
                                     ": некорректная пара '" + pair + "'");
                }
            }
        }
        
        if (validPairs < 2) {
            System.err.println("❌ Matching упражнение ID " + exercise.getId() + 
                             " пропущено: недостаточно корректных пар (нужно минимум 2, найдено " + 
                             validPairs + ")");
            return false;
        }
        
        System.out.println("✅ Matching упражнение ID " + exercise.getId() + 
                           " прошло валидацию: " + validPairs + " корректных пар");
        return true;
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
    
    public List<GrammarExercise> getExercisesByType(String type) {
        if (type == null || type.isEmpty()) {
            return new ArrayList<>();
        }
        return getAllExercises().stream()
            .filter(ex -> type.equals(ex.getType()))
            .collect(Collectors.toList());
    }
    
    public List<GrammarExercise> getExercisesByTypeAndCategory(String type, String category) {
        List<GrammarExercise> byType = getExercisesByType(type);
        
        if (category == null || category.equals("Все категории")) {
            return byType;
        }
        
        return byType.stream()
            .filter(ex -> category.equals(ex.getCategory()))
            .collect(Collectors.toList());
    }
    
    public boolean hasExercisesOfType(String type) {
        return !getExercisesByType(type).isEmpty();
    }
    
    public Set<String> getAvailableTypes() {
        Set<String> types = new TreeSet<>();
        if (allExercises != null) {
            types.addAll(allExercises.stream()
                .map(GrammarExercise::getType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        }
        return types;
    }
    
    public GrammarExercise getExerciseById(int id) {
        return getAllExercises().stream()
            .filter(ex -> ex.getId() == id)
            .findFirst()
            .orElse(null);
    }
}