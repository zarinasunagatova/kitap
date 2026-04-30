package com.tatar.learn;

import com.tatar.learn.services.BackupManager;
import com.tatar.learn.services.DatabaseService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.net.URL;
import java.sql.SQLException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitapApp extends Application {
    
    private BackupManager backupManager;
    private DatabaseService dbService;
    private static final Logger log = LoggerFactory.getLogger(KitapApp.class);
    static {
        System.setProperty("javafx.embed.singleThread", "true");
        System.setProperty("prism.order", "sw");
    }
   
    @Override
    public void start(Stage primaryStage) {
        try {
        	log.info("📚 Инициализация базы данных...");
            
            // Инициализируем БД с обработкой ошибок
            if (!initDatabase()) {
                showFatalDatabaseError(primaryStage);
                return;
            }
            
            // Инициализируем менеджер бэкапов
            if (!initBackupManager()) {
            	log.error("⚠️ BackupManager не инициализирован, бэкапы отключены");
                // Продолжаем работу без бэкапов
            }
            
            log.info("🎨 Загрузка интерфейса...");
            loadMainInterface(primaryStage);
            
        } catch (Exception e) {
        	log.error("❌ Критическая ошибка: " + e.getMessage());
        	log.error("Ошибка", e);
            showFatalErrorDialog(primaryStage, e.getMessage());
        }
    }
    
    /**
     * Инициализация базы данных с корректной обработкой ошибок
     * @return true если успешно, false если ошибка
     */
    private boolean initDatabase() {
        try {
            dbService = DatabaseService.getInstance();
            
            // Проверяем, что БД действительно работает
            if (dbService == null) {
            	log.error("❌ DatabaseService.getInstance() вернул null");
                return false;
            }
            
            // Проверяем здоровье БД
            if (!dbService.isHealthy()) {
            	log.error("❌ Database is not healthy");
                return false;
            }
            
            log.info("✅ Database initialized successfully");
            return true;
            
        } catch (SQLException e) {
        	log.error("❌ Database initialization failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Инициализация менеджера бэкапов
     * @return true если успешно, false если ошибка (бэкапы будут отключены)
     */
    private boolean initBackupManager() {
        try {
            backupManager = BackupManager.getInstance();
            log.info("✅ BackupManager initialized successfully");
            return true;
        } catch (SQLException e) {
        	log.error("⚠️ BackupManager initialization failed: " + e.getMessage());
        	log.error("   Backups will be disabled");
            backupManager = null;
            return false;
        }
    }
    
    /**
     * Загрузка основного интерфейса
     */
    private void loadMainInterface(Stage primaryStage) {
        try {
            URL fxmlUrl = getClass().getResource("/fxml/MainView.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("MainView.fxml not found in resources");
            }
            
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            
            Scene scene = new Scene(root, 1200, 800);
            
            // Загружаем иконку
            try {
                URL iconUrl = getClass().getResource("/images/kitap.png");
                if (iconUrl != null) {
                    Image icon = new Image(iconUrl.openStream());
                    primaryStage.getIcons().add(icon);
                }
            } catch (Exception e) {
            	log.error("⚠️ Icon not loaded: " + e.getMessage());
            }
            
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(700);
            
            // Загружаем CSS
            loadCss(scene);
            
            primaryStage.setTitle("Kitap - Изучение татарского языка");
            primaryStage.setScene(scene);
            
            // Настраиваем обработчик закрытия окна
            setupShutdownHandler(primaryStage);
            
            primaryStage.show();
            
            log.info("✅ Приложение успешно запущено!");
            
        } catch (Exception e) {
        	log.error("❌ Failed to load main interface: " + e.getMessage());
            throw new RuntimeException("Cannot load main interface", e);
        }
    }
    
    /**
     * Загрузка CSS стилей
     */
    private void loadCss(Scene scene) {
        try {
            URL cssUrl = getClass().getResource("/styles/main.css");
            log.info("CSS URL: " + cssUrl);
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
                log.info("✅ CSS стили загружены");
            } else {
            	log.error("❌ CSS не найден по пути: /styles/main.css");
            }
        } catch (Exception e) {
        	log.error("❌ Ошибка загрузки стилей: " + e.getMessage());
        }
    }
    
    /**
     * Настройка обработчика закрытия окна
     */
    private void setupShutdownHandler(Stage primaryStage) {
        primaryStage.setOnCloseRequest(event -> {
        	log.info("📝 Shutting down...");
            
            // Создаем бэкап, если BackupManager доступен
            if (backupManager != null) {
                try {
                	log.info("📝 Creating shutdown backup...");
                    backupManager.createShutdownBackup();
                } catch (Exception e) {
                	log.error("⚠️ Failed to create shutdown backup: " + e.getMessage());
                }
            }
            
            // Закрываем соединение с БД
            if (dbService != null) {
                try {
                	log.info("📝 Closing database connection...");
                    dbService.close();
                    log.info("✅ Database connection closed");
                } catch (Exception e) {  // ← Exception, не SQLException
                	log.error("⚠️ Error closing database: " + e.getMessage());
                }
            }
            
            // Останавливаем BackupManager
            if (backupManager != null) {
                try {
                    backupManager.shutdown();
                } catch (Exception e) {
                	log.error("⚠️ Error shutting down BackupManager: " + e.getMessage());
                }
            }
            
            log.info("✅ Shutdown complete");
        });
    }
    
    /**
     * Показывает диалог с фатальной ошибкой БД
     */
    private void showFatalDatabaseError(Stage primaryStage) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Критическая ошибка");
        alert.setHeaderText("Не удалось подключиться к базе данных");
        alert.setContentText(
            "Приложение не может работать без базы данных.\n\n" +
            "Возможные решения:\n" +
            "1. Перезапустите приложение\n" +
            "2. Проверьте права на запись в папку пользователя\n" +
            "3. Удалите файл kitap.db и перезапустите приложение\n\n" +
            "Если проблема повторяется, обратитесь в поддержку."
        );
        
        ButtonType retryButton = new ButtonType("Повторить");
        ButtonType exitButton = new ButtonType("Выйти");
        alert.getButtonTypes().setAll(retryButton, exitButton);
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == retryButton) {
            // Повторяем попытку инициализации
            start(primaryStage);
        } else {
            Platform.exit();
        }
    }
    
    /**
     * Показывает фатальную ошибку с возможностью выхода
     */
    private void showFatalErrorDialog(Stage stage, String errorMessage) {
        VBox vbox = new VBox(20);
        vbox.setStyle("-fx-alignment: center; -fx-padding: 30; -fx-background-color: #f8f9fa;");
        
        Label titleLabel = new Label("Китап - Татар теле");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: #2B6E4C; -fx-font-weight: bold;");
        
        Label errorLabel = new Label("❌ Критическая ошибка");
        errorLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #d45d79; -fx-font-weight: bold;");
        
        TextArea errorArea = new TextArea();
        errorArea.setEditable(false);
        errorArea.setPrefRowCount(8);
        errorArea.setText(
            "Ошибка: " + errorMessage + "\n\n" +
            "Попробуйте:\n" +
            "1. Запустите: mvn clean javafx:run\n" +
            "2. Проверьте наличие всех FXML файлов в resources/fxml/\n" +
            "3. Проверьте структуру проекта"
        );
        
        javafx.scene.control.Button exitButton = new javafx.scene.control.Button("Выйти");
        exitButton.setStyle("-fx-background-color: #d45d79; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 5;");
        exitButton.setOnAction(e -> Platform.exit());
        
        vbox.getChildren().addAll(titleLabel, errorLabel, errorArea, exitButton);
        
        Scene scene = new Scene(vbox, 700, 500);
        stage.setTitle("Kitap - Режим восстановления");
        stage.setScene(scene);
        stage.show();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}