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
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;

public class MainController implements Initializable {
    
    @FXML private Button lessonsButton;      // Уроки
    @FXML private Button dictionaryButton;    // Словарь
    @FXML private Button cardsButton;         // Карточки
    @FXML private Button testButton;          // Тесты
    @FXML private Button cultureButton;       // Культура
    @FXML private Label statusLabel;
    @FXML private Label voiceStatusLabel;
    @FXML private Label dbStatusLabel;
    
    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 700;
    
    private boolean databaseAvailable = false;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        statusLabel.setText("Рәхим итегез! / Добро пожаловать!");
        
        updateVoiceStatus();
        checkDatabaseStatus();
        
        lessonsButton.setOnAction(e -> openLessons());
        dictionaryButton.setOnAction(e -> openDictionary());
        cardsButton.setOnAction(e -> openCards());
        testButton.setOnAction(e -> openTest());
        cultureButton.setOnAction(e -> openCultureSection());
    }
    
    /**
     * Открывает раздел УРОКИ (изучение новых тем)
     */
 // MainController.java
    private void openLessons() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TopicsView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("📚 Уроки - Выберите тему");
            stage.setScene(new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT));
            loadCss(stage.getScene());
            setStageIcon(stage);
            stage.show();
            
            // НЕ ЗАКРЫВАЕМ ГЛАВНОЕ ОКНО!
            // Просто показываем новое поверх
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Открывает СЛОВАРЬ (только слова из пройденных тем)
     */
    private void openDictionary() {
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
            setStageIcon(stage);
            stage.setScene(scene);
            stage.show();
            statusLabel.setText("✅ Словарь открыт");
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("❌ Ошибка открытия словаря");
            showErrorAlert("Ошибка", "Не удалось открыть словарь: " + e.getMessage());
        }
    }
    
    /**
     * Открывает КАРТОЧКИ (повторение слов по SM-2)
     */
    private void openCards() {
        if (!ensureDatabaseAvailable("карточки")) {
            return;
        }
        
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LearnView.fxml"));
            Parent root = loader.load();
            
            LearnController learnController = loader.getController();
            learnController.setMode("cards"); // Режим карточек
            
            Stage stage = new Stage();
            stage.setTitle("🃏 Карточки - Повторение слов");
            
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            loadCss(scene);
            setStageIcon(stage);
            stage.setScene(scene);
            stage.show();
            statusLabel.setText("✅ Карточки открыты");
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("❌ Ошибка открытия карточек");
            showErrorAlert("Ошибка", "Не удалось открыть карточки: " + e.getMessage());
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
            setStageIcon(stage);
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
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CultureView.fxml"));
            Parent root = loader.load();
            
            Stage cultureStage = new Stage();
            cultureStage.setTitle("Китап - Татарская культура");
            cultureStage.setScene(new Scene(root, 1000, 700));
            setStageIcon(cultureStage);
            // Безопасная загрузка CSS
            try {
                URL cssUrl = getClass().getResource("/styles/main.css");
                if (cssUrl != null) {
                    cultureStage.getScene().getStylesheets().add(cssUrl.toExternalForm());
                }
            } catch (Exception e) {
                System.err.println("⚠️ CSS не загружен для раздела культуры: " + e.getMessage());
            }
            
            cultureStage.initModality(javafx.stage.Modality.NONE);
            cultureStage.show();
            
        } catch (Exception e) {
            e.printStackTrace();
            showErrorAlert("Ошибка", "Не удалось открыть раздел культуры: " + e.getMessage());
        }
    }
    
    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========
    
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
    
    private boolean ensureDatabaseAvailable(String featureName) {
        if (!databaseAvailable) {
            showErrorAlert(
                "База данных недоступна", 
                "Невозможно открыть " + featureName + ".\n\n" +
                "Пожалуйста, перезапустите приложение."
            );
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
        } else {
            voiceStatusLabel.setStyle("-fx-text-fill: #b4654d; -fx-font-size: 10px; -fx-font-style: italic;");
            voiceStatusLabel.setText("⚠️ " + status);
            voiceStatusLabel.setOnMouseClicked(e -> showTTSHelp());
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
    
    private void loadCss(Scene scene) {
        try {
            URL cssUrl = getClass().getResource("/styles/main.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
                System.out.println("✅ CSS загружен");
            } else {
                System.err.println("⚠️ CSS не найден, приложение продолжит работу без стилей");
                // НЕ ПАДАЕМ — просто логируем предупреждение
            }
        } catch (Exception e) {
            System.err.println("⚠️ Ошибка загрузки CSS: " + e.getMessage());
            // НЕ ПАДАЕМ — приложение работает без CSS
        }
    }
    
    private void showDatabaseWarning() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Внимание");
        alert.setHeaderText("База данных работает с проблемами");
        alert.setContentText("Некоторые функции могут работать некорректно.\nРекомендуется перезапустить приложение.");
        alert.showAndWait();
    }
    
    private void showDatabaseErrorDialog(String errorMessage) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Критическая ошибка");
        alert.setHeaderText("Не удалось подключиться к базе данных");
        alert.setContentText("Ошибка: " + errorMessage + "\n\nПопробуйте перезапустить приложение.");
        
        ButtonType retryButton = new ButtonType("Повторить", ButtonBar.ButtonData.OK_DONE);
        ButtonType exitButton = new ButtonType("Выйти", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        alert.getButtonTypes().setAll(retryButton, exitButton);
        
        alert.showAndWait().ifPresent(response -> {
            if (response == retryButton) {
                checkDatabaseStatus();
            } else {
                Platform.exit();
            }
        });
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
    
    /**
     * Устанавливает иконку для окна (Stage)
     */
    private void setStageIcon(Stage stage) {
        try {
            URL iconUrl = getClass().getResource("/images/kitap.png");
            if (iconUrl == null) {
                iconUrl = getClass().getClassLoader().getResource("images/kitap.png");
            }
            if (iconUrl != null) {
                Image icon = new Image(iconUrl.openStream());
                stage.getIcons().add(icon);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Icon not loaded for stage: " + e.getMessage());
        }
    }
    
    /**
     * Безопасная загрузка иконки для диалоговых окон
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
            System.err.println("⚠️ Не удалось загрузить иконку для диалога: " + e.getMessage());
        }
        return null;
    }

    /**
     * Устанавливает иконку для Alert диалога
     */
    private void setAlertIcon(Alert alert) {
        try {
            Image icon = loadDialogIcon();
            if (icon != null) {
                Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
                stage.getIcons().add(icon);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Не удалось установить иконку для Alert: " + e.getMessage());
        }
    }
}