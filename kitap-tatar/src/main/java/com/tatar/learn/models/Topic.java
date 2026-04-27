package com.tatar.learn.models;

import java.util.ArrayList;
import java.util.List;

public class Topic {
    private int id;
    private String name;
    private String nameTatar;
    private String icon;
    private int order;
    private boolean isUnlocked;
    private boolean isCompleted;
    private List<Word> words;
    private GrammarRule grammar;
    private List<GrammarExercise> exercises; 
    
    public Topic() {
        this.words = new ArrayList<>();
        this.exercises = new ArrayList<>();  
    }
    
    public Topic(int id, String name, String nameTatar, String icon, int order) {
        this.id = id;
        this.name = name;
        this.nameTatar = nameTatar;
        this.icon = icon;
        this.order = order;
        this.words = new ArrayList<>();
        this.exercises = new ArrayList<>();  
        this.isUnlocked = (order == 1);
        this.isCompleted = false;
    }
    
    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getNameTatar() { return nameTatar; }
    public void setNameTatar(String nameTatar) { this.nameTatar = nameTatar; }
    
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    
    public boolean isUnlocked() { return isUnlocked; }
    public void setUnlocked(boolean unlocked) { isUnlocked = unlocked; }
    
    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }
    
    public List<Word> getWords() { 
        if (words == null) words = new ArrayList<>();
        return words; 
    }
    public void setWords(List<Word> words) { this.words = words; }
    
    public GrammarRule getGrammar() { return grammar; }
    public void setGrammar(GrammarRule grammar) { this.grammar = grammar; }
    
    public List<GrammarExercise> getExercises() { 
        if (exercises == null) exercises = new ArrayList<>();
        return exercises; 
    }
    public void setExercises(List<GrammarExercise> exercises) { this.exercises = exercises; }
    
    public int getProgress() {
        if (getWords().isEmpty()) return 0;
        int learned = 0;
        for (Word word : getWords()) {
            if (word.getTimesCorrect() >= 3) learned++;
        }
        return (learned * 100) / getWords().size();
    }
    
    public int getWordsCount() { return getWords().size(); }
    
    public int getLearnedWordsCount() {
        int learned = 0;
        for (Word word : getWords()) {
            if (word.getTimesCorrect() >= 3) learned++;
        }
        return learned;
    }
    
    public void addWord(Word word) { 
        if (words == null) words = new ArrayList<>();
        words.add(word); 
    }
    
    public void addExercise(GrammarExercise exercise) { 
        if (exercises == null) exercises = new ArrayList<>();
        exercises.add(exercise); 
    }
}