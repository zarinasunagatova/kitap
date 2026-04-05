package com.tatar.learn.controllers;

import com.tatar.learn.models.Word;
import com.tatar.learn.services.DatabaseService;
import com.tatar.learn.services.TTSService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.animation.*;
import javafx.util.Duration;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import javafx.application.Platform;

public class LearnController implements Initializable {
    
    @FXML private Label tatarWordLabel;
    @FXML private Label russianWordLabel;
    @FXML private Label progressLabel;
    @FXML private Label queueInfoLabel;  // НОВЫЙ: информация об очереди обучения
    @FXML private VBox cardFront;
    @FXML private VBox cardBack;
    @FXML private Button playButton;
    @FXML private Button easyButton;
    @FXML private Button goodButton;
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
    private List<Word> allWords;
    private List<Word> dueWords;      // Слова для повторения сегодня
    private List<Word> newWords;      // Новые слова
    private Queue<Word> learningQueue; // Очередь обучения
    private Word currentWord;
    private int currentIndex = 0;
    private boolean isFlipped = false;
    private boolean databaseAvailable = false;
    private String currentFilterCategory = "Все категории";  
    
    // SM-2 константы
    private static final int[] INTERVALS = {0, 1, 3, 7, 14, 30, 60, 120, 180, 365};
    private static final double MIN_EASE = 1.3;
    private static final double MAX_EASE = 2.5;
    
    // Настройки обучения
    private static final int MAX_NEW_WORDS_PER_DAY = 10;  // Максимум новых слов в день
    private static final int MAX_DUE_WORDS_PER_SESSION = 50; // Максимум слов для повторения за сессию
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Инициализируем БД с обработкой ошибок
        if (!initDatabase()) {
            showDatabaseErrorAndDisable();
            return;
        }
        
        ttsService = TTSService.getInstance();
        voiceStatusLabel.setText(ttsService.getStatus());
        
        loadWordsAndBuildQueue();
        
        if (learningQueue == null || learningQueue.isEmpty()) {
            showAlert("Нет слов", "Добавьте слова в словарь");
            return;
        }
        
        setupCategoryFilter();
        setupButtons();
        showCurrentWord();
        updateProgress();
        
