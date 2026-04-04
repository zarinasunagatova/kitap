package com.tatar.learn;

import com.tatar.learn.services.BackupManager;
import com.tatar.learn.services.DatabaseService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import java.net.URL;

public class KitapApp extends Application {
    
    private BackupManager backupManager;
    
    static {
        System.setProperty("javafx.embed.singleThread", "true");
        System.setProperty("prism.order", "sw");
    }
    
    @Override
    public void start(Stage primaryStage) {
        try {
            System.out.println("📚 Инициализация базы данных...");
            DatabaseService.getInstance();
            
            // Инициализируем менеджер бэкапов
            backupManager = BackupManager.getInstance();
            
            System.out.println("🎨 Загрузка интерфейса...");
            
            URL fxmlUrl = getClass().getResource("/fxml/MainView.fxml");
            if (fxmlUrl == null) {
                System.err.println("❌ MainView.fxml не найден!");
                showFallbackWindow(primaryStage);
                return;
            }
            
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            
            Scene scene = new Scene(root, 1200, 800);
            Image icon = new Image(getClass().getResourceAsStream("/images/kitap.png"));
            primaryStage.getIcons().add(icon);
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(700);
            
            // Загружаем CSS - ИСПРАВЛЕН ПУТЬ
            try {
                URL cssUrl = getClass().getResource("/styles/main.css");
                System.out.println("CSS URL: " + cssUrl);
                if (cssUrl != null) {
                    scene.getStylesheets().add(cssUrl.toExternalForm());
                    System.out.println("✅ CSS стили загружены");
                } else {
                    System.err.println("❌ CSS не найден по пути: /styles/main.css");
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка загрузки стилей: " + e.getMessage());
            }
            
            primaryStage.setTitle("Kitap - Изучение татарского языка");
            primaryStage.setScene(scene);
            primaryStage.show();
            
            // ДОБАВЛЕНО: автоматический бэкап при закрытии
            primaryStage.setOnCloseRequest(e -> {
                System.out.println("📝 Создание бэкапа перед закрытием...");
                backupManager.createShutdownBackup();
                
                System.out.println("📝 Закрытие соединения с БД...");
                DatabaseService.getInstance().close();
                
                backupManager.shutdown();
            });
            
            System.out.println("✅ Приложение успешно запущено!");
            
        } catch (Exception e) {
            System.err.println("❌ Критическая ошибка:");
            e.printStackTrace();
            showFallbackWindow(primaryStage);
        }
    }
    
    private void showFallbackWindow(Stage stage) {
        javafx.scene.control.Label label = new javafx.scene.control.Label("Китап - Татар теле");
        label.setStyle("-fx-font-size: 24px; -fx-text-fill: #2B6E4C; -fx-font-weight: bold;");
        
        javafx.scene.control.TextArea errorArea = new javafx.scene.control.TextArea();
        errorArea.setEditable(false);
        errorArea.setPrefRowCount(5);
        errorArea.setText("Ошибка загрузки:\n" +
                         "1. Проверьте наличие MainView.fxml в resources/fxml/\n" +
                         "2. Запустите: mvn clean javafx:run\n" +
                         "3. Проверьте структуру проекта");
        
        javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(20, label, errorArea);
        vbox.setStyle("-fx-alignment: center; -fx-padding: 30; -fx-background-color: #f8f9fa;");
        
        Scene scene = new Scene(vbox, 700, 400);
        stage.setTitle("Kitap - Режим восстановления");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
} 
