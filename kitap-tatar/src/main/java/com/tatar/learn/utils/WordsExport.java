package com.tatar.learn.utils;

import com.tatar.learn.models.Word;
import java.time.LocalDate;
import java.util.List;

public class WordsExport {
    private List<Word> words;
    private List<String> categories;
    private LocalDate exportDate;
    private int totalWords;
    
    // геттеры и сеттеры
    public List<Word> getWords() { return words; }
    public void setWords(List<Word> words) { this.words = words; }
    
    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }
    
    public LocalDate getExportDate() { return exportDate; }
    public void setExportDate(LocalDate exportDate) { this.exportDate = exportDate; }
    
    public int getTotalWords() { return totalWords; }
    public void setTotalWords(int totalWords) { this.totalWords = totalWords; }
}