package com.tatar.learn.controllers;

import com.tatar.learn.models.GrammarRule;
import com.tatar.learn.models.Topic;
import com.tatar.learn.services.TopicsService;
import com.tatar.learn.models.Word;
import com.tatar.learn.services.DatabaseService;
import com.tatar.learn.services.TTSService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.io.InputStream;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

public class TopicLearnController implements Initializable {
    
    // ========== ПОЛЯ ИЗ FXML ==========
	@FXML private TabPane lessonTabPane; 
    @FXML private Label topicIconLabel;
    @FXML private Label topicNameLabel;
    @FXML private Label topicNameTatarLabel;
    @FXML private Label progressLabel;
    @FXML private Label queueInfoLabel;
    @FXML private Label voiceStatusLabel;
    
    // Вкладка Слова
    @FXML private Label tatarWordLabel;
    @FXML private Label russianWordLabel;
    @FXML private Button playButton;
    @FXML private VBox examplesContainer;
    @FXML private ScrollPane examplesScroll;
    @FXML private VBox examplesList;
    @FXML private Button prevWordButton;
    @FXML private Button nextWordButton;
    @FXML private Label wordCounterLabel;
    @FXML private Button markLearnedButton;
    
    // Вкладка Грамматика
    @FXML private Label grammarTitleLabel;
    @FXML private Label grammarTitleTatarLabel;
    @FXML private Label grammarExplanationLabel;
    @FXML private Label grammarExamplesLabel;
    
    // Вкладка Тест
    @FXML private Label testQuestionLabel;
    @FXML private VBox testOptionsContainer;
    @FXML private Label testFeedbackLabel;
    @FXML private Button testNextButton;
    @FXML private Button testCompleteButton;
    
    // Кнопка выхода
    @FXML private Button exitButton;
    
    // ========== СЕРВИСЫ И ДАННЫЕ ==========
    private DatabaseService dbService;
    private TTSService ttsService;
    private TopicsService topicsService;
    private Topic currentTopic;
    private List<Word> wordsList;
    private int currentWordIndex = 0;
    private int learnedCount = 0;
    
    // Для теста
    private List<Word> testWords;
    private int currentTestIndex = 0;
    private int testScore = 0;
    private Map<String, Button> testButtons = new HashMap<>();
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            dbService = DatabaseService.getInstance();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        topicsService = TopicsService.getInstance();
        ttsService = TTSService.getInstance();
        
        if (voiceStatusLabel != null) {
            voiceStatusLabel.setText(ttsService.getStatus());
        }
        
