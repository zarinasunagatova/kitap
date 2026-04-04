package com.tatar.learn.controllers;

import com.tatar.learn.models.Word;
import com.tatar.learn.services.DatabaseService;
import com.tatar.learn.services.TTSService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.animation.*;
import javafx.util.Duration;
import java.net.URL;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class LearnController implements Initializable {
    
    @FXML private Label tatarWordLabel;
    @FXML private Label russianWordLabel;
    @FXML private Label progressLabel;
    @FXML private VBox cardFront;
    @FXML private VBox cardBack;
    @FXML private Button playButton;
    @FXML private Button easyButton;
    @FXML private Button goodButton;      // НОВАЯ кнопка (средняя оценка)
    @FXML private Button hardButton;
    @FXML private Button againButton;
    @FXML private Button prevButton;
    @FXML private Button nextButton;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private Label voiceStatusLabel;
    @FXML private Button applyFilterButton;
    @FXML private Label categoryLabel;
    @FXML private VBox examplesContainer;
    @FXML private ScrollPane examplesScroll;
    @FXML private VBox examplesList;
    
    private DatabaseService dbService;
    private TTSService ttsService;
    private List<Word> words;
    private List<Word> filteredWords;
    private int currentIndex = 0;
    private boolean isFlipped = false;
    
    // SM-2 константы
    private static final int[] INTERVALS = {0, 1, 3, 7, 14, 30, 60, 120, 180, 365};
    private static final double MIN_EASE = 1.3;
    private static final double MAX_EASE = 2.5;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        dbService = DatabaseService.getInstance();
        ttsService = TTSService.getInstance();
        
        voiceStatusLabel.setText(ttsService.getStatus());
        
        words = dbService.getAllWords();
        filteredWords = new ArrayList<>(words);
        
        // Проверяем, есть ли примеры у слов
        for (Word w : words) {
            if (w.getExamples() != null && !w.getExamples().isEmpty()) {
                System.out.println("Word '" + w.getTatar() + "' has " + w.getExamples().size() + " examples");
                for (String ex : w.getExamples()) {
                    System.out.println("  - " + ex);
                }
            }
        }
        
        if (filteredWords.isEmpty()) {
            showAlert("Нет слов", "Добавьте слова в словарь");
            return;
        }
        
        setupCategoryFilter();
        setupButtons();
        showCurrentWord();
        updateProgress();
    }
  
    private void setupCategoryFilter() {
        Set<String> categories = new HashSet<>();
        for (Word word : words) {
            if (word.getCategory() != null && !word.getCategory().isEmpty()) {
                categories.add(word.getCategory());
            }
        }
        
        categoryCombo.getItems().add("Все категории");
        categoryCombo.getItems().addAll(categories);
        categoryCombo.getSelectionModel().selectFirst();
        
        applyFilterButton.setOnAction(e -> applyFilter());
        categoryCombo.setOnAction(e -> applyFilter());
    }
    
    private void applyFilter() {
        String selected = categoryCombo.getValue();
        if (selected == null || selected.equals("Все категории")) {
            filteredWords = new ArrayList<>(words);
        } else {
            filteredWords.clear();
            for (Word word : words) {
                if (selected.equals(word.getCategory())) {
                    filteredWords.add(word);
                }
            }
        }
        
        if (filteredWords.isEmpty()) {
            showAlert("Нет слов", "В выбранной категории нет слов");
            filteredWords = new ArrayList<>(words);
            categoryCombo.getSelectionModel().selectFirst();
        }
        
        currentIndex = 0;
        showCurrentWord();
        updateProgress();
    }
    
    private void setupButtons() {
        // Только карточки реагируют на клик для переворота
        cardFront.setOnMouseClicked(e -> flipCard());
        cardBack.setOnMouseClicked(e -> flipCard());
        
        // Кнопки не должны вызывать переворот
        playButton.setOnAction(e -> playCurrentWord());
        againButton.setOnAction(e -> handleAgain());
        hardButton.setOnAction(e -> handleHard());
        goodButton.setOnAction(e -> handleGood());
        easyButton.setOnAction(e -> handleEasy());
        prevButton.setOnAction(e -> previousWord());
        nextButton.setOnAction(e -> nextWord());
        
        // Важно: запрещаем событиям мыши от кнопок доходить до карточки
        playButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        againButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        hardButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        goodButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        easyButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        prevButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        nextButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
    }
    
    private void flipCard() {
        if (filteredWords.isEmpty()) return;
        
        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(150), 
            isFlipped ? cardBack : cardFront);
        scaleOut.setToX(0);
        scaleOut.setOnFinished(e -> {
            isFlipped = !isFlipped;
            cardFront.setVisible(!isFlipped);
            cardFront.setManaged(!isFlipped);
            cardBack.setVisible(isFlipped);
            cardBack.setManaged(isFlipped);
            
            if (isFlipped) {
                Word word = filteredWords.get(currentIndex);
                russianWordLabel.setText(word.getRussian());
            }
            
            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(150), 
                isFlipped ? cardBack : cardFront);
            scaleIn.setFromX(0);
            scaleIn.setToX(1);
            scaleIn.play();
        });
        
        scaleOut.play();
    }
    
    private void playCurrentWord() {
        if (filteredWords.isEmpty()) return;
        
        Word word = filteredWords.get(currentIndex);
        ttsService.speakWithFeedback(word.getTatar(), playButton, "🔊");
    }
    
    // ========== SM-2 АЛГОРИТМ ==========
    
    /**
     * Обработка ответа "Снова" (не знал слово)
     */
    private void handleAgain() {
        if (filteredWords.isEmpty()) return;
        
        Word word = filteredWords.get(currentIndex);
        
        // Сброс прогресса для этого слова
        word.setTimesCorrect(0);
        word.setTimesWrong(word.getTimesWrong() + 1);
        word.setLastReviewed(LocalDate.now());
        word.setEaseFactor(Math.max(MIN_EASE, word.getEaseFactor() - 0.2)); // уменьшаем легкость
        
        // Сохраняем в БД
        dbService.saveWordAttempt(word.getId(), false);
        dbService.updateWord(word);
        
        showTemporaryMessage("🔄 Сброс (повторить сегодня)", againButton);
        
        // Сразу показываем перевод
        if (!isFlipped) flipCard();
    }
    
    /**
     * Обработка ответа "Трудно" (с трудом вспомнил)
     */
    private void handleHard() {
        if (filteredWords.isEmpty()) return;
        
        Word word = filteredWords.get(currentIndex);
        
        // Увеличиваем счетчик правильных, но немного
        word.setTimesCorrect(word.getTimesCorrect() + 1);
        word.setLastReviewed(LocalDate.now());
        word.setEaseFactor(Math.max(MIN_EASE, word.getEaseFactor() - 0.15)); // чуть уменьшаем
        
        // Сохраняем в БД
        dbService.saveWordAttempt(word.getId(), true);
        dbService.updateWord(word);
        
        int nextInterval = getNextInterval(word.getTimesCorrect());
        showTemporaryMessage("⚠️ Повтор через " + nextInterval + " дн.", hardButton);
        
        nextWord();
    }
    
    /**
     * Обработка ответа "Хорошо" (вспомнил, но не сразу)
     */
    private void handleGood() {
        if (filteredWords.isEmpty()) return;
        
        Word word = filteredWords.get(currentIndex);
        
        // Нормальный ответ
        word.setTimesCorrect(word.getTimesCorrect() + 1);
        word.setLastReviewed(LocalDate.now());
        // Ease factor не меняется
        
        // Сохраняем в БД
        dbService.saveWordAttempt(word.getId(), true);
        dbService.updateWord(word);
        
        int nextInterval = getNextInterval(word.getTimesCorrect());
        showTemporaryMessage("✅ Повтор через " + nextInterval + " дн.", goodButton);
        
        nextWord();
    }
    
    /**
     * Обработка ответа "Легко" (сразу знал)
     */
    private void handleEasy() {
        if (filteredWords.isEmpty()) return;
        
        Word word = filteredWords.get(currentIndex);
        
        // Отличный ответ
        word.setTimesCorrect(word.getTimesCorrect() + 1);
        word.setLastReviewed(LocalDate.now());
        word.setEaseFactor(Math.min(MAX_EASE, word.getEaseFactor() + 0.1)); // увеличиваем легкость
        
        // Сохраняем в БД
        dbService.saveWordAttempt(word.getId(), true);
        dbService.updateWord(word);
        
        int nextInterval = getNextInterval(word.getTimesCorrect());
        showTemporaryMessage("🚀 Повтор через " + nextInterval + " дн.", easyButton);
        
        nextWord();
    }
    
    /**
     * Получить следующий интервал повторения (в днях)
     */
    private int getNextInterval(int timesCorrect) {
        if (timesCorrect >= INTERVALS.length) {
            return INTERVALS[INTERVALS.length - 1];
        }
        return INTERVALS[timesCorrect];
    }
    
    /**
     * Показать следующее слово (с учетом интервалов)
     */
    private void nextWord() {
        if (filteredWords.isEmpty()) return;
        
        // Ищем следующее слово, которое нужно повторить сегодня
        Word currentWord = filteredWords.get(currentIndex);
        
        // Если текущее слово было только что отвечено, сохраняем его прогресс
        if (currentWord.getLastReviewed() != null && 
            currentWord.getLastReviewed().equals(LocalDate.now())) {
            
            // Оставляем его в списке, но отметим, что сегодня его больше не показываем
            // TODO: реальная фильтрация слов для сегодняшнего дня
        }
        
        currentIndex++;
        if (currentIndex >= filteredWords.size()) {
            currentIndex = 0;
            showCongratsDialog();
        }
        showCurrentWord();
    }
    
    private void previousWord() {
        if (filteredWords.isEmpty()) return;
        
        currentIndex--;
        if (currentIndex < 0) {
            currentIndex = filteredWords.size() - 1;
        }
        showCurrentWord();
    }
    
    private void showCurrentWord() {
        if (filteredWords.isEmpty()) return;
        
        Word word = filteredWords.get(currentIndex);
        
        tatarWordLabel.setText(word.getTatar());
        russianWordLabel.setText(word.getRussian());
        
        // ПОКАЗЫВАЕМ ПРИМЕРЫ
        showExamples(word);
        
        isFlipped = false;
        cardFront.setVisible(true);
        cardFront.setManaged(true);
        cardBack.setVisible(false);
        cardBack.setManaged(false);
        
        // Удаляем старые классы прогресса
        tatarWordLabel.getStyleClass().removeAll("progress-zero", "progress-low", "progress-medium", "progress-high");
        
        // Новая система: цвет в зависимости от статуса SM-2
        if (word.getTimesCorrect() >= 5) {
            tatarWordLabel.getStyleClass().add("progress-high");
        } else if (word.getTimesCorrect() >= 2) {
            tatarWordLabel.getStyleClass().add("progress-medium");
        } else if (word.getTimesCorrect() > 0) {
            tatarWordLabel.getStyleClass().add("progress-low");
        } else {
            tatarWordLabel.getStyleClass().add("progress-zero");
        }
        
        updateProgress();
    }
    
    private void showExamples(Word word) {
        examplesList.getChildren().clear();
        
        if (word.getExamples() != null && !word.getExamples().isEmpty()) {
            examplesScroll.setVisible(true);
            
            for (String example : word.getExamples()) {
                HBox exampleBox = new HBox();
                exampleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                exampleBox.getStyleClass().add("example-item");
                exampleBox.setSpacing(8);
                
                Label iconLabel = new Label("📖");
                iconLabel.getStyleClass().add("example-icon");
                
                Label exampleLabel = new Label(example);
                exampleLabel.getStyleClass().add("example-text");
                exampleLabel.setWrapText(true);
                
                exampleBox.getChildren().addAll(iconLabel, exampleLabel);
                examplesList.getChildren().add(exampleBox);
            }
        } else {
            // Если нет примеров, показываем сообщение
            examplesScroll.setVisible(true);
            Label noExamplesLabel = new Label("Нет примеров для этого слова");
            noExamplesLabel.getStyleClass().add("no-examples-label");
            examplesList.getChildren().add(noExamplesLabel);
        }
    }

    
    private void updateProgress() {
        if (filteredWords.isEmpty()) {
            progressLabel.setText("📊 Нет слов");
            return;
        }
        
        Word word = filteredWords.get(currentIndex);
        int total = filteredWords.size();
        int learned = 0;
        int inProgress = 0;
        int newWords = 0;
        
        for (Word w : filteredWords) {
            if (w.getTimesCorrect() >= 5) learned++;
            else if (w.getTimesCorrect() > 0) inProgress++;
            else newWords++;
        }
        
        String nextReview = "сегодня";
        if (word.getLastReviewed() != null && word.getTimesCorrect() > 0) {
            int interval = getNextInterval(word.getTimesCorrect());
            LocalDate nextDate = word.getLastReviewed().plusDays(interval);
            if (nextDate.isAfter(LocalDate.now())) {
                long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), nextDate);
                nextReview = "через " + daysLeft + " дн.";
            }
        }
        
        progressLabel.setText(String.format(
            "📊 Выучено: %d | В процессе: %d | Новых: %d | Текущее: %d раз | След: %s",
            learned, inProgress, newWords, word.getTimesCorrect(), nextReview
        ));
    }
    
    private void showTemporaryMessage(String message, Button button) {
        String originalText = button.getText();
        button.setText(message);
        
        PauseTransition pause = new PauseTransition(Duration.seconds(1));
        pause.setOnFinished(e -> button.setText(originalText));
        pause.play();
    }
    
    private void showCongratsDialog() {
        int learned = 0;
        int inProgress = 0;
        int newWords = 0;
        
        for (Word w : filteredWords) {
            if (w.getTimesCorrect() >= 5) learned++;
            else if (w.getTimesCorrect() > 0) inProgress++;
            else newWords++;
        }
        
        String category = filteredWords.get(0).getCategory();
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("🎉 Отличная работа!");
        alert.setHeaderText("Вы просмотрели все доступные слова!");
        alert.setContentText(String.format(
            "Категория: %s\n\n" +
            "✅ Выучено: %d\n" +
            "📖 В процессе: %d\n" +
            "🆕 Новых: %d\n\n" +
            "Завтра будет больше слов для повторения!",
            category != null ? category : "Все категории",
            learned, inProgress, newWords
        ));
        alert.showAndWait();
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}