        currentFilterCategory = "Все категории";
    }
    
    private boolean initDatabase() {
		try {
            dbService = DatabaseService.getInstance();
            
            if (dbService == null) {
                showErrorOnStatus("❌ База данных недоступна");
                return false;
            }
            
            if (!dbService.isHealthy()) {
                showErrorOnStatus("❌ База данных нездорова");
                return false;
            }
            
            databaseAvailable = true;
            return true;
            
        } catch (SQLException e) {
            System.err.println("Database init failed: " + e.getMessage());
            showErrorOnStatus("❌ Ошибка БД: " + e.getMessage());
            databaseAvailable = false;
            return false;
        }
    }

    private void showDatabaseErrorAndDisable() {
        // Отключаем все кнопки
        easyButton.setDisable(true);
        goodButton.setDisable(true);
        hardButton.setDisable(true);
        againButton.setDisable(true);
        playButton.setDisable(true);
        prevButton.setDisable(true);
        nextButton.setDisable(true);
        applyFilterButton.setDisable(true);
        categoryCombo.setDisable(true);
        
        // Показываем сообщение
        tatarWordLabel.setText("❌ База данных недоступна");
        russianWordLabel.setText("Пожалуйста, перезапустите приложение");
        progressLabel.setText("Ошибка подключения к БД");
    }

    private void showErrorOnStatus(String message) {
        if (queueInfoLabel != null) {
            queueInfoLabel.setText(message);
        }
        System.err.println(message);
    }
    
    /**
     * Загружает слова и строит очередь обучения на основе интервалов
     */
    private void loadWordsAndBuildQueue() {
        allWords = dbService.getAllWords();
        
        // Разделяем слова на категории
        dueWords = new ArrayList<>();
        newWords = new ArrayList<>();
        
        LocalDate today = LocalDate.now();
        
        for (Word word : allWords) {
            if (isWordDueForReview(word, today)) {
                dueWords.add(word);
            } else if (word.getTimesCorrect() == 0) {
                newWords.add(word);
            }
        }
        
        // Сортируем due слова по приоритету (чем дольше не повторяли, тем выше приоритет)
        dueWords.sort((w1, w2) -> {
            int priority1 = getReviewPriority(w1, today);
            int priority2 = getReviewPriority(w2, today);
            return Integer.compare(priority2, priority1); // Выше приоритет - первым
        });
        
        // Сортируем новые слова (можно по алфавиту или случайно)
        Collections.shuffle(newWords);
        
        // Строим очередь обучения
        learningQueue = new LinkedList<>();
        
        // Сначала добавляем due слова
        int dueToTake = Math.min(dueWords.size(), MAX_DUE_WORDS_PER_SESSION);
        for (int i = 0; i < dueToTake; i++) {
            learningQueue.add(dueWords.get(i));
        }
        
        // Затем добавляем новые слова (ограниченное количество)
        int newToTake = Math.min(newWords.size(), MAX_NEW_WORDS_PER_DAY);
        for (int i = 0; i < newToTake; i++) {
            learningQueue.add(newWords.get(i));
        }
        
        System.out.println("=== Learning Queue Built ===");
        System.out.println("Due words: " + dueWords.size() + " (taking " + dueToTake + ")");
        System.out.println("New words: " + newWords.size() + " (taking " + newToTake + ")");
        System.out.println("Total in queue: " + learningQueue.size());
        
        // Обновляем UI информацию
        updateQueueInfo();
    }
    
    /**
     * Проверяет, нужно ли повторить слово сегодня
     */
    private boolean isWordDueForReview(Word word, LocalDate today) {
        if (word.getTimesCorrect() == 0) {
            return false; // Новые слова обрабатываются отдельно
        }
        
        if (word.getLastReviewed() == null) {
            return true; // Никогда не повторяли - пора
        }
        
        int interval = getNextInterval(word.getTimesCorrect());
        LocalDate nextReviewDate = word.getLastReviewed().plusDays(interval);
        
        return !nextReviewDate.isAfter(today);
    }
    
    /**
     * Вычисляет приоритет повторения (чем больше просрочка, тем выше приоритет)
     */
    private int getReviewPriority(Word word, LocalDate today) {
        if (word.getLastReviewed() == null) {
            return Integer.MAX_VALUE;
        }
        
        int interval = getNextInterval(word.getTimesCorrect());
        LocalDate nextReviewDate = word.getLastReviewed().plusDays(interval);
        
        if (nextReviewDate.isAfter(today)) {
            return 0; // Еще не пора
        }
        
        // Количество дней просрочки
        long overdue = ChronoUnit.DAYS.between(nextReviewDate, today);
        return (int) overdue + 1;
    }
    
    /**
     * Обновляет информацию об очереди обучения
     */
    private void updateQueueInfo() {
        if (learningQueue == null) return;
        
        int dueCount = 0;
        int newCount = 0;
        
        for (Word w : learningQueue) {
            if (w.getTimesCorrect() == 0) {
                newCount++;
            } else {
                dueCount++;
            }
        }
        
        String info = String.format("📚 Очередь: %d слов (повтор: %d, новых: %d)", 
            learningQueue.size(), dueCount, newCount);
        
        if (queueInfoLabel != null) {
            queueInfoLabel.setText(info);
        }
    }
    
    private void setupCategoryFilter() {
        Set<String> categories = new HashSet<>();
        for (Word word : allWords) {
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
        currentFilterCategory = selected;  
        
        List<Word> filteredAllWords;
        
        if (selected == null || selected.equals("Все категории")) {
            filteredAllWords = new ArrayList<>(allWords);
            currentFilterCategory = "Все категории";
        } else {
            filteredAllWords = allWords.stream()
                .filter(w -> selected.equals(w.getCategory()))
                .collect(Collectors.toList());
            currentFilterCategory = selected;
        }
        
        if (filteredAllWords.isEmpty()) {
            showAlert("Нет слов", "В выбранной категории нет слов");
            return;
        }
        
        // Перестраиваем очередь с учетом фильтра
        LocalDate today = LocalDate.now();
        
        List<Word> filteredDue = filteredAllWords.stream()
            .filter(w -> isWordDueForReview(w, today))
            .collect(Collectors.toList());
        
        List<Word> filteredNew = filteredAllWords.stream()
            .filter(w -> w.getTimesCorrect() == 0)
            .collect(Collectors.toList());
        
        learningQueue.clear();
        
        int dueToTake = Math.min(filteredDue.size(), MAX_DUE_WORDS_PER_SESSION);
        for (int i = 0; i < dueToTake; i++) {
            learningQueue.add(filteredDue.get(i));
        }
        
        int newToTake = Math.min(filteredNew.size(), MAX_NEW_WORDS_PER_DAY);
        for (int i = 0; i < newToTake; i++) {
            learningQueue.add(filteredNew.get(i));
        }
        
        if (learningQueue.isEmpty()) {
            showAlert("Нет слов для изучения", 
                "В выбранной категории нет слов, которые нужно повторять сегодня");
        }
        
        currentIndex = 0;
        updateQueueInfo();
        showCurrentWord();
        updateProgress();
    }
    
    private void setupButtons() {
        cardFront.setOnMouseClicked(e -> flipCard());
        cardBack.setOnMouseClicked(e -> flipCard());
        
        playButton.setOnAction(e -> playCurrentWord());
        againButton.setOnAction(e -> handleAgain());
        hardButton.setOnAction(e -> handleHard());
        goodButton.setOnAction(e -> handleGood());
        easyButton.setOnAction(e -> handleEasy());
        prevButton.setOnAction(e -> previousWord());
        nextButton.setOnAction(e -> nextWord());
        
        // Запрещаем событиям мыши от кнопок доходить до карточки
        playButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        againButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        hardButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        goodButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        easyButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        prevButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        nextButton.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
    }
    
    private void flipCard() {
        if (currentWord == null) return;
        
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
                russianWordLabel.setText(currentWord.getRussian());
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
        if (currentWord == null) return;
        ttsService.speakWithFeedback(currentWord.getTatar(), playButton, "🔊");
    }
    
    // ========== SM-2 АЛГОРИТМ С ОБНОВЛЕНИЕМ СТАТИСТИКИ ==========
    
    private void handleAgain() {
        if (currentWord == null) return;
        
        // Сброс прогресса
        currentWord.setTimesCorrect(0);
        currentWord.setTimesWrong(currentWord.getTimesWrong() + 1);
        currentWord.setLastReviewed(LocalDate.now());
        currentWord.setEaseFactor(Math.max(MIN_EASE, currentWord.getEaseFactor() - 0.2));
        
        // Сохраняем в БД
        dbService.saveWordAttempt(currentWord.getId(), false);
        dbService.updateWord(currentWord);
        
        showTemporaryMessage("🔄 Сброс (повторить сегодня)", againButton);
        
        if (!isFlipped) flipCard();
        
        // Остаемся на этом же слове для повторения
    }
    
    private void handleHard() {
        if (currentWord == null) return;
        
        currentWord.setTimesCorrect(currentWord.getTimesCorrect() + 1);
        currentWord.setLastReviewed(LocalDate.now());
        currentWord.setEaseFactor(Math.max(MIN_EASE, currentWord.getEaseFactor() - 0.15));
        
        dbService.saveWordAttempt(currentWord.getId(), true);
        dbService.updateWord(currentWord);
        
        int nextInterval = getNextInterval(currentWord.getTimesCorrect());
        showTemporaryMessage("⚠️ Повтор через " + nextInterval + " дн.", hardButton);
        
        moveToNextWord();
    }
    
    private void handleGood() {
        if (currentWord == null) return;
        
        currentWord.setTimesCorrect(currentWord.getTimesCorrect() + 1);
        currentWord.setLastReviewed(LocalDate.now());
        
        dbService.saveWordAttempt(currentWord.getId(), true);
        dbService.updateWord(currentWord);
        
        int nextInterval = getNextInterval(currentWord.getTimesCorrect());
        showTemporaryMessage("✅ Повтор через " + nextInterval + " дн.", goodButton);
        
        moveToNextWord();
    }
    
    private void handleEasy() {
        if (currentWord == null) return;
        
        currentWord.setTimesCorrect(currentWord.getTimesCorrect() + 1);
        currentWord.setLastReviewed(LocalDate.now());
        currentWord.setEaseFactor(Math.min(MAX_EASE, currentWord.getEaseFactor() + 0.1));
        
        dbService.saveWordAttempt(currentWord.getId(), true);
        dbService.updateWord(currentWord);
        
        int nextInterval = getNextInterval(currentWord.getTimesCorrect());
        showTemporaryMessage("🚀 Повтор через " + nextInterval + " дн.", easyButton);
        
        moveToNextWord();
    }
    
    private void moveToNextWord() {
        // Удаляем текущее слово из очереди
        learningQueue.poll();
        
        if (learningQueue.isEmpty()) {
            // Очередь пуста - проверяем, есть ли еще слова для загрузки
            loadWordsAndBuildQueue();
            
            if (learningQueue.isEmpty()) {
                showCongratsDialog();
                return;
            }
        }
        
        currentIndex = 0;
        updateQueueInfo();
        showCurrentWord();
    }
    
    private void nextWord() {
        if (learningQueue == null || learningQueue.isEmpty()) return;
        
        // Пропускаем слово (не обновляя прогресс)
        Word skipped = learningQueue.poll();
        if (skipped != null) {
            // Возвращаем в конец очереди для следующей сессии
            // Но для простоты просто удаляем
            System.out.println("Skipped word: " + skipped.getTatar());
        }
        
        if (learningQueue.isEmpty()) {
            loadWordsAndBuildQueue();
            if (learningQueue.isEmpty()) {
                showCongratsDialog();
                return;
            }
        }
        
        showCurrentWord();
        updateQueueInfo();
    }
    
    private void previousWord() {
        // В интервальном повторении нет смысла возвращаться назад
        // Но для удобства показываем предыдущее слово из очереди
        showTemporaryMessage("⏪ Только вперед!", prevButton);
    }
    
    private void showCurrentWord() {
        if (learningQueue == null || learningQueue.isEmpty()) {
            return;
        }
        
        currentWord = learningQueue.peek();
        if (currentWord == null) return;
        
        tatarWordLabel.setText(currentWord.getTatar());
        russianWordLabel.setText(currentWord.getRussian());
        
        showExamples(currentWord);
        
        isFlipped = false;
        cardFront.setVisible(true);
        cardFront.setManaged(true);
        cardBack.setVisible(false);
        cardBack.setManaged(false);
        
        // Обновляем цвет в зависимости от прогресса
        tatarWordLabel.getStyleClass().removeAll("progress-zero", "progress-low", "progress-medium", "progress-high");
        
        if (currentWord.getTimesCorrect() >= 5) {
            tatarWordLabel.getStyleClass().add("progress-high");
        } else if (currentWord.getTimesCorrect() >= 2) {
            tatarWordLabel.getStyleClass().add("progress-medium");
        } else if (currentWord.getTimesCorrect() > 0) {
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
            examplesScroll.setVisible(true);
            Label noExamplesLabel = new Label("Нет примеров для этого слова");
            noExamplesLabel.getStyleClass().add("no-examples-label");
            examplesList.getChildren().add(noExamplesLabel);
        }
    }
    
    private void updateProgress() {
        if (currentWord == null) {
            progressLabel.setText("📊 Нет слов");
            return;
        }
        
        int total = learningQueue.size();
        int position = currentIndex + 1;
        
        String nextReview = "сегодня";
        if (currentWord.getLastReviewed() != null && currentWord.getTimesCorrect() > 0) {
            int interval = getNextInterval(currentWord.getTimesCorrect());
            LocalDate nextDate = currentWord.getLastReviewed().plusDays(interval);
            if (nextDate.isAfter(LocalDate.now())) {
                long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), nextDate);
                nextReview = "через " + daysLeft + " дн.";
            }
        }
        
        progressLabel.setText(String.format(
            "📊 Слово %d из %d | Правильных: %d | След: %s",
            position, total, currentWord.getTimesCorrect(), nextReview
        ));
    }
    
    private int getNextInterval(int timesCorrect) {
        if (timesCorrect >= INTERVALS.length) {
            return INTERVALS[INTERVALS.length - 1];
        }
        return INTERVALS[timesCorrect];
    }
    
    private void showTemporaryMessage(String message, Button button) {
        String originalText = button.getText();
        button.setText(message);
        
        PauseTransition pause = new PauseTransition(Duration.seconds(1));
        pause.setOnFinished(e -> button.setText(originalText));
        pause.play();
    }
    
    private void showCongratsDialog() {
        // Подсчитываем статистику
        int learned = 0;
        int inProgress = 0;
        int newCount = 0;
        
        for (Word w : allWords) {
            if (w.getTimesCorrect() >= 5) learned++;
            else if (w.getTimesCorrect() > 0) inProgress++;
            else newCount++;
        }
        
        // Формируем информацию о категории
        String categoryInfo;
        if (currentFilterCategory == null || currentFilterCategory.equals("Все категории")) {
            categoryInfo = "📚 Категория: Все категории";
        } else {
            categoryInfo = "📚 Категория: " + currentFilterCategory;
        }
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("🎉 Отличная работа!");
        alert.setHeaderText("Вы завершили сегодняшнюю сессию!");
        alert.setContentText(String.format(
            "📊 Статистика на сегодня:\n\n" +
            "%s\n" +
            "✅ Выучено (5+ раз): %d\n" +
            "📖 В процессе: %d\n" +
            "🆕 Новых слов: %d\n\n" +
            "🎯 Завтра вас ждут новые слова для повторения!\n" +
            "Продолжайте в том же духе! 💪",
            categoryInfo, learned, inProgress, newCount
        ));
        alert.showAndWait();
        
        // Закрываем окно обучения
        Stage stage = (Stage) progressLabel.getScene().getWindow();
        stage.close();
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}