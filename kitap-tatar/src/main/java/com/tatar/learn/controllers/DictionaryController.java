package com.tatar.learn.controllers;

import com.tatar.learn.models.Word;
import com.tatar.learn.services.DatabaseService;
import com.tatar.learn.services.TTSService;
import com.tatar.learn.services.TopicsService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class DictionaryController implements Initializable {
    
    @FXML private TableView<Word> wordsTable;
    @FXML private TableColumn<Word, Integer> idColumn;
    @FXML private TableColumn<Word, String> tatarColumn;
    @FXML private TableColumn<Word, String> russianColumn;
    @FXML private TableColumn<Word, Number> timesCorrectColumn;  
    @FXML private TableColumn<Word, Void> actionsColumn;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Button clearButton;
    @FXML private Button addButton;
    @FXML private Label statusLabel;
    @FXML private Label countLabel;
    
    private DatabaseService dbService;
    private ObservableList<Word> wordList;
    private FilteredList<Word> filteredData;
    private boolean databaseAvailable = false;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Инициализируем БД с обработкой ошибок
        if (!initDatabase()) {
            showDatabaseErrorAndDisable();
            return;
        }
        
        wordList = FXCollections.observableArrayList();
        
        setupTableColumns();
        setupActions();
        loadWords();
    }
    
    /**
     * Инициализация базы данных
     * @return true если успешно, false если ошибка
     */
    private boolean initDatabase() {
        try {
            dbService = DatabaseService.getInstance();
            
            if (dbService == null) {
                showErrorOnStatus("❌ База данных недоступна (сервис вернул null)");
                return false;
            }
            
            if (!dbService.isHealthy()) {
                showErrorOnStatus("❌ База данных нездорова");
                return false;
            }
            
            databaseAvailable = true;
            showSuccessOnStatus("✅ База данных подключена");
            return true;
            
        } catch (SQLException e) {
            System.err.println("Database init failed: " + e.getMessage());
            showErrorOnStatus("❌ Ошибка подключения к базе данных: " + e.getMessage());
            databaseAvailable = false;
            return false;
        }
    }
    
    /**
     * Показывает ошибку и отключает функциональность
     */
    private void showDatabaseErrorAndDisable() {
        // Отключаем кнопки
        addButton.setDisable(true);
        searchButton.setDisable(true);
        clearButton.setDisable(true);
        searchField.setDisable(true);
        
        // Показываем сообщение в таблице
        wordsTable.setPlaceholder(new Label(
            "❌ База данных недоступна\n\n" +
            "Пожалуйста, перезапустите приложение.\n" +
            "Если проблема повторяется, проверьте:\n" +
            "• Права на запись в папку пользователя\n" +
            "• Наличие свободного места на диске"
        ));
        
        statusLabel.setText("❌ Критическая ошибка: база данных недоступна");
        countLabel.setText("БД недоступна");
    }
    
    private void showErrorOnStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: #d45d79;");
        }
        System.err.println(message);
    }
    
    private void showSuccessOnStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: #2c7a4c;");
        }
    }
    
    private void setupTableColumns() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        tatarColumn.setCellValueFactory(new PropertyValueFactory<>("tatar"));
        russianColumn.setCellValueFactory(new PropertyValueFactory<>("russian"));
        
        timesCorrectColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleIntegerProperty(
                cellData.getValue().getTimesCorrect()
            )
        );

        timesCorrectColumn.setCellFactory(col -> new TableCell<Word, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("progress-high", "progress-medium", "progress-low", "progress-zero");
                } else {
                    int timesCorrect = item.intValue();
                    setText(timesCorrect + " раз");
                    
                    getStyleClass().removeAll("progress-high", "progress-medium", "progress-low", "progress-zero");
                    getStyleClass().add(getTimesCorrectStyleClass(timesCorrect));
                }
            }
        });
        
        // Колонка с кнопками действий
        actionsColumn.setCellFactory(col -> new TableCell<Word, Void>() {
            private final Button playButton = new Button("🔊");
            private final Button incrementButton = new Button("✓");
            private final Button resetButton = new Button("↺");
            private final Button editButton = new Button("✎");
            private final Button deleteButton = new Button("✗");
            private final HBox pane = new HBox(3, playButton, incrementButton, resetButton, editButton, deleteButton);
            
            {
                playButton.getStyleClass().add("play-button-small");
                playButton.setOnAction(event -> {
                    Word word = getTableView().getItems().get(getIndex());
                    playWord(word);
                });
                
                incrementButton.getStyleClass().add("increment-button");
                incrementButton.setOnAction(event -> {
                    Word word = getTableView().getItems().get(getIndex());
                    incrementTimesCorrect(word);
                });
                
                resetButton.getStyleClass().add("reset-button");
                resetButton.setOnAction(event -> {
                    Word word = getTableView().getItems().get(getIndex());
                    resetProgress(word);
                });
                
                editButton.getStyleClass().add("edit-button");
                editButton.setOnAction(event -> {
                    Word word = getTableView().getItems().get(getIndex());
                    editWord(word);
                });
                
                deleteButton.getStyleClass().add("delete-button");
                deleteButton.setOnAction(event -> {
                    Word word = getTableView().getItems().get(getIndex());
                    deleteWord(word);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });
    }
    
    private String getTimesCorrectStyleClass(int timesCorrect) {
        if (timesCorrect >= 5) return "progress-high";
        if (timesCorrect >= 2) return "progress-medium";
        if (timesCorrect > 0) return "progress-low";
        return "progress-zero";
    }
    
    private void playWord(Word word) {
        if (!databaseAvailable) {
            statusLabel.setText("❌ База данных недоступна");
            return;
        }
        try {
            TTSService.getInstance().speak(word.getTatar());
            statusLabel.setText("🔊 " + word.getTatar());
        } catch (Exception e) {
            statusLabel.setText("❌ Ошибка воспроизведения");
        }
    }
    
    private void setupActions() {
        searchButton.setOnAction(e -> filterWords());
        clearButton.setOnAction(e -> clearFilters());
        addButton.setOnAction(e -> showAddWordDialog());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterWords();
        });
    }
    
 // Добавьте этот метод в DictionaryController.java
    private void loadWords() {
        if (!databaseAvailable) {
            showDatabaseErrorAndDisable();
            return;
        }
        
        try {
            statusLabel.setText("Загрузка слов...");
            wordList.clear();
            
            // Загружаем ТОЛЬКО слова из пройденных тем
            TopicsService topicsService = TopicsService.getInstance();
            List<Word> learnedWords = topicsService.getAllLearnedWords();
            
            wordList.addAll(learnedWords);
            
            System.out.println("Загружено слов из пройденных тем: " + wordList.size());
            
            filteredData = new FilteredList<>(wordList, p -> true);
            SortedList<Word> sortedData = new SortedList<>(filteredData);
            sortedData.comparatorProperty().bind(wordsTable.comparatorProperty());
            
            wordsTable.setItems(sortedData);
            updateCountLabel();
            statusLabel.setText("✅ Загружено " + wordList.size() + " слов из пройденных тем");
        } catch (Exception e) {
            statusLabel.setText("❌ Ошибка загрузки слов: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void filterWords() {
        if (!databaseAvailable) return;
        
        String searchText = searchField.getText().toLowerCase();
        
        filteredData.setPredicate(word -> {
            if (searchText == null || searchText.isEmpty()) {
                return true;
            }
            return word.getTatar().toLowerCase().contains(searchText) ||
                   word.getRussian().toLowerCase().contains(searchText);
        });
        
        updateCountLabel();
    }
    
    private void clearFilters() {
        searchField.clear();
        filterWords();
    }

    private boolean validateWordFields(String tatar, String russian) {
        // Проверка на null и пустоту после trim
        if (tatar == null || tatar.trim().isEmpty()) {
            showValidationError("Татарское слово не может быть пустым!");
            return false;
        }
        
        if (russian == null || russian.trim().isEmpty()) {
            showValidationError("Русский перевод не может быть пустым!");
            return false;
        }
        
        // Проверка минимальной длины (опционально)
        if (tatar.trim().length() < 2) {
            showValidationError("Татарское слово должно содержать хотя бы 2 буквы!");
            return false;
        }
        
        if (russian.trim().length() < 1) {
            showValidationError("Русский перевод должен содержать хотя бы 1 букву!");
            return false;
        }
        
        // Проверка на недопустимые символы (опционально)
        if (!tatar.trim().matches("^[a-zA-Zа-яА-ЯёЁәӘөӨҗҖңҢүҮһҺ\\s\\-']+$")) {
            showValidationError("Татарское слово содержит недопустимые символы!");
            return false;
        }
        
        if (!russian.trim().matches("^[a-zA-Zа-яА-ЯёЁ\\s\\-']+$")) {
            showValidationError("Русское слово содержит недопустимые символы!");
            return false;
        }
        
        return true;
    }

 
    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Ошибка валидации");
        alert.setHeaderText("Некорректные данные");
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void updateCountLabel() {
        int filteredCount = filteredData != null ? filteredData.size() : 0;
        int totalCount = wordList.size();
        countLabel.setText(String.format("Показано: %d из %d слов", filteredCount, totalCount));
    }
    
    private void incrementTimesCorrect(Word word) {
        if (!databaseAvailable) {
            statusLabel.setText("❌ База данных недоступна");
            return;
        }
        
        try {
            word.incrementCorrect();
            dbService.updateWord(word);
            wordsTable.refresh();
            statusLabel.setText("✅ Прогресс увеличен: " + word.getTatar() + 
                               " (" + word.getTimesCorrect() + " раз)");
        } catch (Exception e) {
            statusLabel.setText("❌ Ошибка обновления: " + e.getMessage());
        }
    }
    
    private void resetProgress(Word word) {
        if (!databaseAvailable) {
            statusLabel.setText("❌ База данных недоступна");
            return;
        }
        
        try {
            word.resetProgress();
            dbService.updateWord(word);
            wordsTable.refresh();
            statusLabel.setText("🔄 Прогресс сброшен: " + word.getTatar());
        } catch (Exception e) {
            statusLabel.setText("❌ Ошибка сброса: " + e.getMessage());
        }
    }
    
    private void editWord(Word word) {
        if (!databaseAvailable) {
            statusLabel.setText("❌ База данных недоступна");
            return;
        }
        
        Dialog<Word> dialog = new Dialog<>();
        dialog.setTitle("Редактирование слова");
        dialog.setHeaderText("Редактирование: " + word.getTatar());
        
        ButtonType saveButtonType = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        // Отключаем кнопку "Сохранить" пока поля не заполнены
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField tatarField = new TextField(word.getTatar());
        TextField russianField = new TextField(word.getRussian());
        TextField categoryField = new TextField(word.getCategory());
        
        // Валидация в реальном времени
        tatarField.textProperty().addListener((obs, old, val) -> {
            boolean isValid = !val.trim().isEmpty() && !russianField.getText().trim().isEmpty();
            saveButton.setDisable(!isValid);
            
            if (val.trim().isEmpty()) {
                tatarField.setStyle("-fx-border-color: #e8a898; -fx-border-radius: 3;");
            } else {
                tatarField.setStyle("-fx-border-color: #7cb87a; -fx-border-radius: 3;");
            }
        });
        
        russianField.textProperty().addListener((obs, old, val) -> {
            boolean isValid = !tatarField.getText().trim().isEmpty() && !val.trim().isEmpty();
            saveButton.setDisable(!isValid);
            
            if (val.trim().isEmpty()) {
                russianField.setStyle("-fx-border-color: #e8a898; -fx-border-radius: 3;");
            } else {
                russianField.setStyle("-fx-border-color: #7cb87a; -fx-border-radius: 3;");
            }
        });
        
        // Инициализируем состояние кнопки
        saveButton.setDisable(tatarField.getText().trim().isEmpty() || 
                              russianField.getText().trim().isEmpty());
        
        grid.add(new Label("Татарча:*"), 0, 0);
        grid.add(tatarField, 1, 0);
        grid.add(new Label("Русский:*"), 0, 1);
        grid.add(russianField, 1, 1);
        grid.add(new Label("Категория:"), 0, 2);
        grid.add(categoryField, 1, 2);
        
        Label requiredHint = new Label("* обязательные поля");
        requiredHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");
        grid.add(requiredHint, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String tatar = tatarField.getText();
                String russian = russianField.getText();
                String category = categoryField.getText();
                
                // Валидация перед сохранением
                if (!validateWordFields(tatar, russian)) {
                    return null;
                }
                
                word.setTatar(tatar.trim());
                word.setRussian(russian.trim());
                word.setCategory((category == null || category.trim().isEmpty()) ? "Общее" : category.trim());
                return word;
            }
            return null;
        });
        
        Optional<Word> result = dialog.showAndWait();
        result.ifPresent(updatedWord -> {
            try {
                dbService.updateWord(updatedWord);
                loadWords();
                statusLabel.setText("✅ Слово обновлено: " + updatedWord.getTatar());
            } catch (Exception e) {
                statusLabel.setText("❌ Ошибка обновления: " + e.getMessage());
                showValidationError("Не удалось обновить слово: " + e.getMessage());
            }
        });
    }
    
    private void deleteWord(Word word) {
        if (!databaseAvailable) {
            statusLabel.setText("❌ База данных недоступна");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удаление слова");
        confirm.setHeaderText("Удалить слово: " + word.getTatar() + "?");
        confirm.setContentText("Это действие нельзя отменить!");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                dbService.deleteWord(word.getId());
                loadWords();
                statusLabel.setText("✅ Слово удалено: " + word.getTatar());
            } catch (Exception e) {
                statusLabel.setText("❌ Ошибка удаления: " + e.getMessage());
            }
        }
    }
    
    public void refreshProgress() {
        if (!databaseAvailable) return;
        
        if (wordsTable != null) {
            wordsTable.refresh();
            
            int total = wordList.size();
            int learned = 0;
            for (Word w : wordList) {
                if (w.getTimesCorrect() >= 5) learned++;
            }
            statusLabel.setText(String.format("✅ Выучено: %d из %d слов", learned, total));
        }
    }
    
    private void showAddWordDialog() {
        if (!databaseAvailable) {
            statusLabel.setText("❌ База данных недоступна");
            return;
        }
        
        Dialog<Word> dialog = new Dialog<>();
        dialog.setTitle("Добавление слова");
        dialog.setHeaderText("Новое слово");
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        
        try {
            URL cssUrl = getClass().getResource("/styles/main.css");
            if (cssUrl != null) {
                dialog.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception e) {
            System.err.println("Ошибка загрузки CSS для диалога: " + e.getMessage());
        }
        
        ButtonType addButtonType = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);
        
        // Отключаем кнопку "Добавить" пока поля не заполнены (опционально)
        Button addButton = (Button) dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(true);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField tatarField = new TextField();
        tatarField.setPromptText("Татарское слово (обязательно)");
        TextField russianField = new TextField();
        russianField.setPromptText("Перевод на русский (обязательно)");
        TextField categoryField = new TextField();
        categoryField.setPromptText("Категория (например, Приветствия)");
        
        // Добавляем стили для обязательных полей
        tatarField.setStyle("-fx-border-color: #ccc; -fx-border-radius: 3;");
        russianField.setStyle("-fx-border-color: #ccc; -fx-border-radius: 3;");
        
        // Валидация в реальном времени (подсветка пустых полей)
        tatarField.textProperty().addListener((obs, old, val) -> {
            boolean isValid = !val.trim().isEmpty() && !russianField.getText().trim().isEmpty();
            addButton.setDisable(!isValid);
            
            if (val.trim().isEmpty()) {
                tatarField.setStyle("-fx-border-color: #e8a898; -fx-border-radius: 3;");
            } else {
                tatarField.setStyle("-fx-border-color: #7cb87a; -fx-border-radius: 3;");
            }
        });
        
        russianField.textProperty().addListener((obs, old, val) -> {
            boolean isValid = !tatarField.getText().trim().isEmpty() && !val.trim().isEmpty();
            addButton.setDisable(!isValid);
            
            if (val.trim().isEmpty()) {
                russianField.setStyle("-fx-border-color: #e8a898; -fx-border-radius: 3;");
            } else {
                russianField.setStyle("-fx-border-color: #7cb87a; -fx-border-radius: 3;");
            }
        });
        
        // Label для подсказки
        Label requiredHint = new Label("* обязательные поля");
        requiredHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");
        
        grid.add(new Label("Татарча:*"), 0, 0);
        grid.add(tatarField, 1, 0);
        grid.add(new Label("Русский:*"), 0, 1);
        grid.add(russianField, 1, 1);
        grid.add(new Label("Категория:"), 0, 2);
        grid.add(categoryField, 1, 2);
        grid.add(requiredHint, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        
        // Валидация при нажатии кнопки
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                String tatar = tatarField.getText();
                String russian = russianField.getText();
                String category = categoryField.getText();
                
                // Двойная проверка перед сохранением
                if (!validateWordFields(tatar, russian)) {
                    return null;
                }
                
                String cleanCategory = (category == null || category.trim().isEmpty()) 
                    ? "Общее" : category.trim();
                
                return new Word(tatar.trim(), russian.trim(), cleanCategory);
            }
            return null;
        });
        
        Optional<Word> result = dialog.showAndWait();
        result.ifPresent(word -> {
            try {
                dbService.addWord(word);
                loadWords();
                statusLabel.setText("✅ Слово добавлено: " + word.getTatar());
            } catch (Exception e) {
                statusLabel.setText("❌ Ошибка добавления: " + e.getMessage());
                showValidationError("Не удалось добавить слово: " + e.getMessage());
            }
        });
    }
}