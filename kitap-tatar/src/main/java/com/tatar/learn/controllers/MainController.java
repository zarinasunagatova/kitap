package com.tatar.learn.controllers;

import com.tatar.learn.services.TTSService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;

public class MainController implements Initializable {
    
    @FXML private Button dictionaryButton;
    @FXML private Button learnButton;
    @FXML private Button testButton;
    @FXML private Button cultureButton;
    @FXML private Label statusLabel;
    @FXML private Label voiceStatusLabel;
    
    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 700;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        statusLabel.setText("Рәхим итегез! / Добро пожаловать!");
        
        updateVoiceStatus();
        
        dictionaryButton.setOnAction(e -> openDictionary());
        learnButton.setOnAction(e -> openLearn());
        testButton.setOnAction(e -> openTest());
        cultureButton.setOnAction(e -> openCultureSection());
    }
    
    private void updateVoiceStatus() {
        TTSService tts = TTSService.getInstance();
        String status = tts.getStatus();
        
        if (tts.isAvailable()) {
            voiceStatusLabel.setStyle("-fx-text-fill: #2c7a4c; -fx-font-size: 10px; -fx-font-style: italic;");
            voiceStatusLabel.setText("✅ " + status);
            voiceStatusLabel.setOnMouseClicked(null);
        } else {
            voiceStatusLabel.setStyle("-fx-text-fill: #b4654d; -fx-font-size: 10px; -fx-font-style: italic;");
            voiceStatusLabel.setText("⚠️ " + status);
            voiceStatusLabel.setOnMouseClicked(e -> showTTSHelp());
            Tooltip tooltip = new Tooltip("Нажмите для инструкции по установке");
            Tooltip.install(voiceStatusLabel, tooltip);
        }
    }
    
    private void showTTSHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Настройка голоса");
        alert.setHeaderText("Для озвучивания слов на татарском");
        
        Hyperlink link = new Hyperlink("https://github.com/RHVoice/RHVoice/releases");
        link.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(
                    new java.net.URI("https://github.com/RHVoice/RHVoice/releases")
                );
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        Label instructionLabel = new Label(
            "Установите RH Voice с голосом Талгат:\n\n" +
            "1. Скачайте установщик по ссылке выше\n" +
            "2. Запустите установку\n" +
            "3. Выберите компонент 'Talgon' (татарский голос)\n" +
            "4. Завершите установку\n" +
            "5. Перезапустите приложение"
        );
        instructionLabel.setWrapText(true);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(link, instructionLabel);
        
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefWidth(400);
        alert.showAndWait();
        
        updateVoiceStatus();
    }
    
    // ========== ВСПОМОГАТЕЛЬНЫЙ МЕТОД ДЛЯ ЗАГРУЗКИ CSS ==========
    private void loadCss(Scene scene) {
        try {
            URL cssUrl = getClass().getResource("/styles/main.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
                System.out.println("✅ CSS загружен для окна: " + scene);
            } else {
                System.err.println("❌ CSS не найден");
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки CSS: " + e.getMessage());
        }
    }
    // ============================================================
    
    private void openDictionary() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DictionaryView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Сүзлек / Словарь");
            
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT); // Увеличенный размер
            loadCss(scene); // ЗАГРУЖАЕМ CSS ДЛЯ ЭТОГО ОКНА
            
            stage.setScene(scene);
            stage.show();
            statusLabel.setText("✅ Словарь открыт");
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("❌ Ошибка открытия словаря");
        }
    }

    private void openLearn() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LearnView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Өйрән / Изучение");
            
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT); // Увеличенный размер
            loadCss(scene); // ЗАГРУЖАЕМ CSS ДЛЯ ЭТОГО ОКНА
            
            stage.setScene(scene);
            stage.show();
            statusLabel.setText("✅ Режим обучения открыт");
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("❌ Ошибка открытия обучения");
        }
    }
    
    private void openTest() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TestView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Тесты и упражнения / Сынау һәм күнегүләр");
            
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            loadCss(scene);
            
            stage.setScene(scene);
            stage.show();
            statusLabel.setText("✅ Тесты открыты");
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("❌ Ошибка открытия тестов");
        }
    }
    
    private void openCultureSection() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CultureView.fxml"));
            Parent root = loader.load();
            
            Stage cultureStage = new Stage();
            cultureStage.setTitle("Китап - Татарская культура");
            cultureStage.setScene(new Scene(root, 1000, 700));
            cultureStage.getScene().getStylesheets().add(getClass().getResource("/styles/main.css").toExternalForm());
            
            // Делаем модальным (опционально - блокирует главное окно пока открыто)
            cultureStage.initModality(javafx.stage.Modality.NONE);
            
            // Показываем окно
            cultureStage.show();
            
        } catch (Exception e) {
            e.printStackTrace();
            showErrorAlert("Ошибка", "Не удалось открыть раздел культуры: " + e.getMessage());
        }
    }
    
    private void showNotImplementedDialog(String feature) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("В разработке");
        alert.setHeaderText(feature);
        alert.setContentText("Этот раздел находится в разработке!\n\n" +
                           "Скоро здесь появится:\n" +
                           "• Тесты на знание слов\n" +
                           "• Упражнения на перевод\n" +
                           "• Проверка произношения");
        alert.showAndWait();
    }
    
    private void showErrorAlert(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    public void refreshVoiceStatus() {
        updateVoiceStatus();
    }
}