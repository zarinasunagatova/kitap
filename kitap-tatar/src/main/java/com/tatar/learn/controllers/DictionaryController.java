package com.tatar.learn.controllers;

import com.tatar.learn.models.Word;
import com.tatar.learn.services.DatabaseService;
import com.tatar.learn.services.TTSService;
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
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.stage.FileChooser;
import java.io.File;
import com.tatar.learn.services.ImportService;

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
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        dbService = DatabaseService.getInstance();
        wordList = FXCollections.observableArrayList();
        
        setupTableColumns();
        setupActions();
        loadWords();
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
        statusLabel.setText("Загрузка слов...");
        wordList.clear();
        wordList.addAll(dbService.getAllWords());
        
        filteredData = new FilteredList<>(wordList, p -> true);
        SortedList<Word> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(wordsTable.comparatorProperty());
        
        wordsTable.setItems(sortedData);
        updateCountLabel();
        statusLabel.setText("Загружено " + wordList.size() + " слов");
    }
    
    private void filterWords() {
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
    
    private void updateCountLabel() {
        int filteredCount = filteredData != null ? filteredData.size() : 0;
        int totalCount = wordList.size();
        countLabel.setText(String.format("Показано: %d из %d слов", filteredCount, totalCount));
    }
    
    private void incrementTimesCorrect(Word word) {
        word.incrementCorrect();
        dbService.updateWord(word);
        wordsTable.refresh();
        statusLabel.setText("✅ Прогресс увеличен: " + word.getTatar() + 
                           " (" + word.getTimesCorrect() + " раз)");
    }
    
    // ИСПРАВЛЕНО: убрал updateWordProgress
    private void resetProgress(Word word) {
        word.resetProgress();
        dbService.updateWord(word);
        wordsTable.refresh();
        statusLabel.setText("🔄 Прогресс сброшен: " + word.getTatar());
    }
    
    private void editWord(Word word) {
        Dialog<Word> dialog = new Dialog<>();
        dialog.setTitle("Редактирование слова");
        dialog.setHeaderText("Редактирование: " + word.getTatar());
        
        ButtonType saveButtonType = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField tatarField = new TextField(word.getTatar());
        TextField russianField = new TextField(word.getRussian());
        TextField categoryField = new TextField(word.getCategory());
        
        grid.add(new Label("Татарча:"), 0, 0);
        grid.add(tatarField, 1, 0);
        grid.add(new Label("Русский:"), 0, 1);
        grid.add(russianField, 1, 1);
        grid.add(new Label("Категория:"), 0, 2);
        grid.add(categoryField, 1, 2);
        
        dialog.getDialogPane().setContent(grid);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                word.setTatar(tatarField.getText());
                word.setRussian(russianField.getText());
                word.setCategory(categoryField.getText());
                return word;
            }
            return null;
        });
        
        Optional<Word> result = dialog.showAndWait();
        result.ifPresent(updatedWord -> {
            dbService.updateWord(updatedWord);
            loadWords();
            statusLabel.setText("✅ Слово обновлено: " + updatedWord.getTatar());
        });
    }
    
    private void deleteWord(Word word) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удаление слова");
        confirm.setHeaderText("Удалить слово: " + word.getTatar() + "?");
        confirm.setContentText("Это действие нельзя отменить!");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            dbService.deleteWord(word.getId());
            loadWords();
            statusLabel.setText("✅ Слово удалено: " + word.getTatar());
        }
    }
    
    public void refreshProgress() {
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
        Dialog<Word> dialog = new Dialog<>();
        dialog.setTitle("Добавление слова");
        dialog.setHeaderText("Новое слово");
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        
        // 👇 ДОБАВЬТЕ ЭТИ СТРОКИ - ПОДКЛЮЧАЕМ CSS К ДИАЛОГУ
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
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField tatarField = new TextField();
        tatarField.setPromptText("Татарское слово");
        TextField russianField = new TextField();
        russianField.setPromptText("Перевод на русский");
        TextField categoryField = new TextField();
        categoryField.setPromptText("Категория (например, Приветствия)");
        
        grid.add(new Label("Татарча:"), 0, 0);
        grid.add(tatarField, 1, 0);
        grid.add(new Label("Русский:"), 0, 1);
        grid.add(russianField, 1, 1);
        grid.add(new Label("Категория:"), 0, 2);
        grid.add(categoryField, 1, 2);
        
        dialog.getDialogPane().setContent(grid);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                return new Word(tatarField.getText(), russianField.getText(), categoryField.getText());
            }
            return null;
        });
        
        Optional<Word> result = dialog.showAndWait();
        result.ifPresent(word -> {
            dbService.addWord(word);
            loadWords();
            statusLabel.setText("✅ Слово добавлено: " + word.getTatar());
        });
    }
   
}