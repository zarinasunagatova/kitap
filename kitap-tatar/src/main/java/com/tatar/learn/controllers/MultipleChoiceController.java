package com.tatar.learn.controllers;

import com.tatar.learn.models.GrammarExercise;
import com.tatar.learn.services.GrammarService;
import com.tatar.learn.services.TopicsService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.event.ActionEvent;
import java.net.URL;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultipleChoiceController implements Initializable {
    
    @FXML private Label questionLabel;
    @FXML private Label feedbackLabel;
    @FXML private Button option1, option2, option3, option4;
    @FXML private Button nextButton;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private Label progressLabel;
    @FXML private Label statsLabel;
    
    // Matching компоненты
    @FXML private VBox matchingContainer;
    @FXML private Label matchingHeader;
    @FXML private GridPane matchingGrid;
    @FXML private Button checkMatchingButton;
    @FXML private GridPane multipleChoiceGrid;
    
    private GrammarService grammarService;
    private List<GrammarExercise> currentExercises;
    private GrammarExercise currentExercise;
    private Random random = new Random();
    private static final Logger log = LoggerFactory.getLogger(MultipleChoiceController.class);
    
    // Флаг режима и константа типа
    private boolean isMatchingMode = false;
    private static final String MULTIPLE_CHOICE_TYPE = "multiple_choice";
    private static final String MATCHING_TYPE = "matching";
    
    private List<MatchingItem> matchingItems;
    private Map<String, ComboBox<String>> matchingSelections;
    
    private int totalAttempts = 0;
    private int correctAttempts = 0;
    private List<GrammarExercise> answeredExercises = new ArrayList<>();
    
    private static class MatchingItem {
        String tatarWord;
        String correctTranslation;
        
        MatchingItem(String tatarWord, String correctTranslation) {
            this.tatarWord = tatarWord;
            this.correctTranslation = correctTranslation;
        }
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            grammarService = GrammarService.getInstance();
            TopicsService topicsService = TopicsService.getInstance();
            
            // ===== получаем ТОЛЬКО упражнения из пройденных тем =====
            Set<String> completedTopics = new HashSet<>(topicsService.getCompletedTopicNames());
            log.info("Пройденные темы: " + completedTopics);
            
            List<GrammarExercise> allMultipleChoice = grammarService.getExercisesByType(MULTIPLE_CHOICE_TYPE);
            List<GrammarExercise> allMatching = grammarService.getExercisesByType(MATCHING_TYPE);
            
            // Фильтруем по категориям, которые соответствуют пройденным темам
            currentExercises = new ArrayList<>();
            
            for (GrammarExercise ex : allMultipleChoice) {
                if (isExerciseUnlocked(ex, completedTopics)) {
                    currentExercises.add(ex);
                }
            }
            for (GrammarExercise ex : allMatching) {
                if (isExerciseUnlocked(ex, completedTopics)) {
                    currentExercises.add(ex);
                }
            }
            
            // Проверяем, есть ли хоть какие-то упражнения
            if (currentExercises.isEmpty()) {
                showNoExercisesMessage();
                return;
            }
            
            initializeUI();
            loadNewQuestion();
            updateStats();
            updateProgress();
            
        } catch (Exception e) {
            System.err.println("Error initializing MultipleChoiceController: " + e.getMessage());
            log.error("Ошибка", e);
        }
    }
    
    private void initializeUI() {
    	if (categoryCombo != null) {
            categoryCombo.getItems().add("Все категории");
            
            // ===== только категории из ПРОЙДЕННЫХ тем =====
            TopicsService topicsService = TopicsService.getInstance();
            Set<String> completedTopics = new HashSet<>(topicsService.getCompletedTopicNames());
            
            Set<String> availableCategories = new HashSet<>();
            for (GrammarExercise ex : currentExercises) {
                String category = ex.getCategory();
                if (category != null && completedTopics.contains(category)) {
                    availableCategories.add(category);
                }
            }
            
            if (!availableCategories.isEmpty()) {
                categoryCombo.getItems().addAll(availableCategories);
            }
            // ===== КОНЕЦ ИСПРАВЛЕНИЯ =====
            
            categoryCombo.getSelectionModel().selectFirst();
            categoryCombo.setOnAction(e -> filterByCategory());
        }
        
        if (option1 != null) option1.setOnAction(this::checkAnswer);
        if (option2 != null) option2.setOnAction(this::checkAnswer);
        if (option3 != null) option3.setOnAction(this::checkAnswer);
        if (option4 != null) option4.setOnAction(this::checkAnswer);
        if (nextButton != null) nextButton.setOnAction(e -> loadNewQuestion());
        if (checkMatchingButton != null) checkMatchingButton.setOnAction(e -> checkMatchingAnswer());
    }
    /**
     * Проверяет, доступно ли упражнение (категория соответствует пройденной теме)
     */
    private boolean isExerciseUnlocked(GrammarExercise exercise, Set<String> completedTopics) {
        String category = exercise.getCategory();
        if (category == null) return false;
        
        // Проверяем, соответствует ли категория упражнения одной из пройденных тем
        for (String topic : completedTopics) {
            if (category.equalsIgnoreCase(topic)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Показывает сообщение, когда нет доступных упражнений
     */
    private void showNoExercisesMessage() {
        if (questionLabel != null) {
            questionLabel.setText("📭 Нет доступных упражнений");
            questionLabel.setStyle("-fx-text-fill: #b4654d; -fx-font-size: 18px;");
        }
        
        // Скрываем все контролы
        if (multipleChoiceGrid != null) {
            multipleChoiceGrid.setVisible(false);
            multipleChoiceGrid.setManaged(false);
        }
        if (matchingContainer != null) {
            matchingContainer.setVisible(false);
            matchingContainer.setManaged(false);
        }
        
        // Показываем сообщение в feedback
        if (feedbackLabel != null) {
            feedbackLabel.setText("Пройдите уроки, чтобы открыть упражнения!\nЗайдите в раздел «Уроки» и изучите темы.");
            feedbackLabel.setStyle("-fx-text-fill: #b4654d; -fx-font-size: 16px; -fx-font-style: italic;");
        }
        
        if (nextButton != null) nextButton.setDisable(true);
        if (categoryCombo != null) categoryCombo.setDisable(true);
    }

    
    private void filterByCategory() {
        try {
            String selectedCategory = categoryCombo.getValue();
            
            // Сохраняем предыдущую категорию на случай отката
            String previousCategory = null;
            if (!currentExercises.isEmpty() && currentExercises.get(0) != null) {
                previousCategory = currentExercises.get(0).getCategory();
            }
            
            // Фильтруем оба типа упражнений по категории
            List<GrammarExercise> filteredMultipleChoice = 
                grammarService.getExercisesByTypeAndCategory(MULTIPLE_CHOICE_TYPE, selectedCategory);
            List<GrammarExercise> filteredMatching = 
                grammarService.getExercisesByTypeAndCategory(MATCHING_TYPE, selectedCategory);
            
            List<GrammarExercise> newExercises = new ArrayList<>();
            newExercises.addAll(filteredMultipleChoice);
            newExercises.addAll(filteredMatching);
            
            if (newExercises.isEmpty()) {
                // Показываем пустое состояние
                showEmptyCategoryState(selectedCategory);
                
          
                categoryCombo.setValue(previousCategory != null ? previousCategory : "Все категории");
                
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
            
            // Скрываем сообщение о пустой категории, если оно было
            clearEmptyStateMessage();
            
        } catch (Exception e) {
            System.err.println("Error filtering by category: " + e.getMessage());
            showErrorState("Ошибка фильтрации: " + e.getMessage());
        }
    }
    
    private void showEmptyCategoryState(String category) {
        // Очищаем текущий вопрос и варианты
        if (questionLabel != null) {
            questionLabel.setText("📭 В категории \"" + category + "\" нет упражнений");
            questionLabel.setStyle("-fx-text-fill: #b4654d; -fx-font-size: 18px;");
        }
        
        // Очищаем кнопки
        if (option1 != null) option1.setText("");
        if (option2 != null) option2.setText("");
        if (option3 != null) option3.setText("");
        if (option4 != null) option4.setText("");
        
        // Отключаем кнопки
        setButtonsDisable(true);
        
        // Скрываем matching контейнер, если он виден
        if (matchingContainer != null) {
            matchingContainer.setVisible(false);
            matchingContainer.setManaged(false);
        }
        if (multipleChoiceGrid != null) {
            multipleChoiceGrid.setVisible(true);
            multipleChoiceGrid.setManaged(true);
        }
        
        // Очищаем фидбек
        if (feedbackLabel != null) {
            feedbackLabel.setText("Выберите другую категорию или добавьте упражнения");
            feedbackLabel.setStyle("-fx-text-fill: #b4654d; -fx-font-size: 14px; -fx-font-style: italic;");
        }
        
        // Отключаем кнопку "Следующее"
        if (nextButton != null) nextButton.setDisable(true);
        
        // Обновляем прогресс (0 из 0)
        if (progressLabel != null) {
            progressLabel.setText("📈 Прогресс: 0 из 0 упражнений");
        }
        
        // Очищаем текущее упражнение
        currentExercise = null;
        isMatchingMode = false;
    }


    private void clearEmptyStateMessage() {
        if (questionLabel != null) {
            questionLabel.setStyle("-fx-text-fill: #4a2c5a; -fx-font-size: 20px;");
        }
        if (feedbackLabel != null) {
            feedbackLabel.setStyle("");
            feedbackLabel.setText("");
        }
    }

    private void showErrorState(String errorMessage) {
        if (questionLabel != null) {
            questionLabel.setText("❌ " + errorMessage);
            questionLabel.setStyle("-fx-text-fill: #d45d79; -fx-font-size: 16px;");
        }
        setButtonsDisable(true);
        if (nextButton != null) nextButton.setDisable(true);
        if (feedbackLabel != null) feedbackLabel.setText("Попробуйте перезагрузить страницу");
    }
    
    private void loadNewQuestion() {
        try {
            List<GrammarExercise> unanswered = new ArrayList<>(currentExercises);
            unanswered.removeAll(answeredExercises);
            
            if (unanswered.isEmpty()) {
                if (questionLabel != null) {
                    questionLabel.setText("🎉 Поздравляем! Вы прошли все упражнения! 🎉");
                }
                hideAllControls();
                return;
            }
            
            currentExercise = unanswered.get(random.nextInt(unanswered.size()));
            
            if (MATCHING_TYPE.equals(currentExercise.getType())) {
                loadMatchingExercise();
            } else {
                loadMultipleChoiceExercise();
            }
            
            if (feedbackLabel != null) feedbackLabel.setText("");
            if (nextButton != null) nextButton.setDisable(true);
            
            updateProgress();
            
        } catch (Exception e) {
            System.err.println("Error loading question: " + e.getMessage());
            log.error("Ошибка", e);
            if (feedbackLabel != null) {
                feedbackLabel.setText("Ошибка загрузки вопроса: " + e.getMessage());
            }
        }
    }
    
    private void loadMultipleChoiceExercise() {
        isMatchingMode = false;
        
        if (matchingContainer != null) {
            matchingContainer.setVisible(false);
            matchingContainer.setManaged(false);
        }
        if (multipleChoiceGrid != null) {
            multipleChoiceGrid.setVisible(true);
            multipleChoiceGrid.setManaged(true);
        }
        
        if (questionLabel != null) {
            questionLabel.setText("📝 " + currentExercise.getQuestion());
            questionLabel.getStyleClass().removeAll("matching-question");
            questionLabel.getStyleClass().add("question-label");
        }
        
        List<String> options = generateOptions(currentExercise);
        
        if (options.size() >= 4) {
            if (option1 != null) option1.setText(options.get(0));
            if (option2 != null) option2.setText(options.get(1));
            if (option3 != null) option3.setText(options.get(2));
            if (option4 != null) option4.setText(options.get(3));
        }
        
        resetButtonStyles();
        setButtonsDisable(false);
    }
    
    private void loadMatchingExercise() {
        isMatchingMode = true;
        
        if (multipleChoiceGrid != null) {
            multipleChoiceGrid.setVisible(false);
            multipleChoiceGrid.setManaged(false);
        }
        if (matchingContainer != null) {
            matchingContainer.setVisible(true);
            matchingContainer.setManaged(true);
        }
        
        if (questionLabel != null) {
            questionLabel.setText("🔗 " + currentExercise.getQuestion());
            questionLabel.getStyleClass().add("matching-question");
        }
        
        parseMatchingPairs();
        buildMatchingGrid();
    }
    
    private void parseMatchingPairs() {
        matchingItems = new ArrayList<>();
        
        // Для matching упражнений пары хранятся в специальном формате
        String pairsStr = currentExercise.getExplanation();
        log.info("Parsing matching pairs: " + pairsStr);
        
        if (pairsStr != null && !pairsStr.isEmpty()) {
            String[] pairs = pairsStr.split(";");
            for (String pair : pairs) {
                if (pair.contains(":")) {
                    String[] parts = pair.split(":");
                    if (parts.length == 2) {
                        matchingItems.add(new MatchingItem(parts[0].trim(), parts[1].trim()));
                    }
                }
            }
        }
        
        // Если не удалось распарсить, используем данные из JSON напрямую
        // В реальном коде нужно получать pairs из специального поля
        // Пока используем fallback для местоимений
        if (matchingItems.isEmpty()) {
            matchingItems.add(new MatchingItem("мин", "я"));
            matchingItems.add(new MatchingItem("син", "ты"));
            matchingItems.add(new MatchingItem("ул", "он/она"));
            matchingItems.add(new MatchingItem("без", "мы"));
            matchingItems.add(new MatchingItem("сез", "вы"));
            matchingItems.add(new MatchingItem("алар", "они"));
        }
        
        Collections.shuffle(matchingItems);
    }
    
    private void buildMatchingGrid() {
        if (matchingContainer == null || matchingItems == null) return;
        
        matchingContainer.getChildren().clear();
        matchingSelections = new HashMap<>();
        
        Label headerLabel = new Label("Соедините татарские слова с правильными переводами:");
        headerLabel.getStyleClass().add("matching-header");
        
        VBox rowsContainer = new VBox(8);
        rowsContainer.getStyleClass().add("matching-grid");
        rowsContainer.setPadding(new Insets(10));
        
        List<String> allTranslations = new ArrayList<>();
        for (MatchingItem item : matchingItems) {
            allTranslations.add(item.correctTranslation);
        }
        Collections.shuffle(allTranslations);
        
        for (MatchingItem item : matchingItems) {
            HBox row = new HBox(15);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setPadding(new Insets(5, 10, 5, 10));
            
            Label tatarLabel = new Label(item.tatarWord);
            tatarLabel.setMinWidth(100);
            tatarLabel.getStyleClass().add("matching-tatar-cell");
            
            ComboBox<String> comboBox = new ComboBox<>();
            comboBox.getItems().addAll(allTranslations);
            comboBox.setPromptText("Выберите перевод");
            comboBox.setMinWidth(140);
            comboBox.setPrefWidth(150);
            comboBox.getStyleClass().add("matching-combo");
            
            matchingSelections.put(item.tatarWord, comboBox);
            
            row.getChildren().addAll(tatarLabel, comboBox);
            rowsContainer.getChildren().add(row);
        }
        
        matchingContainer.getChildren().addAll(headerLabel, rowsContainer);
        
        if (checkMatchingButton != null) {
            checkMatchingButton.setOnAction(e -> checkMatchingAnswer());
            if (!matchingContainer.getChildren().contains(checkMatchingButton)) {
                matchingContainer.getChildren().add(checkMatchingButton);
            }
        } else {
            Button checkButton = new Button("✅ Проверить все сопоставления");
            checkButton.getStyleClass().add("matching-check-button");
            checkButton.setOnAction(e -> checkMatchingAnswer());
            matchingContainer.getChildren().add(checkButton);
        }
    }
    
    private void checkMatchingAnswer() {
        if (matchingItems == null || matchingSelections == null) return;
        
        int correctCount = 0;
        int totalCount = matchingItems.size();
        StringBuilder result = new StringBuilder();
        
        for (ComboBox<String> combo : matchingSelections.values()) {
            combo.getStyleClass().removeAll("correct", "wrong");
        }
        
        for (MatchingItem item : matchingItems) {
            ComboBox<String> combo = matchingSelections.get(item.tatarWord);
            String selected = combo != null ? combo.getValue() : null;
            boolean isCorrect = selected != null && selected.equals(item.correctTranslation);
            
            if (isCorrect) {
                correctCount++;
                combo.getStyleClass().add("correct");
            } else if (selected != null && !selected.isEmpty()) {
                combo.getStyleClass().add("wrong");
            }
            
            result.append(item.tatarWord).append(" → ");
            if (selected == null || selected.isEmpty()) {
                result.append("❌ [не выбрано]");
            } else if (isCorrect) {
                result.append("✅ ").append(selected);
            } else {
                result.append("❌ ").append(selected).append(" (правильно: ").append(item.correctTranslation).append(")");
            }
            result.append("\n");
        }
        
        if (correctCount == totalCount) {
            correctAttempts++;
            feedbackLabel.setText("✅ Отлично! Все " + totalCount + " слов переведены верно!\n\n" + result.toString());
            feedbackLabel.setStyle("-fx-text-fill: #2c7a4c; -fx-font-weight: bold; -fx-font-size: 14px;");
            feedbackLabel.getStyleClass().add("matching-result-correct");
        } else {
            feedbackLabel.setText("❌ Правильно переведено " + correctCount + " из " + totalCount + " слов.\n\n" + result.toString());
            feedbackLabel.setStyle("-fx-text-fill: #d45d79; -fx-font-weight: bold; -fx-font-size: 14px;");
            feedbackLabel.getStyleClass().add("matching-result-wrong");
        }
        
        totalAttempts++;
        answeredExercises.add(currentExercise);
        if (nextButton != null) nextButton.setDisable(false);
        updateStats();
    }
    
    private List<String> generateOptions(GrammarExercise correctExercise) {
        List<String> options = new ArrayList<>();
        
        try {
            if (correctExercise.getOptions() != null && !correctExercise.getOptions().isEmpty()) {
                options.addAll(correctExercise.getOptions());
                if (!options.contains(correctExercise.getCorrectAnswer())) {
                    options.add(correctExercise.getCorrectAnswer());
                }
                Collections.shuffle(options);
                while (options.size() < 4) options.add("???");
                return options.subList(0, 4);
            }
            
            Set<String> uniqueAnswers = new HashSet<>();
            // Берем только multiple_choice упражнения для вариантов
            List<GrammarExercise> multipleChoiceExercises = 
                grammarService.getExercisesByType(MULTIPLE_CHOICE_TYPE);
            
            for (GrammarExercise ex : multipleChoiceExercises) {
                if (ex.getCorrectAnswer() != null && !ex.getCorrectAnswer().isEmpty()) {
                    if (!ex.getCorrectAnswer().equals(correctExercise.getCorrectAnswer())) {
                        uniqueAnswers.add(ex.getCorrectAnswer());
                    }
                }
            }
            
            options.add(correctExercise.getCorrectAnswer());
            
            List<String> otherAnswers = new ArrayList<>(uniqueAnswers);
            Collections.shuffle(otherAnswers);
            
            for (int i = 0; i < 3 && i < otherAnswers.size(); i++) {
                options.add(otherAnswers.get(i));
            }
            
            while (options.size() < 4) options.add("???");
            Collections.shuffle(options);
            
        } catch (Exception e) {
            options.clear();
            options.add(correctExercise.getCorrectAnswer());
            options.add("???");
            options.add("???");
            options.add("???");
        }
        
        return options;
    }
    
    @FXML
    private void checkAnswer(ActionEvent event) {
        if (isMatchingMode) return;
        
        try {
            Button clicked = (Button) event.getSource();
            String answer = clicked.getText();
            boolean isCorrect = answer.equals(currentExercise.getCorrectAnswer());
            
            if (isCorrect) {
                correctAttempts++;
                String feedback = "✅ Правильно!";
                if (currentExercise.getExplanation() != null && !currentExercise.getExplanation().isEmpty()) {
                    feedback += "\n📖 " + currentExercise.getExplanation();
                }
                feedbackLabel.setText(feedback);
                feedbackLabel.setStyle("-fx-text-fill: #2c7a4c; -fx-font-weight: bold; -fx-font-size: 16px;");
                highlightCorrectAnswer(clicked);
            } else {
                String feedback = "❌ Неправильно.\nПравильный ответ: " + currentExercise.getCorrectAnswer();
                if (currentExercise.getExplanation() != null && !currentExercise.getExplanation().isEmpty()) {
                    feedback += "\n📖 " + currentExercise.getExplanation();
                }
                feedbackLabel.setText(feedback);
                feedbackLabel.setStyle("-fx-text-fill: #d45d79; -fx-font-weight: bold; -fx-font-size: 16px;");
                highlightWrongAnswer(clicked, findCorrectButton());
            }
            
            totalAttempts++;
            answeredExercises.add(currentExercise);
            setButtonsDisable(true);
            if (nextButton != null) nextButton.setDisable(false);
            updateStats();
            
        } catch (Exception e) {
            System.err.println("Error checking answer: " + e.getMessage());
        }
    }
    
    private Button findCorrectButton() {
        if (option1 != null && option1.getText().equals(currentExercise.getCorrectAnswer())) return option1;
        if (option2 != null && option2.getText().equals(currentExercise.getCorrectAnswer())) return option2;
        if (option3 != null && option3.getText().equals(currentExercise.getCorrectAnswer())) return option3;
        if (option4 != null && option4.getText().equals(currentExercise.getCorrectAnswer())) return option4;
        return null;
    }
    
    private void highlightCorrectAnswer(Button correctButton) {
        resetButtonStyles();
        if (correctButton != null) {
            correctButton.setStyle("-fx-background-color: #7cb87a; -fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 15 25; -fx-background-radius: 25; -fx-effect: dropshadow(gaussian, rgba(124, 184, 122, 0.4), 10, 0, 0, 2);");
        }
    }
    
    private void highlightWrongAnswer(Button wrongButton, Button correctButton) {
        resetButtonStyles();
        if (wrongButton != null) {
            wrongButton.setStyle("-fx-background-color: #e8a898; -fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 15 25; -fx-background-radius: 25; -fx-effect: dropshadow(gaussian, rgba(232, 168, 152, 0.4), 10, 0, 0, 2);");
        }
        if (correctButton != null) {
            correctButton.setStyle("-fx-background-color: #7cb87a; -fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 15 25; -fx-background-radius: 25; -fx-effect: dropshadow(gaussian, rgba(124, 184, 122, 0.4), 10, 0, 0, 2);");
        }
    }
    
    private void resetButtonStyles() {
        String baseStyle = "-fx-background-color: #9b87c9; -fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 15 25; -fx-background-radius: 25; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 2);";
        if (option1 != null) option1.setStyle(baseStyle);
        if (option2 != null) option2.setStyle(baseStyle);
        if (option3 != null) option3.setStyle(baseStyle);
        if (option4 != null) option4.setStyle(baseStyle);
    }
    
    private void setButtonsDisable(boolean disable) {
        if (option1 != null) option1.setDisable(disable);
        if (option2 != null) option2.setDisable(disable);
        if (option3 != null) option3.setDisable(disable);
        if (option4 != null) option4.setDisable(disable);
    }
    
    private void hideAllControls() {
        if (multipleChoiceGrid != null) multipleChoiceGrid.setVisible(false);
        if (matchingContainer != null) matchingContainer.setVisible(false);
        if (nextButton != null) nextButton.setDisable(true);
    }
    
    private void updateStats() {
        if (statsLabel != null) {
            if (totalAttempts > 0) {
                int percent = (correctAttempts * 100) / totalAttempts;
                statsLabel.setText(String.format("📊 Статистика: %d/%d правильных (%d%%)", correctAttempts, totalAttempts, percent));
            } else {
                statsLabel.setText("📊 Статистика: 0/0 (0%)");
            }
        }
    }
    
    private void updateProgress() {
        if (progressLabel != null && currentExercises != null) {
            progressLabel.setText(String.format("📈 Прогресс: %d из %d упражнений", answeredExercises.size(), currentExercises.size()));
        }
    }
}