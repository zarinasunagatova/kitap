package com.tatar.learn.controllers;

import com.tatar.learn.services.BackupManager;
import com.tatar.learn.services.DatabaseService;
import com.tatar.learn.services.ImportService;
import com.tatar.learn.services.TTSService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.sql.SQLException;
import java.util.Optional;
import java.util.ResourceBundle;

public class MainController implements Initializable {
    
    @FXML private Button lessonsButton;
    @FXML private Button dictionaryButton;
    @FXML private Button cardsButton;
    @FXML private Button testButton;
    @FXML private Button cultureButton;
    @FXML private Button backupButton;
    @FXML private Label statusLabel;
    @FXML private Label voiceStatusLabel;
    @FXML private Label dbStatusLabel;
    
    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 700;
    
    private boolean databaseAvailable = false;
    
    private BackupManager backupManager;
    private ImportService importService;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        statusLabel.setText("Рәхим итегез! / Добро пожаловать!");
        
        updateVoiceStatus();
        checkDatabaseStatus();
        
        initServices();
        
        lessonsButton.setOnAction(e -> openLessons());
        dictionaryButton.setOnAction(e -> openDictionary());
        cardsButton.setOnAction(e -> openCards());
        testButton.setOnAction(e -> openTest());
        cultureButton.setOnAction(e -> openCultureSection());
        backupButton.setOnAction(e -> openBackupDialog());
    }
    
    private void initServices() {
        try {
            backupManager = BackupManager.getInstance();
            System.out.println("✅ BackupManager доступен");
        } catch (SQLException e) {
            System.err.println("⚠️ BackupManager не доступен: " + e.getMessage());
            backupManager = null;
        }
        
        try {
            importService = ImportService.getInstance();
            System.out.println("✅ ImportService доступен");
        } catch (SQLException e) {
            System.err.println("⚠️ ImportService не доступен: " + e.getMessage());
            importService = null;
        }
    }
    
    /**
     * Открывает диалог управления бэкапами и импортом 
     */
    private void openBackupDialog() {
        if (!ensureDatabaseAvailable("бэкап/импорт")) {
            return;
        }
        
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("💾 Бэкап и восстановление");
        dialog.setHeaderText("Сохранение и восстановление данных");
        setAlertIcon(dialog);
        ButtonType backupBtn = new ButtonType("📀 Создать бэкап");
        ButtonType restoreBtn = new ButtonType("🔄 Восстановить из бэкапа");
        ButtonType cancelBtn = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        dialog.getDialogPane().getButtonTypes().setAll(backupBtn, restoreBtn, cancelBtn);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label infoLabel = new Label(
            "📌 Бэкап сохраняет ТОЛЬКО слова из словаря\n" +
            "   • Татарские слова\n" +
            "   • Русские переводы\n" +
            "   • Категории и примеры\n" +
            "   • Статистику изучения (timesCorrect, timesWrong)\n\n" +
            "⚠️ Прогресс по урокам и тестам НЕ сохраняется"
        );
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;");
        
        content.getChildren().add(infoLabel);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(450);
        
        Optional<ButtonType> result = dialog.showAndWait();
        
        if (result.isPresent()) {
            if (result.get() == backupBtn) {
                showBackupTypeDialog(); 
            } else if (result.get() == restoreBtn) {
                showRestoreDialog(); 
            }
        }
    }

    /**
     * Показывает диалог выбора типа бэкапа
     */
    private void showBackupTypeDialog() {
        if (backupManager == null) {
            showErrorAlert("Бэкап недоступен", "Сервис бэкапов не инициализирован.");
            return;
        }
        
        Alert typeDialog = new Alert(Alert.AlertType.CONFIRMATION);
        typeDialog.setTitle("Создание бэкапа");
        typeDialog.setHeaderText("Выберите тип бэкапа");
        setAlertIcon(typeDialog);
        ButtonType manualType = new ButtonType("📁 Ручной бэкап (сейчас)");
        ButtonType dailyType = new ButtonType("📅 Ежедневный бэкап");
        ButtonType cancelBtn = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        typeDialog.getDialogPane().getButtonTypes().setAll(manualType, dailyType, cancelBtn);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        Label infoLabel = new Label(
            "• Ручной бэкап - создает копию прямо сейчас\n" +
            "• Ежедневный бэкап - будет создаваться автоматически раз в день"
        );
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        content.getChildren().add(infoLabel);
        typeDialog.getDialogPane().setContent(content);
        
        Optional<ButtonType> result = typeDialog.showAndWait();
        
        if (result.isPresent() && result.get() != cancelBtn) {
            try {
                String backupPath = null;
                if (result.get() == manualType) {
                    backupPath = backupManager.createManualBackup();
                } else if (result.get() == dailyType) {
                    backupManager.createDailyBackup();
                }
                
                if (backupPath != null || result.get() == dailyType) {
                    String msg = result.get() == manualType ? 
                        "Бэкап создан: " + backupPath : 
                        "Ежедневный бэкап запланирован";
                    showInfoAlert("✅ Бэкап создан", msg);
                    statusLabel.setText("✅ " + msg);
                } else {
                    showErrorAlert("Ошибка", "Не удалось создать бэкап");
                }
            } catch (Exception e) {
                showErrorAlert("Ошибка бэкапа", e.getMessage());
            }
        }
    }

    /**
     * Показывает диалог восстановления из бэкапа
     */
    private void showRestoreDialog() {
        if (importService == null) {
            showErrorAlert("Восстановление недоступно", "Сервис импорта не инициализирован.");
            return;
        }
        
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Восстановление данных");
        confirmDialog.setHeaderText("⚠️ Восстановление из бэкапа");
        confirmDialog.setContentText(
            "ВНИМАНИЕ! Восстановление ЗАМЕНИТ все текущие данные на данные из бэкапа.\n\n" +
            "• Все текущие слова будут заменены\n" +
            "Продолжить?"
        );
        setAlertIcon(confirmDialog);
        ButtonType yesBtn = new ButtonType("Да, восстановить");
        ButtonType noBtn = new ButtonType("Нет, отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmDialog.getButtonTypes().setAll(yesBtn, noBtn);
        
        Optional<ButtonType> result = confirmDialog.showAndWait();
        
        if (result.isPresent() && result.get() == yesBtn) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выберите файл бэкапа (.json)");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Бэкап файлы", "*.json")
            );
            fileChooser.setInitialDirectory(new File("backups"));
            
            File selectedFile = fileChooser.showOpenDialog(null);
            if (selectedFile != null) {
                try {
                    // Используем полный импорт с перезаписью
                    ImportService.ImportResult importResult = importService.importFullWithProgress(
                        selectedFile.getAbsolutePath(), 
                        true  // перезаписать все
                    );
                    
                    if (importResult.isSuccess()) {
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("Восстановление завершено");
                        successAlert.setHeaderText("✅ Данные успешно восстановлены");
                        successAlert.setContentText(
                            "Восстановлено слов: " + importResult.getAdded() + "\n" +
                            "Пропущено: " + importResult.getSkipped() + "\n\n" +
                            "Рекомендуется перезапустить приложение."
                        );
                        
                        ButtonType restartBtn = new ButtonType("Перезапустить сейчас");
                        ButtonType laterBtn = new ButtonType("Позже");
                        successAlert.getButtonTypes().setAll(restartBtn, laterBtn);
                        
                        Optional<ButtonType> restartResult = successAlert.showAndWait();
                        if (restartResult.isPresent() && restartResult.get() == restartBtn) {
                            Platform.exit();
                            // Можно добавить автоматический перезапуск
                        }
                    } else {
                        showErrorAlert("Ошибка восстановления", importResult.getMessage());
                    }
                    
                    statusLabel.setText(importResult.getMessage());
                } catch (Exception e) {
                    showErrorAlert("Ошибка", "Не удалось восстановить данные: " + e.getMessage());
                }
            }
        }
    }
    
    private void openLessons() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TopicsView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle(" Уроки / Дәресләр ");
            stage.setScene(new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT));
            loadCss(stage.getScene());
            setStageIcon(stage);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void openDictionary() {
        if (!ensureDatabaseAvailable("словарь")) {
            return;
        }
        
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DictionaryView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle(" Словарь / Сүзлек ");
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
    
    private void openCards() {
        if (!ensureDatabaseAvailable("карточки")) {
            return;
        }
        
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LearnView.fxml"));
            Parent root = loader.load();
            
            LearnController learnController = loader.getController();
            learnController.setMode("cards");
            
            Stage stage = new Stage();
            stage.setTitle(" Карточки / Карталар ");
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
            cultureStage.setTitle(" Культура / Мәдәният ");
            cultureStage.setScene(new Scene(root, 1000, 700));
            setStageIcon(cultureStage);
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
                System.err.println("⚠️ CSS не найден");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Ошибка загрузки CSS: " + e.getMessage());
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
    
    private void showInfoAlert(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Информация");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    public void refreshVoiceStatus() {
        updateVoiceStatus();
    }
    
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
}