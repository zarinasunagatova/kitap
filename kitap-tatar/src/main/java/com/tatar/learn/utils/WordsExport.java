package com.tatar.learn.utils;

import com.tatar.learn.models.Word;
import java.time.LocalDate;
import java.util.List;

public class WordsExport {
    private List<Word> words;
    private List<String> categories;
    private LocalDate exportDate;
    private int totalWords;
    private String version = "2.0";
    
    // Статистика прогресса
    private int totalLearned;      // Слов с timesCorrect >= 5
    private int totalInProgress;   // Слов с timesCorrect > 0 и < 5
    private int totalNew;          // Слов с timesCorrect == 0
    
    public WordsExport() {}
    
    // Геттеры и сеттеры
    public List<Word> getWords() { return words; }
    public void setWords(List<Word> words) { 
        this.words = words;
        calculateStats();
    }
    
    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }
    
    public LocalDate getExportDate() { return exportDate; }
    public void setExportDate(LocalDate exportDate) { this.exportDate = exportDate; }
    
    public int getTotalWords() { return totalWords; }
    public void setTotalWords(int totalWords) { this.totalWords = totalWords; }
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public int getTotalLearned() { return totalLearned; }
    public void setTotalLearned(int totalLearned) { this.totalLearned = totalLearned; }
    
    public int getTotalInProgress() { return totalInProgress; }
    public void setTotalInProgress(int totalInProgress) { this.totalInProgress = totalInProgress; }
    
    public int getTotalNew() { return totalNew; }
    public void setTotalNew(int totalNew) { this.totalNew = totalNew; }
    
    private void calculateStats() {
        if (words == null) return;
        
        totalLearned = 0;
        totalInProgress = 0;
        totalNew = 0;
        
        for (Word word : words) {
            if (word.getTimesCorrect() >= 5) {
                totalLearned++;
            } else if (word.getTimesCorrect() > 0) {
                totalInProgress++;
            } else {
                totalNew++;
            }
        }
    }
}