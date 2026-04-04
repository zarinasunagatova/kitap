package com.tatar.learn.models;

import java.util.List;

public class CultureItem {
    private int id;
    private String title;
    private String titleTatar;
    private String description;
    private String descriptionTatar;
    private String category; // "holiday", "food", "music", "clothing", "literature", "tradition"
    private String icon; // эмодзи или путь к иконке
    private List<String> funFacts;
    private String imageUrl;
    
    public CultureItem() {}
    
    public CultureItem(int id, String title, String titleTatar, String description, 
                       String descriptionTatar, String category, String icon) {
        this.id = id;
        this.title = title;
        this.titleTatar = titleTatar;
        this.description = description;
        this.descriptionTatar = descriptionTatar;
        this.category = category;
        this.icon = icon;
    }
    
    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getTitleTatar() { return titleTatar; }
    public void setTitleTatar(String titleTatar) { this.titleTatar = titleTatar; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getDescriptionTatar() { return descriptionTatar; }
    public void setDescriptionTatar(String descriptionTatar) { this.descriptionTatar = descriptionTatar; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    
    public List<String> getFunFacts() { return funFacts; }
    public void setFunFacts(List<String> funFacts) { this.funFacts = funFacts; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}