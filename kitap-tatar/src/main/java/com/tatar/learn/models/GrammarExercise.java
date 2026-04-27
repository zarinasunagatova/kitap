package com.tatar.learn.models;

import java.util.List;

public class GrammarExercise {
    private int id;
    private int ruleId;  
    private String question;
    private String correctAnswer;
    private String explanation;
    private String category;
    private String difficulty;
    private String type;  
    private List<String> options;  
    
    public GrammarExercise() {}
    
    // Конструктор со всеми полями
    public GrammarExercise(int id, String question, String correctAnswer, 
                           String explanation, String category, String difficulty) {
        this.id = id;
        this.question = question;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
        this.category = category;
        this.difficulty = difficulty;
    }
    
    // Геттеры
    public int getId() { return id; }
    public int getRuleId() { return ruleId; }
    public String getQuestion() { return question; }
    public String getCorrectAnswer() { return correctAnswer; }
    public String getExplanation() { return explanation; }
    public String getCategory() { return category; }
    public String getDifficulty() { return difficulty; }
    public String getType() { return type; }
    public List<String> getOptions() { return options; }
    
    // Сеттеры
    public void setId(int id) { this.id = id; }
    public void setRuleId(int ruleId) { this.ruleId = ruleId; }
    public void setQuestion(String question) { this.question = question; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public void setCategory(String category) { this.category = category; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public void setType(String type) { this.type = type; }
    public void setOptions(List<String> options) { this.options = options; }
}