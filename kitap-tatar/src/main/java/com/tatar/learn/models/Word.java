package com.tatar.learn.models;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Word {
    private int id;
    private String tatar;
    private String russian;
    private String category;
    private List<String> examples;  // НОВОЕ ПОЛЕ
    private int timesCorrect;
    private int timesWrong;
    private LocalDate lastReviewed;
    private double easeFactor;
    
    // Конструктор с категорией
    public Word(String tatar, String russian, String category) {
        this(tatar, russian, category, new ArrayList<>());
    }
    
    // НОВЫЙ КОНСТРУКТОР с примерами
    public Word(String tatar, String russian, String category, List<String> examples) {
        this.tatar = tatar;
        this.russian = russian;
        this.category = category;
        this.examples = examples != null ? examples : new ArrayList<>();
        this.timesCorrect = 0;
        this.timesWrong = 0;
        this.lastReviewed = null;
        this.easeFactor = 2.5;
    }
    
    // Пустой конструктор
    public Word() {
        this.examples = new ArrayList<>();
    }
    
    // Геттеры и сеттеры
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
    
    public int getTimesCorrect() { return timesCorrect; }
    public void setTimesCorrect(int timesCorrect) { this.timesCorrect = timesCorrect; }
    
    public int getTimesWrong() { return timesWrong; }
    public void setTimesWrong(int timesWrong) { this.timesWrong = timesWrong; }
    
    public LocalDate getLastReviewed() { return lastReviewed; }
    public void setLastReviewed(LocalDate lastReviewed) { this.lastReviewed = lastReviewed; }
    
    public double getEaseFactor() { return easeFactor; }
    public void setEaseFactor(double easeFactor) { this.easeFactor = easeFactor; }
    
    // Добавить пример
    public void addExample(String example) {
        if (examples == null) {
            examples = new ArrayList<>();
        }
        examples.add(example);
    }
    
    // Получить случайный пример
    public String getRandomExample() {
        if (examples == null || examples.isEmpty()) {
            return null;
        }
        return examples.get((int)(Math.random() * examples.size()));
    }
    
    public void incrementCorrect() {
        timesCorrect++;
        lastReviewed = LocalDate.now();
    }
    
    public void incrementWrong() {
        timesWrong++;
        lastReviewed = LocalDate.now();
    }
    
    public int getNextReviewInterval() {
        if (timesCorrect == 0) return 1;
        if (timesCorrect == 1) return 3;
        if (timesCorrect == 2) return 7;
        if (timesCorrect == 3) return 14;
        if (timesCorrect == 4) return 30;
        return 60;
    }
    
    public void resetProgress() {
        timesCorrect = 0;
        timesWrong = 0;
        easeFactor = 2.5;
        lastReviewed = null;
    }
    
    @Override
    public String toString() {
        return tatar + " - " + russian + " [" + category + "] (" + 
               timesCorrect + " прав., " + timesWrong + " неправ.)" +
               (examples != null && !examples.isEmpty() ? " +" + examples.size() + " примеров" : "");
    }
}