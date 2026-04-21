package com.tatar.learn.controllers;

import com.tatar.learn.models.Topic;
import com.tatar.learn.models.Word;
import com.tatar.learn.services.DatabaseService;
import com.tatar.learn.services.TopicsService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.InputStream;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

public class TopicsController implements Initializable {
    
    @FXML private GridPane topicsGrid;
    @FXML private Button backButton;
    @FXML private Label totalProgressLabel;
    @FXML private ProgressBar totalProgressBar;
    @FXML private Label totalPercentLabel;
    
    private TopicsService topicsService;
    private DatabaseService dbService;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        topicsService = TopicsService.getInstance();
        try {
            dbService = DatabaseService.getInstance();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        updateTotalProgress();
        createTopicCards();
        
        backButton.setOnAction(e -> goBack());
    }
    
    private void updateTotalProgress() {
        int progress = topicsService.getTotalProgress();
        totalProgressBar.setProgress(progress / 100.0);
        totalPercentLabel.setText(progress + "%");
    }
    
    private void createTopicCards() {
        topicsGrid.getChildren().clear();
        
        List<Topic> topics = topicsService.getAllTopics();
        int col = 0;
        int row = 0;
        
        for (Topic topic : topics) {
            VBox card = createTopicCard(topic);
            topicsGrid.add(card, col, row);
            col++;
            if (col >= 2) {
                col = 0;
                row++;
            }
        }
    }
    
    private VBox createTopicCard(Topic topic) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new javafx.geometry.Insets(20));
        card.getStyleClass().add("topic-card");
        
        if (!topic.isUnlocked()) {
            card.getStyleClass().add("topic-card-locked");
        }
        if (topic.isCompleted()) {
            card.getStyleClass().add("topic-card-completed");
        }
        
        Label iconLabel = new Label(topic.getIcon());
        iconLabel.setStyle("-fx-font-size: 48px;");
        
        Label titleLabel = new Label(topic.getName());
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        
        Label tatarLabel = new Label(topic.getNameTatar());
        tatarLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #9b87c9;");
        
        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(150);
        progressBar.setProgress(topic.getProgress() / 100.0);
        
        Label progressLabel = new Label(topic.getLearnedWordsCount() + " / " + topic.getWordsCount() + " слов");
        progressLabel.setStyle("-fx-font-size: 12px;");
        
        Button actionButton = new Button();
        
        if (!topic.isUnlocked()) {
            actionButton.setText("🔒 Закрыто");
            actionButton.setDisable(true);
            actionButton.getStyleClass().add("locked-button");
        } else if (topic.isCompleted()) {
            actionButton.setText("✅ Пройдено");
            actionButton.setDisable(true);
            actionButton.getStyleClass().add("completed-button");
        } else {
            actionButton.setText("📖 Начать урок");
            actionButton.getStyleClass().add("start-button");
            actionButton.setOnAction(e -> startTopic(topic));
        }
        
        card.getChildren().addAll(iconLabel, titleLabel, tatarLabel, progressBar, progressLabel, actionButton);
        
        return card;
    }
    
    private void startTopic(Topic topic) {
        try {
            // ДОБАВЛЯЕМ СЛОВА ТЕМЫ В БД ПЕРЕД НАЧАЛОМ УРОКА
            syncTopicWordsToDatabase(topic);
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TopicLearnView.fxml"));
            Parent root = loader.load();
            
            TopicLearnController learnController = loader.getController();
            learnController.setTopic(topic);
            
            Stage stage = new Stage();
            stage.setTitle("📚 Урок: " + topic.getName() + " - " + topic.getNameTatar());
            stage.setScene(new Scene(root, 1000, 700));
            
            loadCssSafely(stage.getScene());
            stage.show();
            
            Stage currentStage = (Stage) backButton.getScene().getWindow();
            currentStage.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            showError("Ошибка", "Не удалось начать урок: " + e.getMessage());
        }
    }

    /**
     * Синхронизирует слова темы с базой данных
     */
    private void syncTopicWordsToDatabase(Topic topic) {
        if (dbService == null) return;
        
        for (Word word : topic.getWords()) {
            boolean exists = false;
            for (Word existing : dbService.getAllWords()) {
                if (existing.getTatar().equalsIgnoreCase(word.getTatar())) {
                    exists = true;
                    // Обновляем ID у слова из темы
                    word.setId(existing.getId());
                    word.setTimesCorrect(existing.getTimesCorrect());
                    word.setTimesWrong(existing.getTimesWrong());
                    word.setLastReviewed(existing.getLastReviewed());
                    word.setEaseFactor(existing.getEaseFactor());
                    break;
                }
            }
            if (!exists) {
                dbService.addWord(word);
                System.out.println("✅ Добавлено слово в БД: " + word.getTatar() + " (ID: " + word.getId() + ")");
            }
        }
    }

 // TopicsController.java — кнопка «Назад» просто закрывает окно
    @FXML
    private void goBack() {
        Stage stage = (Stage) backButton.getScene().getWindow();
        stage.close();  // Просто закрываем окно уроков, главное окно остаётся!
    }

    // Вспомогательный метод для безопасной загрузки CSS
    private void loadCssSafely(Scene scene) {
        try {
            URL cssUrl = getClass().getResource("/styles/main.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            } else {
                System.err.println("⚠️ CSS не найден в TopicsController");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Ошибка загрузки CSS: " + e.getMessage());
        }
    }
    
    /**
     * Безопасная загрузка иконки для диалогов
     */
    private Image loadDialogIcon() {
        try {
            InputStream is = getClass().getResourceAsStream("/images/kitap.png");
            if (is == null) {
                is = getClass().getClassLoader().getResourceAsStream("images/kitap.png");
            }
            if (is != null) {
                return new Image(is);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Не удалось загрузить иконку: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Устанавливает иконку для Alert
     */
    private void setAlertIcon(Alert alert) {
        try {
            Image icon = loadDialogIcon();
            if (icon != null) {
                Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
                stage.getIcons().add(icon);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Не удалось установить иконку: " + e.getMessage());
        }
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        setAlertIcon(alert);
        alert.showAndWait();
    }
}