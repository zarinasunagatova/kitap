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
    private static final String EXERCISE_TYPE = "typing";
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            grammarService = GrammarService.getInstance();
            
            // Получаем валидные упражнения нужных типов
            List<GrammarExercise> validTyping = grammarService.getExercisesByType(EXERCISE_TYPE);
            
            // Фильтруем только валидные (хотя сервис уже должен отфильтровать)
            currentExercises = new ArrayList<>();
            currentExercises.addAll(validTyping);
            
            // Проверяем, есть ли хоть какие-то упражнения
            if (currentExercises.isEmpty()) {
                showAlert("Нет доступных упражнений", 
                    "Нет корректных упражнений для отображения.\n" +
                    "Проверьте файл exercises.json на наличие ошибок.");
                return;
            }
            
            setupCategoryFilter();
            setupButtons();
            setupEnterKeyHandler();
            loadNewQuestion();
            updateStats();
            updateProgress();
            
        } catch (Exception e) {
            System.err.println("Error initializing MultipleChoiceController: " + e.getMessage());
            e.printStackTrace();
        }
    }

    
    private void setupCategoryFilter() {
        if (categoryCombo != null) {
            categoryCombo.getItems().add("Все категории");
            
            // ИСПРАВЛЕНО: используем ТОЛЬКО категории упражнений
            Set<String> categories = grammarService.getExerciseCategories();
            if (categories != null && !categories.isEmpty()) {
                categoryCombo.getItems().addAll(categories);
            }
            
            categoryCombo.getSelectionModel().selectFirst();
            categoryCombo.setOnAction(e -> filterByCategory());
        }
    }
    
    private void filterByCategory() {
        String selectedCategory = categoryCombo.getValue();
        
        // Сохраняем предыдущую категорию
        String previousCategory = null;
        if (!currentExercises.isEmpty() && currentExercises.get(0) != null) {
            previousCategory = currentExercises.get(0).getCategory();
        }
        
        // Фильтруем по типу И категории
        List<GrammarExercise> newExercises = grammarService.getExercisesByTypeAndCategory(EXERCISE_TYPE, selectedCategory);
        
        if (newExercises.isEmpty()) {
            // Показываем пустое состояние
            showEmptyCategoryState(selectedCategory);
            return;
        }
        
        // Обновляем список упражнений
        currentExercises = newExercises;
        
        // Сбрасываем статистику
        answeredExercises.clear();
        totalAttempts = 0;
        correctAttempts = 0;
        
        // Загружаем новый вопрос
        loadNewQuestion();
        updateStats();
        updateProgress();
        
        // Очищаем сообщение о пустой категории
        clearEmptyStateMessage();
    }

    private void showEmptyCategoryState(String category) {
        // Очищаем вопрос
        if (questionLabel != null) {
            questionLabel.setText("📭 В категории \"" + category + "\" нет упражнений для ввода");
            questionLabel.setStyle("-fx-text-fill: #b4654d; -fx-font-size: 18px;");
        }
        
        // Очищаем и отключаем поле ввода
        if (answerField != null) {
            answerField.clear();
            answerField.setDisable(true);
            answerField.setPromptText("Нет доступных упражнений");
        }
        
        // Очищаем подсказку
        if (hintLabel != null) {
            hintLabel.setText("");
        }
        
        if (categoryLabel != null) {
            categoryLabel.setText("📚 Категория: " + category + " (пусто)");
        }
        
        // Отключаем кнопки
        if (checkButton != null) checkButton.setDisable(true);
        if (nextButton != null) nextButton.setDisable(true);
        
        // Очищаем фидбек
        if (feedbackLabel != null) {
            feedbackLabel.setText("Выберите другую категорию с упражнениями");
            feedbackLabel.setStyle("-fx-text-fill: #b4654d; -fx-font-size: 14px; -fx-font-style: italic;");
        }
        
        // Обновляем прогресс
        if (progressLabel != null) {
            progressLabel.setText("📈 Прогресс: 0 из 0 упражнений");
        }
        
        // Обновляем статистику
        if (statsLabel != null) {
            statsLabel.setText("📊 Статистика: 0/0 (0%)");
        }
        
        // Очищаем текущее упражнение
        currentExercise = null;
    }

    private void clearEmptyStateMessage() {
        if (questionLabel != null) {
            questionLabel.setStyle("-fx-text-fill: #4a2c5a; -fx-font-size: 20px;");
        }
        if (answerField != null) {
            answerField.setDisable(false);
            answerField.setPromptText("Введите ответ...");
        }
        if (feedbackLabel != null) {
            feedbackLabel.setStyle("");
            feedbackLabel.setText("");
        }
        if (checkButton != null) checkButton.setDisable(false);
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
     * - опечаток (с учетом длины слова)
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
        
        if (checkNumericAnswer(normalizedUser, normalizedCorrect)) {
            return true;
        }
        
        // 3. Проверка на опечатки с учетом длины слова
        int distance = levenshteinDistance(normalizedUser, normalizedCorrect);
        int maxAllowed = getMaxAllowedDistance(normalizedCorrect.length());
        
        if (distance <= maxAllowed && distance > 0) {
            // Логируем опечатку для отладки
            System.out.println("⚠️ Опечатка в слове '" + correctAnswer + 
                               "': расстояние " + distance + 
                               " (допустимо " + maxAllowed + ")");
            return true;
        }
        
        // 4. Проверка на раскладку (если русский набрали английскими буквами)
        String transliterated = transliterateFromEnglish(normalizedUser);
        if (transliterated.equals(normalizedCorrect)) {
            System.out.println("⚠️ Исправлена раскладка: '" + userAnswer + "' → '" + transliterated + "'");
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
    
    private boolean isNumericAnswer(String answer) {
        return answer.matches("\\d+");
    }

    /**
     * Проверка числовых ответов - строгое равенство
     */
    private boolean checkNumericAnswer(String userAnswer, String correctAnswer) {
        if (isNumericAnswer(correctAnswer)) {
            return userAnswer.equals(correctAnswer);
        }
        return false;
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
    
    private int getMaxAllowedDistance(int wordLength) {
        if (wordLength <= 3) {
            return 0;  // Слова до 3 букв - без ошибок
        } else if (wordLength <= 5) {
            return 1;  // Слова 4-5 букв - максимум 1 ошибка
        } else if (wordLength <= 8) {
            return 1;  // Слова 6-8 букв - максимум 1 ошибка
        } else if (wordLength <= 12) {
            return 2;  // Слова 9-12 букв - максимум 2 ошибки
        } else {
            return 2;  // Длинные слова - максимум 2 ошибки
        }
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