        setupButtons();
    }
    
    public void setTopic(Topic topic) {
        this.currentTopic = topic;
        
        // Заголовок с безопасной установкой
        if (topicIconLabel != null) {
            String icon = topic.getIcon();
            topicIconLabel.setText(icon != null ? icon : "📚");
        }
        if (topicNameLabel != null) {
            String name = topic.getName();
            topicNameLabel.setText(name != null ? name : "Тема");
        }
        if (topicNameTatarLabel != null) {
            String nameTatar = topic.getNameTatar();
            topicNameTatarLabel.setText(nameTatar != null ? nameTatar : "");
        }
        
        // ===== ВАЖНО: Правильная инициализация слов =====
        wordsList = new ArrayList<>();
        if (topic.getWords() != null && !topic.getWords().isEmpty()) {
            wordsList.addAll(topic.getWords());
            System.out.println("DEBUG: Загружено слов в wordsList: " + wordsList.size());
            for (Word w : wordsList) {
                System.out.println("  - " + w.getTatar() + " = " + w.getRussian());
            }
        } else {
            System.err.println("ERROR: topic.getWords() пуст или null!");
            // Создаем тестовые слова для отладки
            wordsList.add(new Word("исәнме", "здравствуйте", "Приветствия"));
            wordsList.add(new Word("сау бул", "до свидания", "Приветствия"));
        }
        
        currentWordIndex = 0;
        learnedCount = 0;
        
        // Показываем первое слово
        showCurrentWord();
        updateWordCounter();
        updateProgress();
        updateQueueInfo();
        
        // Грамматика
        loadGrammar();
        
        // Тест
        loadTest();
        
        System.out.println("=== НАЧАЛО УРОКА: " + topic.getName() + " ===");
        System.out.println("Слов в теме: " + wordsList.size());
    }
    
    private void setupButtons() {
        // Навигация по словам
        if (prevWordButton != null) {
            prevWordButton.setOnAction(e -> previousWord());
        }
        if (nextWordButton != null) {
            nextWordButton.setOnAction(e -> nextWord());
        }
        
        // Озвучивание
        if (playButton != null) {
            playButton.setOnAction(e -> playCurrentWord());
        }
        
        // Отметить выученным
        if (markLearnedButton != null) {
            markLearnedButton.setOnAction(e -> markCurrentWordAsLearned());
        }
        
        // Тест
        if (testNextButton != null) {
            testNextButton.setOnAction(e -> nextTestQuestion());
        }
        if (testCompleteButton != null) {
            testCompleteButton.setOnAction(e -> completeLesson());
        }
        
        // Выход
        if (exitButton != null) {
            exitButton.setOnAction(e -> exitLesson());
        }
    }
    
    // ========== МЕТОДЫ ДЛЯ РАБОТЫ СО СЛОВАМИ ==========
    
    private void showCurrentWord() {
        if (wordsList == null || wordsList.isEmpty()) {
            System.err.println("ERROR: wordsList пуст в showCurrentWord()");
            if (tatarWordLabel != null) tatarWordLabel.setText("???");
            if (russianWordLabel != null) russianWordLabel.setText("Слова не загружены");
            return;
        }
        
        if (currentWordIndex < 0 || currentWordIndex >= wordsList.size()) {
            currentWordIndex = 0;
        }
        
        Word word = wordsList.get(currentWordIndex);
        
        if (word == null) {
            System.err.println("ERROR: word is null at index " + currentWordIndex);
            return;
        }
        
        String tatar = word.getTatar();
        String russian = word.getRussian();
        
        System.out.println("DEBUG: Показываем слово: " + tatar + " = " + russian);
        
        if (tatarWordLabel != null) {
            tatarWordLabel.setText(tatar != null ? tatar : "???");
        }
        if (russianWordLabel != null) {
            russianWordLabel.setText(russian != null ? russian : "???");
        }
        
        showExamples(word);
    }
    
    private void showExamples(Word word) {
        if (examplesList == null) return;
        examplesList.getChildren().clear();
        
        if (word.getExamples() != null && !word.getExamples().isEmpty()) {
            if (examplesScroll != null) {
                examplesScroll.setVisible(true);
                examplesScroll.setManaged(true);
            }
            
            for (String example : word.getExamples()) {
                HBox exampleBox = new HBox();
                exampleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                exampleBox.getStyleClass().add("lesson-example-item");
                exampleBox.setSpacing(8);
                
                Label iconLabel = new Label("📖");
                iconLabel.setStyle("-fx-font-size: 14px;");
                
                Label exampleLabel = new Label(example);
                exampleLabel.getStyleClass().add("lesson-example-text");
                exampleLabel.setWrapText(true);
                
                exampleBox.getChildren().addAll(iconLabel, exampleLabel);
                examplesList.getChildren().add(exampleBox);
            }
        } else {
            Label noExamplesLabel = new Label("Нет примеров для этого слова");
            noExamplesLabel.setStyle("-fx-text-fill: #b5a3df; -fx-font-style: italic;");
            examplesList.getChildren().add(noExamplesLabel);
        }
    }
    
    private void updateWordCounter() {
        if (wordCounterLabel != null) {
            wordCounterLabel.setText((currentWordIndex + 1) + " / " + wordsList.size());
        }
    }
    
    private void updateProgress() {
        if (progressLabel != null) {
            progressLabel.setText("📊 Прогресс: " + learnedCount + " / " + wordsList.size() + " слов");
        }
    }
    
    private void updateQueueInfo() {
        if (queueInfoLabel != null) {
            int remaining = wordsList.size() - learnedCount;
            queueInfoLabel.setText("📚 Осталось: " + remaining + " слов");
        }
    }
    
    private void previousWord() {
        if (currentWordIndex > 0) {
            currentWordIndex--;
            showCurrentWord();
            updateWordCounter();
        } else {
            showTemporaryMessage("Это первое слово", prevWordButton);
        }
    }
    
    private void nextWord() {
        if (currentWordIndex < wordsList.size() - 1) {
            currentWordIndex++;
            showCurrentWord();
            updateWordCounter();
        } else {
            showTemporaryMessage("Это последнее слово", nextWordButton);
        }
    }
    
    private void playCurrentWord() {
        if (wordsList != null && !wordsList.isEmpty()) {
            Word word = wordsList.get(currentWordIndex);
            ttsService.speakWithFeedback(word.getTatar(), playButton, "🔊");
        }
    }
    
    private void markCurrentWordAsLearned() {
        Word word = wordsList.get(currentWordIndex);
        
        // Если слово еще не отмечено как выученное
        if (word.getTimesCorrect() < 3) {
            word.setTimesCorrect(3);
            word.setLastReviewed(LocalDate.now());
            
            try {
                dbService.updateWord(word);
                learnedCount++;
                updateProgress();
                updateQueueInfo();
                showTemporaryMessage("✅ Слово отмечено как выученное!", markLearnedButton);
                
                // Проверяем, все ли слова выучены
                if (learnedCount >= wordsList.size()) {
                    // Переключаемся на вкладку теста
                    if (lessonTabPane != null) {
                        lessonTabPane.getSelectionModel().select(2); // Тест
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                showTemporaryMessage("❌ Ошибка сохранения", markLearnedButton);
            }
        } else {
            showTemporaryMessage("ℹ️ Это слово уже выучено", markLearnedButton);
        }
    }
    
    // ========== ГРАММАТИКА ==========
    
    private void loadGrammar() {
        if (currentTopic.getGrammar() != null) {
            GrammarRule grammar = currentTopic.getGrammar();
            
            String title = grammar.getTitle() != null ? grammar.getTitle() : "Грамматика";
            String explanation = grammar.getExplanation() != null ? grammar.getExplanation() : "Нет описания";
            String examples = grammar.getExamples() != null ? grammar.getExamples() : "";
            
            grammarTitleLabel.setText(title);
            grammarTitleTatarLabel.setText(title);
            grammarExplanationLabel.setText(explanation);
            grammarExamplesLabel.setText(examples);
        } else {
            grammarTitleLabel.setText("Грамматика темы");
            grammarTitleTatarLabel.setText("");
            grammarExplanationLabel.setText("В этой теме пока нет грамматического материала.");
            grammarExamplesLabel.setText("");
        }
    }
    
    // ========== ТЕСТ ==========
    
    private void loadTest() {
        testWords = new ArrayList<>(wordsList);
        currentTestIndex = 0;
        testScore = 0;
        showTestQuestion();
    }
    
    private void showTestQuestion() {
        if (currentTestIndex >= testWords.size()) {
            showTestResults();
            return;
        }
        
        Word word = testWords.get(currentTestIndex);
        
        // Проверка на null
        String tatar = word.getTatar() != null ? word.getTatar() : "???";
        String russian = word.getRussian() != null ? word.getRussian() : "???";
        
        testQuestionLabel.setText("Как переводится слово \"" + tatar + "\"?");
        
        // Генерируем варианты
        List<String> options = new ArrayList<>();
        options.add(russian);
        
        // Добавляем случайные варианты из других слов
        List<String> otherTranslations = new ArrayList<>();
        for (Word w : testWords) {
            String otherRussian = w.getRussian();
            if (otherRussian != null && !otherRussian.equals(russian)) {
                otherTranslations.add(otherRussian);
            }
        }
        Collections.shuffle(otherTranslations);
        for (int i = 0; i < Math.min(3, otherTranslations.size()); i++) {
            options.add(otherTranslations.get(i));
        }
        Collections.shuffle(options);
        
        testOptionsContainer.getChildren().clear();
        testButtons.clear();
        
        for (String option : options) {
            Button btn = new Button(option);
            btn.getStyleClass().add("lesson-test-option");
            btn.setOnAction(e -> checkTestAnswer(btn, russian));
            testOptionsContainer.getChildren().add(btn);
            testButtons.put(option, btn);
        }
        
        testFeedbackLabel.setText("");
        testNextButton.setVisible(false);
        testCompleteButton.setVisible(false);
    }
    
    private void checkTestAnswer(Button selectedBtn, String correctAnswer) {
        boolean isCorrect = selectedBtn.getText().equals(correctAnswer);
        
        // Отключаем все кнопки
        for (Button btn : testButtons.values()) {
            btn.setDisable(true);
            if (btn.getText().equals(correctAnswer)) {
                btn.getStyleClass().add("lesson-test-option-correct");
            } else if (btn == selectedBtn && !isCorrect) {
                btn.getStyleClass().add("lesson-test-option-wrong");
            }
        }
        
        if (isCorrect) {
            testScore++;
            testFeedbackLabel.setText("✅ Правильно! +1 балл");
            testFeedbackLabel.setStyle("-fx-text-fill: #7cb87a; -fx-font-weight: bold;");
        } else {
            testFeedbackLabel.setText("❌ Неправильно! Правильный ответ: " + correctAnswer);
            testFeedbackLabel.setStyle("-fx-text-fill: #e8a898; -fx-font-weight: bold;");
        }
        
        testNextButton.setVisible(true);
    }
    
    private void nextTestQuestion() {
        currentTestIndex++;
        showTestQuestion();
    }
    
    private void showTestResults() {
        int maxScore = testWords.size();
        int percentage = (testScore * 100) / maxScore;
        
        String resultMessage;
        if (percentage >= 80) {
            resultMessage = "🎉 Отлично! Вы усвоили материал!";
        } else if (percentage >= 60) {
            resultMessage = "👍 Хорошо! Повторите еще раз для лучшего результата.";
        } else {
            resultMessage = "📖 Стоит повторить слова перед тестом.";
        }
        
        testQuestionLabel.setText("📊 Результаты теста");
        testOptionsContainer.getChildren().clear();
        
        Label resultLabel = new Label(String.format("Правильных ответов: %d из %d\n\n%s", 
            testScore, maxScore, resultMessage));
        resultLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #4a3859; -fx-wrap-text: true; -fx-alignment: center;");
        resultLabel.setMaxWidth(500);
        
        testOptionsContainer.getChildren().add(resultLabel);
        testFeedbackLabel.setText("");
        testNextButton.setVisible(false);
        testCompleteButton.setVisible(true);
    }
    
    // ========== ЗАВЕРШЕНИЕ УРОКА ==========
    
    private void completeLesson() {
        topicsService.completeTopic(currentTopic.getName());
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("🎉 Урок завершен!");
        alert.setHeaderText("Поздравляем! Вы завершили тему \"" + currentTopic.getName() + "\"");
        alert.setContentText(String.format(
            "📊 Результаты:\n\n" +
            "✅ Выучено слов: %d из %d\n" +
            "📖 Грамматика: %s\n" +
            "🎯 Тест: %d/%d (%d%%)\n\n" +
            "Теперь эти слова доступны в словаре и карточках!",
            learnedCount, wordsList.size(),
            currentTopic.getGrammar() != null ? currentTopic.getGrammar().getTitle() : "—",
            testScore, wordsList.size(), 
            wordsList.size() > 0 ? (testScore * 100) / wordsList.size() : 0
        ));
        
        // Устанавливаем иконку
        setAlertIcon(alert);
        
        alert.showAndWait();
        
        Stage stage = (Stage) exitButton.getScene().getWindow();
        stage.close();
    }

    private void exitLesson() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Выход из урока");
        confirm.setHeaderText("Вы хотите выйти из урока?");
        confirm.setContentText("Весь прогресс по этой теме будет сохранен.");
        setAlertIcon(confirm);
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Stage stage = (Stage) exitButton.getScene().getWindow();
            stage.close();
        }
    }

    // Добавьте методы для иконки:
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
    
    private void showTemporaryMessage(String message, Button button) {
        String originalText = button.getText();
        button.setText(message);
        PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
        pause.setOnFinished(e -> button.setText(originalText));
        pause.play();
    }
}