package com.tatar.learn.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.net.URL;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestController implements Initializable {
    
    @FXML private TabPane exerciseTabs;
    @FXML private Tab multipleChoiceTab;
    @FXML private Tab typingTab;
    @FXML private Tab grammarTab;
    @FXML private VBox multipleChoiceContainer;
    @FXML private VBox typingContainer;
    @FXML private VBox grammarContainer;
    @FXML private Label statusLabel;
    
    private Map<Tab, Object> controllers = new HashMap<>();
    private Map<Tab, Boolean> loadedFlags = new HashMap<>();
    private static final Logger log = LoggerFactory.getLogger(TypingController.class);
    
    private static class TabConfig {
        final Tab tab;
        final VBox container;
        final String fxmlPath;
        
        TabConfig(Tab tab, VBox container, String fxmlPath) {
            this.tab = tab;
            this.container = container;
            this.fxmlPath = fxmlPath;
        }
    }
    
    private List<TabConfig> tabConfigs;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            tabConfigs = Arrays.asList(
                new TabConfig(multipleChoiceTab, multipleChoiceContainer, "/fxml/MultipleChoiceView.fxml"),
                new TabConfig(typingTab, typingContainer, "/fxml/TypingView.fxml"),
                new TabConfig(grammarTab, grammarContainer, "/fxml/GrammarView.fxml")
            );
            
            for (TabConfig config : tabConfigs) {
                loadedFlags.put(config.tab, false);
            }
            
            setupLazyLoading();
            log.info("Предварительная загрузка вкладки: " + multipleChoiceTab.getText());
            loadTab(tabConfigs.get(0));
            updateStatus("Готов к работе. Выберите тип упражнения.", "success");
            
        } catch (Exception e) {
            System.err.println("Error initializing TestController: " + e.getMessage());
            updateStatus("Ошибка инициализации: " + e.getMessage(), "error");
        }
    }
    
    private void setupLazyLoading() {
        exerciseTabs.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldTab, newTab) -> {
                if (newTab != null && !loadedFlags.getOrDefault(newTab, false)) {
                    tabConfigs.stream()
                        .filter(config -> config.tab == newTab)
                        .findFirst()
                        .ifPresent(this::loadTab);
                }
            }
        );
    }
    
    private void loadTab(TabConfig config) {
        try {
            updateStatus("Загрузка " + config.tab.getText() + "...", "info");
            
            URL fxmlUrl = getClass().getResource(config.fxmlPath);
            if (fxmlUrl == null) {
                throw new RuntimeException("FXML file not found: " + config.fxmlPath);
            }
            
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            
            controllers.put(config.tab, loader.getController());
            config.container.getChildren().setAll(root);
            loadedFlags.put(config.tab, true);
            config.tab.getStyleClass().remove("error");
            
            updateStatus(config.tab.getText() + " успешно загружен", "success");
            
        } catch (Exception e) {
            System.err.println("Error loading tab " + config.tab.getText() + ": " + e.getMessage());
            showError(config, e);
        }
    }
    
    private void showError(TabConfig config, Exception e) {
        VBox errorBox = new VBox(15);
        errorBox.setStyle("-fx-padding: 20; -fx-alignment: center; -fx-background-color: white;");
        
        Label iconLabel = new Label("⚠️");
        iconLabel.setStyle("-fx-font-size: 48px;");
        
        Label titleLabel = new Label("Не удалось загрузить " + config.tab.getText());
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #d48c7a;");
        
        Label messageLabel = new Label("Ошибка: " + e.getMessage());
        messageLabel.setStyle("-fx-wrap-text: true; -fx-max-width: 400;");
        
        Button retryButton = new Button("🔄 Попробовать снова");
        retryButton.setStyle("-fx-background-color: #9b87c9; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 20;");
        retryButton.setOnAction(event -> loadTab(config));
        
        errorBox.getChildren().addAll(iconLabel, titleLabel, messageLabel, retryButton);
        config.container.getChildren().setAll(errorBox);
        config.tab.getStyleClass().add("error");
        loadedFlags.put(config.tab, false);
        
        updateStatus("Ошибка загрузки " + config.tab.getText(), "error");
    }
    
    private void updateStatus(String message, String type) {
        if (statusLabel != null) {
            String emoji = "";
            switch (type) {
                case "success": emoji = "✅"; break;
                case "error": emoji = "❌"; break;
                case "info": emoji = "ℹ️"; break;
                default: emoji = "📝";
            }
            statusLabel.setText(emoji + " " + message);
        }
    }
    
    public MultipleChoiceController getMultipleChoiceController() {
        return (MultipleChoiceController) controllers.get(multipleChoiceTab);
    }
    
    public TypingController getTypingController() {
        return (TypingController) controllers.get(typingTab);
    }
    
    public GrammarController getGrammarController() {
        return (GrammarController) controllers.get(grammarTab);
    }
    
    public void refreshAll() {
        tabConfigs.forEach(config -> {
            if (loadedFlags.getOrDefault(config.tab, false)) {
                loadTab(config);
            }
        });
    }
}