package com.tatar.learn.controllers;

import com.tatar.learn.models.GrammarExercise;
import com.tatar.learn.services.GrammarService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import java.net.URL;
import java.text.Normalizer;
import java.util.*;

public class TypingController implements Initializable {
    
    @FXML private Label questionLabel;
    @FXML private Label feedbackLabel;
    @FXML private TextField answerField;
    @FXML private Button checkButton;
    @FXML private Button nextButton;
    @FXML private Label hintLabel;
    @FXML private Label statsLabel;
    @FXML private Label categoryLabel;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private Label progressLabel;
    
    private GrammarService grammarService;
    private List<GrammarExercise> currentExercises;
    private GrammarExercise currentExercise;
    private Random random = new Random();
    
    // Статистика за сессию
    private int totalAttempts = 0;
    private int correctAttempts = 0;
    private List<GrammarExercise> answeredExercises = new ArrayList<>();
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        grammarService = GrammarService.getInstance();
        currentExercises = new ArrayList<>(grammarService.getAllExercises());
        
        if (currentExercises.isEmpty()) {
            showAlert("Нет упражнений", "Сначала добавьте грамматические упражнения в файл exercises.json");
            return;
        }
        
        setupCategoryFilter();
        setupButtons();
        setupEnterKeyHandler();
        loadNewQuestion();
        updateStats();
        updateProgress();
    }
    
    private void setupCategoryFilter() {
        if (categoryCombo != null) {
            categoryCombo.getItems().add("Все категории");
            Set<String> categories = grammarService.getCategories();
            if (categories != null && !categories.isEmpty()) {
                categoryCombo.getItems().addAll(categories);
            }
            categoryCombo.getSelectionModel().selectFirst();
            
            categoryCombo.setOnAction(e -> filterByCategory());
        }
    }
    
    private void filterByCategory() {
        String selectedCategory = categoryCombo.getValue();
        
        if (selectedCategory == null || selectedCategory.equals("Все категории")) {
            currentExercises = new ArrayList<>(grammarService.getAllExercises());
        } else {
            currentExercises = grammarService.getExercisesByCategory(selectedCategory);
        }
        
        if (currentExercises.isEmpty()) {
            showAlert("Нет упражнений", "В этой категории нет упражнений");
            return;
        }
        
        // Сбрасываем статистику при смене категории
        answeredExercises.clear();
        totalAttempts = 0;
        correctAttempts = 0;
        
        loadNewQuestion();
        updateStats();
        updateProgress();
    }
    
    private void setupButtons() {
        checkButton.setOnAction(e -> checkAnswer());
        nextButton.setOnAction(e -> loadNewQuestion());
    }
    
    private void setupEnterKeyHandler() {
        answerField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                checkAnswer();
            }
        });
    }
    
    private void loadNewQuestion() {
        // Выбираем неотвеченное упражнение
        List<GrammarExercise> unanswered = new ArrayList<>(currentExercises);
        unanswered.removeAll(answeredExercises);
        
        if (unanswered.isEmpty()) {
            // Все упражнения пройдены
            questionLabel.setText("🎉 Поздравляем! Вы прошли все упражнения в этой категории! 🎉");
            answerField.setDisable(true);
            checkButton.setDisable(true);
            nextButton.setDisable(true);
            feedbackLabel.setText("Отличная работа! Выберите другую категорию.");
            if (hintLabel != null) hintLabel.setText("");
            if (categoryLabel != null) categoryLabel.setText("");
            return;
        }
        
        currentExercise = unanswered.get(random.nextInt(unanswered.size()));
        
        questionLabel.setText("📝 " + currentExercise.getQuestion());
        answerField.clear();
        answerField.setDisable(false);
        answerField.requestFocus();
        
        feedbackLabel.setText("");
        
        // Генерируем подсказку
        if (hintLabel != null) {
            hintLabel.setText("💡 Подсказка: " + generateHint(currentExercise));
        }
        
        if (categoryLabel != null) {
            categoryLabel.setText("📚 Категория: " + currentExercise.getCategory());
        }
        
        checkButton.setDisable(false);
        nextButton.setDisable(true);
        
        updateProgress();
    }
    
    /**
     * Умная проверка ответа для грамматических упражнений
     */
    @FXML
    private void checkAnswer() {
        String userAnswer = answerField.getText().trim();
        String correctAnswer = currentExercise.getCorrectAnswer();
        
        boolean isCorrect = checkAnswerSmart(userAnswer, correctAnswer);
        
        if (isCorrect) {
            correctAttempts++;
            String feedback = "✅ Правильно!";
            if (currentExercise.getExplanation() != null && !currentExercise.getExplanation().isEmpty()) {
                feedback += "\n📖 " + currentExercise.getExplanation();
            }
            showFeedback(feedback, "correct");
        } else {
            String feedback = "❌ Неправильно.\nПравильный ответ: " + correctAnswer;
            if (currentExercise.getExplanation() != null && !currentExercise.getExplanation().isEmpty()) {
                feedback += "\n📖 " + currentExercise.getExplanation();
            }
            showFeedback(feedback, "wrong");
        }
        
        totalAttempts++;
        answeredExercises.add(currentExercise);
        
        answerField.setDisable(true);
        checkButton.setDisable(true);
        nextButton.setDisable(false);
        
        updateStats();
        updateProgress();
    }
    
    /**
     * Умная проверка с учетом:
     * - регистра
     * - пунктуации
     * - лишних пробелов
     * - опечаток
     * - раскладки клавиатуры
     */
    private boolean checkAnswerSmart(String userAnswer, String correctAnswer) {
        if (userAnswer == null || correctAnswer == null) return false;
        
        // 1. Нормализация
        String normalizedUser = normalizeText(userAnswer);
        String normalizedCorrect = normalizeText(correctAnswer);
        
        // 2. Точное совпадение
        if (normalizedUser.equals(normalizedCorrect)) {
            return true;
        }
        
        // 3. Проверка на опечатки (расстояние Левенштейна)
        if (levenshteinDistance(normalizedUser, normalizedCorrect) <= 2) {
            return true;
        }
        
        // 4. Проверка на раскладку (если русский набрали английскими буквами)
        String transliterated = transliterateFromEnglish(normalizedUser);
        if (transliterated.equals(normalizedCorrect)) {
            return true;
        }
        
        return false;
    }
    
    private String normalizeText(String text) {
        String normalized = text.trim().toLowerCase();
        normalized = normalized.replaceAll("[\\p{Punct}]", "");
        normalized = normalized.replaceAll("\\s+", " ");
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", ""); // Убираем диакритические знаки
        return normalized.trim();
    }
    
    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        
        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                        dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1),
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1)
                    );
                }
            }
        }
        return dp[a.length()][b.length()];
    }
    
    private String transliterateFromEnglish(String text) {
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case 'q': result.append('й'); break;
                case 'w': result.append('ц'); break;
                case 'e': result.append('у'); break;
                case 'r': result.append('к'); break;
                case 't': result.append('е'); break;
                case 'y': result.append('н'); break;
                case 'u': result.append('г'); break;
                case 'i': result.append('ш'); break;
                case 'o': result.append('щ'); break;
                case 'p': result.append('з'); break;
                case 'a': result.append('ф'); break;
                case 's': result.append('ы'); break;
                case 'd': result.append('в'); break;
                case 'f': result.append('а'); break;
                case 'g': result.append('п'); break;
                case 'h': result.append('р'); break;
                case 'j': result.append('о'); break;
                case 'k': result.append('л'); break;
                case 'l': result.append('д'); break;
                case 'z': result.append('я'); break;
                case 'x': result.append('ч'); break;
                case 'c': result.append('с'); break;
                case 'v': result.append('м'); break;
                case 'b': result.append('и'); break;
                case 'n': result.append('т'); break;
                case 'm': result.append('ь'); break;
                default: result.append(c);
            }
        }
        return result.toString();
    }
    
    private String generateHint(GrammarExercise exercise) {
        StringBuilder hint = new StringBuilder();
        String correctAnswer = exercise.getCorrectAnswer();
        
        hint.append("длина ответа: ").append(correctAnswer.length()).append(" симв., ");
        
        // Показываем первую букву
        if (!correctAnswer.isEmpty()) {
            hint.append("начинается на '").append(correctAnswer.charAt(0)).append("'");
        }
        
        // Если ответ состоит из нескольких слов, показываем количество слов
        String[] words = correctAnswer.split("\\s+");
        if (words.length > 1) {
            hint.append(", ").append(words.length).append(" слова");
        }
        
        return hint.toString();
    }
    
    private void showFeedback(String message, String type) {
        feedbackLabel.setText(message);
        if (type.equals("correct")) {
            feedbackLabel.setStyle("-fx-text-fill: #2c7a4c; -fx-font-weight: bold; -fx-font-size: 16px;");
        } else {
            feedbackLabel.setStyle("-fx-text-fill: #d45d79; -fx-font-weight: bold; -fx-font-size: 16px;");
        }
    }
    
    private void updateStats() {
        if (statsLabel != null) {
            if (totalAttempts > 0) {
                int percent = (correctAttempts * 100) / totalAttempts;
                statsLabel.setText(String.format("📊 Статистика: %d/%d правильных (%d%%)", 
                    correctAttempts, totalAttempts, percent));
            } else {
                statsLabel.setText("📊 Статистика: 0/0 (0%)");
            }
        }
    }
    
    private void updateProgress() {
        if (progressLabel != null) {
            int answered = answeredExercises.size();
            int total = currentExercises.size();
            progressLabel.setText(String.format("📈 Прогресс: %d из %d упражнений", answered, total));
        }
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}