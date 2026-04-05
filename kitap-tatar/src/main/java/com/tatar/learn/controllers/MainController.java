package com.tatar.learn.controllers;

import com.tatar.learn.services.DatabaseService;
import com.tatar.learn.services.TTSService;
import javafx.application.Platform;
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
    @FXML private Label dbStatusLabel;  // ← ДОБАВИТЬ В FXML (или использовать statusLabel)
    
    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 700;
    
    // Флаг для отслеживания состояния БД
    private boolean databaseAvailable = false;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        statusLabel.setText("Рәхим итегез! / Добро пожаловать!");
        
        updateVoiceStatus();
        checkDatabaseStatus();  // ← ДОБАВИТЬ ПРОВЕРКУ БД
        
        dictionaryButton.setOnAction(e -> openDictionary());
        learnButton.setOnAction(e -> openLearn());
        testButton.setOnAction(e -> openTest());
        cultureButton.setOnAction(e -> openCultureSection());
    }
    
    /**
     * Проверяет состояние базы данных при запуске
     */
    private void checkDatabaseStatus() {
        try {
            DatabaseService dbService = DatabaseService.getInstance();
            databaseAvailable = dbService.isHealthy();
            
            if (databaseAvailable) {
                System.out.println("✅ Database is healthy");
                if (dbStatusLabel != null) {
                    dbStatusLabel.setText("✅ База данных: OK");
                    dbStatusLabel.setStyle("-fx-text-fill: #2c7a4c;");
                }
            } else {
                databaseAvailable = false;
                System.err.println("⚠️ Database is not healthy");
                if (dbStatusLabel != null) {
                    dbStatusLabel.setText("⚠️ База данных: проблемы");
                    dbStatusLabel.setStyle("-fx-text-fill: #b4654d;");
                }
                showDatabaseWarning();
            }
        } catch (Exception e) {
            databaseAvailable = false;
            System.err.println("❌ Database initialization failed: " + e.getMessage());
            if (dbStatusLabel != null) {
                dbStatusLabel.setText("❌ База данных: ошибка");
                dbStatusLabel.setStyle("-fx-text-fill: #d45d79;");
            }
            showDatabaseErrorDialog(e.getMessage());
        }
    }
    
    /**
     * Показывает предупреждение, если БД работает с проблемами
     */
    private void showDatabaseWarning() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Внимание");
        alert.setHeaderText("База данных работает с проблемами");
        alert.setContentText(
            "Некоторые функции могут работать некорректно.\n\n" +
            "Рекомендуется перезапустить приложение.\n\n" +
            "Если проблема повторяется, проверьте:\n" +
            "• Права на запись в папку пользователя\n" +
            "• Наличие свободного места на диске"
        );
        alert.showAndWait();
    }
    
    /**
     * Показывает критическую ошибку БД
     */
    private void showDatabaseErrorDialog(String errorMessage) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Критическая ошибка");
        alert.setHeaderText("Не удалось подключиться к базе данных");
        alert.setContentText(
            "Приложение не может работать без базы данных.\n\n" +
            "Ошибка: " + errorMessage + "\n\n" +
            "Попробуйте:\n" +
            "1. Перезапустить приложение\n" +
            "2. Проверить права на запись в папку пользователя\n" +
            "3. Удалить файл kitap.db и перезапустить приложение"
        );
        
        ButtonType retryButton = new ButtonType("Повторить", ButtonBar.ButtonData.OK_DONE);
        ButtonType exitButton = new ButtonType("Выйти", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        alert.getButtonTypes().setAll(retryButton, exitButton);
        
        alert.showAndWait().ifPresent(response -> {
            if (response == retryButton) {
                checkDatabaseStatus();  // Повторяем проверку
            } else {
                Platform.exit();  // Выходим из приложения
            }
        });
    }
    
    /**
     * Проверяет, доступна ли БД перед открытием окна
     */
    private boolean ensureDatabaseAvailable(String featureName) {
        if (!databaseAvailable) {
            showErrorAlert(
                "База данных недоступна", 
                "Невозможно открыть " + featureName + ".\n\n" +
                "Пожалуйста, перезапустите приложение.\n" +
                "Если проблема повторяется, проверьте установку."
            );
            return false;
        }
        
        // Дополнительная проверка на лету
        try {
            DatabaseService dbService = DatabaseService.getInstance();
            if (!dbService.isHealthy()) {
                databaseAvailable = false;
                showErrorAlert(
                    "База данных недоступна",
                    "Соединение с базой данных потеряно.\nПожалуйста, перезапустите приложение."
                );
                return false;
            }
        } catch (Exception e) {
            databaseAvailable = false;
            showErrorAlert("Ошибка базы данных", e.getMessage());
            return false;
        }
        
        return true;
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
        // ← ДОБАВИТЬ ПРОВЕРКУ БД
        if (!ensureDatabaseAvailable("словарь")) {
            return;
        }
        
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DictionaryView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Сүзлек / Словарь");
            
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            loadCss(scene);
            
            stage.setScene(scene);
            stage.show();
            statusLabel.setText("✅ Словарь открыт");
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("❌ Ошибка открытия словаря");
            showErrorAlert("Ошибка", "Не удалось открыть словарь: " + e.getMessage());
        }
    }

    private void openLearn() {
        // ← ДОБАВИТЬ ПРОВЕРКУ БД
        if (!ensureDatabaseAvailable("режим обучения")) {
            return;
        }
        
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LearnView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Өйрән / Изучение");
            
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            loadCss(scene);
            
            stage.setScene(scene);
            stage.show();
            statusLabel.setText("✅ Режим обучения открыт");
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("❌ Ошибка открытия обучения");
            showErrorAlert("Ошибка", "Не удалось открыть обучение: " + e.getMessage());
        }
    }
    
    private void openTest() {
        // Тесты НЕ зависят от DatabaseService (используют GrammarService)
        // Поэтому проверку БД можно НЕ делать
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
            showErrorAlert("Ошибка", "Не удалось открыть тесты: " + e.getMessage());
        }
    }
    
    private void openCultureSection() {
        // Культура НЕ зависит от DatabaseService
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CultureView.fxml"));
            Parent root = loader.load();
            
            Stage cultureStage = new Stage();
            cultureStage.setTitle("Китап - Татарская культура");
            cultureStage.setScene(new Scene(root, 1000, 700));
            cultureStage.getScene().getStylesheets().add(getClass().getResource("/styles/main.css").toExternalForm());
            
            cultureStage.initModality(javafx.stage.Modality.NONE);
            cultureStage.show();
            
        } catch (Exception e) {
            e.printStackTrace();
            showErrorAlert("Ошибка", "Не удалось открыть раздел культуры: " + e.getMessage());
        }
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