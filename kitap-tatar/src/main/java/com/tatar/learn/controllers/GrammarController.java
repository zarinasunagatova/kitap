package com.tatar.learn.controllers;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

import com.tatar.learn.models.GrammarRule;
import com.tatar.learn.services.GrammarService;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class GrammarController implements Initializable {
    
    @FXML private ListView<String> topicsList;
    @FXML private Label titleLabel;
    @FXML private TextFlow explanationArea;
    @FXML private TextFlow examplesArea;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private Button prevButton;
    @FXML private Button nextButton;
    
    private GrammarService grammarService;
    private List<GrammarRule> currentRules;
    private List<GrammarRule> allRules;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        grammarService = GrammarService.getInstance();
        allRules = grammarService.getAllRules();
        currentRules = new ArrayList<>(allRules);
        
        setupCategoryFilter();
        setupList();
        setupButtons();
        
        if (!currentRules.isEmpty()) {
            topicsList.getSelectionModel().select(0);
            showRule(0);
        }
    }
    
    private void setupCategoryFilter() {
        categoryCombo.getItems().add("Все категории");
        Set<String> categories = grammarService.getCategories();
        if (categories != null && !categories.isEmpty()) {
            categoryCombo.getItems().addAll(categories);
        }
        categoryCombo.getSelectionModel().selectFirst();
        
        categoryCombo.setOnAction(e -> filterByCategory());
    }
    
    private void setupList() {
        updateTopicsList();
        
        topicsList.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    int index = topicsList.getSelectionModel().getSelectedIndex();
                    if (index >= 0 && index < currentRules.size()) {
                        showRule(index);
                    }
                }
            }
        );
    }
    
    private void updateTopicsList() {
        List<String> titles = new ArrayList<>();
        for (GrammarRule rule : currentRules) {
            titles.add(rule.getTitle());
        }
        topicsList.getItems().setAll(titles);
    }
    
    private void filterByCategory() {
        String selectedCategory = categoryCombo.getValue();
        
        if (selectedCategory == null || selectedCategory.equals("Все категории")) {
            currentRules = new ArrayList<>(allRules);
        } else {
            currentRules = grammarService.getRulesByCategory(selectedCategory);
        }
        
        updateTopicsList();
        
        if (!currentRules.isEmpty()) {
            topicsList.getSelectionModel().select(0);
            showRule(0);
        }
    }
    
    private void setupButtons() {
        prevButton.setOnAction(e -> navigatePrevious());
        nextButton.setOnAction(e -> navigateNext());
    }
    
    private void navigatePrevious() {
        int selectedIndex = topicsList.getSelectionModel().getSelectedIndex();
        if (selectedIndex > 0) {
            topicsList.getSelectionModel().select(selectedIndex - 1);
        }
    }
    
    private void navigateNext() {
        int selectedIndex = topicsList.getSelectionModel().getSelectedIndex();
        if (selectedIndex < topicsList.getItems().size() - 1) {
            topicsList.getSelectionModel().select(selectedIndex + 1);
        }
    }
    
    private void showRule(int index) {
        if (index < 0 || index >= currentRules.size()) return;
        
        GrammarRule rule = currentRules.get(index);
        titleLabel.setText(rule.getTitle());
        
        // Очищаем и заполняем объяснение
        explanationArea.getChildren().clear();
        Text explanationText = new Text(rule.getExplanation());
        explanationText.setStyle("-fx-font-size: 16px;");
        explanationArea.getChildren().add(explanationText);
        
        // Очищаем и заполняем примеры
        examplesArea.getChildren().clear();
        Text examplesText = new Text(rule.getExamples());
        examplesText.setStyle("-fx-font-size: 16px;");
        examplesArea.getChildren().add(examplesText);
    }
}