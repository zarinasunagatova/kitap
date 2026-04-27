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
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.geometry.Insets;

import java.io.InputStream;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.ArrayList;

public class DictionaryController implements Initializable {
    
    @FXML private TableView<Word> wordsTable;
    @FXML private TableColumn<Word, Integer> idColumn;
    @FXML private TableColumn<Word, String> tatarColumn;
    @FXML private TableColumn<Word, String> russianColumn;
    @FXML private TableColumn<Word, String> examplesColumn;
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
        if (!initDatabase()) {
            showDatabaseErrorAndDisable();
            return;
        }
        
        wordList = FXCollections.observableArrayList();
        
        setupTableColumns();
        setupActions();
        loadWords();
    }
    
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
    
    private void showDatabaseErrorAndDisable() {
        addButton.setDisable(true);
        searchButton.setDisable(true);
        clearButton.setDisable(true);
        searchField.setDisable(true);
        
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
        
        // Колонка с примерами
        examplesColumn.setCellValueFactory(cellData -> {
            Word word = cellData.getValue();
            if (word.getExamples() != null && !word.getExamples().isEmpty()) {
                return new javafx.beans.property.SimpleStringProperty(
                    word.getExamples().size() + ""
                );
            }
            return new javafx.beans.property.SimpleStringProperty("0");
        });
        
        examplesColumn.setCellFactory(col -> new TableCell<Word, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setStyle("");
                } else {
                    setText(item);
                    Word word = getTableView().getItems().get(getIndex());
                    if (word.getExamples() != null && !word.getExamples().isEmpty()) {
                        StringBuilder tooltipText = new StringBuilder("Примеры:\n");
                        for (int i = 0; i < word.getExamples().size(); i++) {
                            tooltipText.append(i + 1).append(". ").append(word.getExamples().get(i)).append("\n");
                        }
                        Tooltip tooltip = new Tooltip(tooltipText.toString().trim());
                        tooltip.setMaxWidth(400);
                        tooltip.setWrapText(true);
                        setTooltip(tooltip);
                        setStyle("-fx-text-fill: #2c7a4c;");
                    } else {
                        setTooltip(null);
                        setStyle("-fx-text-fill: #999;");
                    }
                }
            }
        });
        
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
                    setText(timesCorrect + "");
                    
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
    
    private void loadWords() {
        if (!databaseAvailable) {
            showDatabaseErrorAndDisable();
            return;
        }
        
        try {
            statusLabel.setText("Загрузка слов...");
            wordList.clear();
            
            TopicsService topicsService = TopicsService.getInstance();
            List<Word> allWords = topicsService.getAllAvailableWords();
            
            wordList.addAll(allWords);
            
            System.out.println("Загружено слов из БД и пройденных тем: " + wordList.size());
            
            filteredData = new FilteredList<>(wordList, p -> true);
            SortedList<Word> sortedData = new SortedList<>(filteredData);
            sortedData.comparatorProperty().bind(wordsTable.comparatorProperty());
            
            wordsTable.setItems(sortedData);
            updateCountLabel();
            statusLabel.setText("✅ Загружено " + wordList.size() + " слов");
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
                   word.getRussian().toLowerCase().contains(searchText) ||
                   (word.getExamples() != null && word.getExamples().stream()
                       .anyMatch(ex -> ex.toLowerCase().contains(searchText)));
        });
        
        updateCountLabel();
    }
    
    private void clearFilters() {
        searchField.clear();
        filterWords();
    }

    private boolean validateWordFields(String tatar, String russian) {
        if (tatar == null || tatar.trim().isEmpty()) {
            showValidationError("Татарское слово не может быть пустым!");
            return false;
        }
        
        if (russian == null || russian.trim().isEmpty()) {
            showValidationError("Русский перевод не может быть пустым!");
            return false;
        }
        
        if (tatar.trim().length() < 2) {
            showValidationError("Татарское слово должно содержать хотя бы 2 буквы!");
            return false;
        }
        
        if (russian.trim().length() < 1) {
            showValidationError("Русский перевод должен содержать хотя бы 1 букву!");
            return false;
        }
        
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
        setDialogIcon(dialog);

        ButtonType saveButtonType = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);
        
        // Основной контейнер
        VBox mainContainer = new VBox(10);
        mainContainer.setPadding(new Insets(15));
        
        // Основные поля
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 0, 10, 0));
        
        TextField tatarField = new TextField(word.getTatar());
        TextField russianField = new TextField(word.getRussian());
        TextField categoryField = new TextField(word.getCategory());
        
        tatarField.textProperty().addListener((obs, old, val) -> {
            boolean isValid = !val.trim().isEmpty() && !russianField.getText().trim().isEmpty();
            saveButton.setDisable(!isValid);
            tatarField.setStyle(val.trim().isEmpty() ? 
                "-fx-border-color: #e8a898; -fx-border-radius: 3;" : 
                "-fx-border-color: #7cb87a; -fx-border-radius: 3;");
        });
        
        russianField.textProperty().addListener((obs, old, val) -> {
            boolean isValid = !tatarField.getText().trim().isEmpty() && !val.trim().isEmpty();
            saveButton.setDisable(!isValid);
            russianField.setStyle(val.trim().isEmpty() ? 
                "-fx-border-color: #e8a898; -fx-border-radius: 3;" : 
                "-fx-border-color: #7cb87a; -fx-border-radius: 3;");
        });
        
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
        
        // Секция примеров
        Label examplesLabel = new Label("📖 Примеры употребления:");
        examplesLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 10 0 5 0;");
        
        Label examplesHint = new Label("Добавьте примеры использования слова в предложениях.");
        examplesHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #888; -fx-wrap-text: true;");
        
        ListView<String> examplesListView = new ListView<>();
        ObservableList<String> examplesObservable = FXCollections.observableArrayList(
            word.getExamples() != null ? word.getExamples() : new ArrayList<>()
        );
        examplesListView.setItems(examplesObservable);
        examplesListView.setPrefHeight(120);
        examplesListView.setPlaceholder(new Label("Нет примеров. Добавьте новые ниже."));
        
        // Кнопки управления примерами
        HBox exampleButtonsBox = new HBox(5);
        
        Button editExampleButton = new Button("✎ Изменить");
        editExampleButton.setOnAction(e -> {
            String selected = examplesListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                TextInputDialog inputDialog = new TextInputDialog(selected);
                inputDialog.setTitle("Редактирование примера");
                inputDialog.setHeaderText("Измените пример употребления");
                inputDialog.setContentText("Пример:");
                inputDialog.getDialogPane().setPrefWidth(500);
                
                Optional<String> result = inputDialog.showAndWait();
                result.ifPresent(newExample -> {
                    if (!newExample.trim().isEmpty() && newExample.trim().length() >= 5) {
                        int index = examplesObservable.indexOf(selected);
                        if (index >= 0) {
                            examplesObservable.set(index, newExample.trim());
                        }
                    }
                });
            }
        });
        
        Button removeExampleButton = new Button("🗑 Удалить");
        removeExampleButton.setOnAction(e -> {
            String selected = examplesListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                examplesObservable.remove(selected);
            }
        });
        
        Button moveUpButton = new Button("▲");
        moveUpButton.setTooltip(new Tooltip("Переместить вверх"));
        moveUpButton.setOnAction(e -> {
            int selectedIndex = examplesListView.getSelectionModel().getSelectedIndex();
            if (selectedIndex > 0) {
                String item = examplesObservable.remove(selectedIndex);
                examplesObservable.add(selectedIndex - 1, item);
                examplesListView.getSelectionModel().select(selectedIndex - 1);
            }
        });
        
        Button moveDownButton = new Button("▼");
        moveDownButton.setTooltip(new Tooltip("Переместить вниз"));
        moveDownButton.setOnAction(e -> {
            int selectedIndex = examplesListView.getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0 && selectedIndex < examplesObservable.size() - 1) {
                String item = examplesObservable.remove(selectedIndex);
                examplesObservable.add(selectedIndex + 1, item);
                examplesListView.getSelectionModel().select(selectedIndex + 1);
            }
        });
        
        exampleButtonsBox.getChildren().addAll(editExampleButton, removeExampleButton, 
                                               moveUpButton, moveDownButton);
        
        // Добавление нового примера
        HBox addExampleBox = new HBox(5);
        TextField newExampleField = new TextField();
        newExampleField.setPromptText("Введите пример употребления...");
        newExampleField.setPrefWidth(350);
        
        Button addExampleButton = new Button("➕");
        addExampleButton.setOnAction(e -> {
            String newExample = newExampleField.getText().trim();
            if (!newExample.isEmpty() && newExample.length() >= 5) {
                examplesObservable.add(newExample);
                newExampleField.clear();
                newExampleField.requestFocus();
            }
        });
        
        newExampleField.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                addExampleButton.fire();
            }
        });
        
        addExampleBox.getChildren().addAll(newExampleField, addExampleButton);
        
        mainContainer.getChildren().addAll(
            grid,
            new Separator(),
            examplesLabel,
            examplesHint,
            examplesListView,
            exampleButtonsBox,
            addExampleBox
        );
        
        ScrollPane scrollPane = new ScrollPane(mainContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(500);
        
        dialog.getDialogPane().setContent(scrollPane);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String tatar = tatarField.getText();
                String russian = russianField.getText();
                String category = categoryField.getText();
                
                if (!validateWordFields(tatar, russian)) {
                    return null;
                }
                
                word.setTatar(tatar.trim());
                word.setRussian(russian.trim());
                word.setCategory((category == null || category.trim().isEmpty()) ? "Общее" : category.trim());
                word.setExamples(new ArrayList<>(examplesObservable));
                
                return word;
            }
            return null;
        });
        
        Optional<Word> result = dialog.showAndWait();
        result.ifPresent(updatedWord -> {
            try {
                dbService.updateWord(updatedWord);
                loadWords();
                int examplesCount = updatedWord.getExamples() != null ? updatedWord.getExamples().size() : 0;
                statusLabel.setText("✅ Слово обновлено: " + updatedWord.getTatar() + 
                                  (examplesCount > 0 ? " (" + examplesCount + " )" : ""));
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
        setDialogIcon(confirm);
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
        setDialogIcon(dialog);
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
        
        Button addBtn = (Button) dialog.getDialogPane().lookupButton(addButtonType);
        addBtn.setDisable(true);
        
        // Основной контейнер
        VBox mainContainer = new VBox(10);
        mainContainer.setPadding(new Insets(15));
        
        // Основные поля
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 0, 10, 0));
        
        TextField tatarField = new TextField();
        tatarField.setPromptText("Татарское слово (обязательно)");
        TextField russianField = new TextField();
        russianField.setPromptText("Перевод на русский (обязательно)");
        TextField categoryField = new TextField();
        categoryField.setPromptText("Категория (например, Приветствия)");
        
        tatarField.textProperty().addListener((obs, old, val) -> {
            boolean isValid = !val.trim().isEmpty() && !russianField.getText().trim().isEmpty();
            addBtn.setDisable(!isValid);
            tatarField.setStyle(val.trim().isEmpty() ? 
                "-fx-border-color: #e8a898; -fx-border-radius: 3;" : 
                "-fx-border-color: #7cb87a; -fx-border-radius: 3;");
        });
        
        russianField.textProperty().addListener((obs, old, val) -> {
            boolean isValid = !tatarField.getText().trim().isEmpty() && !val.trim().isEmpty();
            addBtn.setDisable(!isValid);
            russianField.setStyle(val.trim().isEmpty() ? 
                "-fx-border-color: #e8a898; -fx-border-radius: 3;" : 
                "-fx-border-color: #7cb87a; -fx-border-radius: 3;");
        });
        
        grid.add(new Label("Татарча:*"), 0, 0);
        grid.add(tatarField, 1, 0);
        grid.add(new Label("Русский:*"), 0, 1);
        grid.add(russianField, 1, 1);
        grid.add(new Label("Категория:"), 0, 2);
        grid.add(categoryField, 1, 2);
        
        Label requiredHint = new Label("* обязательные поля");
        requiredHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");
        grid.add(requiredHint, 1, 3);
        
        // Секция примеров
        Label examplesLabel = new Label("📖 Примеры употребления (опционально):");
        examplesLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 10 0 5 0;");
        
        ListView<String> examplesListView = new ListView<>();
        ObservableList<String> examplesObservable = FXCollections.observableArrayList();
        examplesListView.setItems(examplesObservable);
        examplesListView.setPrefHeight(120);
        examplesListView.setPlaceholder(new Label("Пока нет примеров"));
        
        Button removeExampleButton = new Button("🗑 Удалить выбранный");
        removeExampleButton.setOnAction(e -> {
            String selected = examplesListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                examplesObservable.remove(selected);
            }
        });
        
        HBox addExampleBox = new HBox(5);
        TextField newExampleField = new TextField();
        newExampleField.setPromptText("Пример употребления в предложении...");
        newExampleField.setPrefWidth(350);
        
        Button addExampleButton = new Button("➕");
        addExampleButton.setMinWidth(35);  
        addExampleButton.setPrefWidth(35); 
        addExampleButton.setOnAction(e -> {
            String example = newExampleField.getText().trim();
            if (!example.isEmpty() && example.length() >= 5) {
                examplesObservable.add(example);
                newExampleField.clear();
                newExampleField.requestFocus();
            }
        });
        
        newExampleField.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                addExampleButton.fire();
            }
        });
        
        addExampleBox.getChildren().addAll(newExampleField, addExampleButton);
        
        mainContainer.getChildren().addAll(
            grid,
            new Separator(),
            examplesLabel,
            examplesListView,
            removeExampleButton,
            addExampleBox
        );
        
        ScrollPane scrollPane = new ScrollPane(mainContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(450);
        
        dialog.getDialogPane().setContent(scrollPane);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                String tatar = tatarField.getText();
                String russian = russianField.getText();
                String category = categoryField.getText();
                
                if (!validateWordFields(tatar, russian)) {
                    return null;
                }
                
                String cleanCategory = (category == null || category.trim().isEmpty()) 
                    ? "Общее" : category.trim();
                
                Word newWord = new Word(tatar.trim(), russian.trim(), cleanCategory);
                newWord.setExamples(new ArrayList<>(examplesObservable));
                
                return newWord;
            }
            return null;
        });
        
        Optional<Word> result = dialog.showAndWait();
        result.ifPresent(word -> {
            try {
                dbService.addWord(word);
                TopicsService.getInstance().refreshCache();
                loadWords();
                int examplesCount = word.getExamples() != null ? word.getExamples().size() : 0;
                statusLabel.setText("✅ Слово добавлено: " + word.getTatar() + 
                                  (examplesCount > 0 ? " (" + examplesCount + ")" : ""));
            } catch (Exception e) {
                statusLabel.setText("❌ Ошибка добавления: " + e.getMessage());
                showValidationError("Не удалось добавить слово: " + e.getMessage());
            }
        });
    }
    
    /**
     * Загружает иконку для диалоговых окон
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
     * Устанавливает иконку для диалогового окна
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

    /**
     * Устанавливает иконку для диалога редактирования/добавления
     */
    private void setDialogIcon(Dialog<?> dialog) {
        try {
            Image icon = loadDialogIcon();
            if (icon != null) {
                Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
                stage.getIcons().add(icon);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Не удалось установить иконку для диалога: " + e.getMessage());
        }
    }
    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Ошибка валидации");
        alert.setHeaderText("Некорректные данные");
        alert.setContentText(message);
        setAlertIcon(alert); 
        alert.showAndWait();
    }
}