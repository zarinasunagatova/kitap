package com.tatar.learn.controllers;

import com.tatar.learn.models.CultureItem;
import com.tatar.learn.services.CultureService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import java.net.URL;
import java.util.*;

public class CultureController implements Initializable {
    
	@FXML private ComboBox<String> categoryCombo;
    @FXML private GridPane itemsGrid;
    @FXML private Button backButton;
    @FXML private VBox detailView;
    @FXML private Label detailIcon;
    @FXML private Label detailTitle;
    @FXML private Label detailTitleTatar;
    @FXML private TextArea detailDescription;
    @FXML private TextArea detailDescriptionTatar;
    
    private CultureService cultureService;
    private List<CultureItem> currentItems;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cultureService = CultureService.getInstance();
        currentItems = cultureService.getAllItems();
        
        setupCategoryFilter();
        displayItemsGrid();
        
        if (backButton != null) {
            backButton.setOnAction(e -> showGrid());
        }
    }
    
    private void setupCategoryFilter() {
        if (categoryCombo != null) {
            categoryCombo.getItems().addAll(
                "📌 Все категории",
                "🎉 Праздники",
                "🍽️ Кухня",
                "🎵 Музыка",
                "📚 Литература",
                "👗 Одежда",
                "🏺 Традиции"
            );
            categoryCombo.getSelectionModel().selectFirst();
            categoryCombo.setOnAction(e -> filterByCategory());
        }
    }
    
    private void filterByCategory() {
        String selected = categoryCombo.getValue();
        
        if (selected == null || selected.equals("📌 Все категории")) {
            currentItems = cultureService.getAllItems();
        } else if (selected.equals("🎉 Праздники")) {
            currentItems = cultureService.getItemsByCategory("holiday");
        } else if (selected.equals("🍽️ Кухня")) {
            currentItems = cultureService.getItemsByCategory("food");
        } else if (selected.equals("🎵 Музыка")) {
            currentItems = cultureService.getItemsByCategory("music");
        } else if (selected.equals("📚 Литература")) {
            currentItems = cultureService.getItemsByCategory("literature");
        } else if (selected.equals("👗 Одежда")) {
            currentItems = cultureService.getItemsByCategory("clothing");
        } else if (selected.equals("🏺 Традиции")) {
            currentItems = cultureService.getItemsByCategory("tradition");
        } else {
            currentItems = cultureService.getAllItems();
        }
        displayItemsGrid();
    }
    
    private void displayItemsGrid() {
        if (itemsGrid == null) return;
        
        itemsGrid.getChildren().clear();
        itemsGrid.setVisible(true);
        if (detailView != null) {
            detailView.setVisible(false);
            detailView.setManaged(false);
        }
        
        // Очищаем columnConstraints
        itemsGrid.getColumnConstraints().clear();
        
        int col = 0;
        int row = 0;
        
        for (CultureItem item : currentItems) {
            VBox card = createCard(item);
            itemsGrid.add(card, col, row);
            
            col++;
            if (col >= 2) { // 2 колонки
                col = 0;
                row++;
            }
        }
    }
    
    private VBox createCard(CultureItem item) {
        VBox card = new VBox(10);
        card.getStyleClass().add("culture-card");
        card.setMaxWidth(350);
        card.setMinWidth(280);
        // Убираем фиксацию высоты:
        // card.setMinHeight(280);
        
        Label iconLabel = new Label(item.getIcon());
        iconLabel.getStyleClass().add("culture-card-icon");
        
        Label titleLabel = new Label(item.getTitle());
        titleLabel.getStyleClass().add("culture-card-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        
        Label tatarLabel = new Label(item.getTitleTatar());
        tatarLabel.getStyleClass().add("culture-card-title-tatar");
        tatarLabel.setWrapText(true);
        tatarLabel.setMaxWidth(Double.MAX_VALUE);
        
        Label descLabel = new Label(item.getDescription());
        descLabel.getStyleClass().add("culture-card-desc");
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(Double.MAX_VALUE);
        
        // Позволяем описанию занимать всё свободное место
        VBox.setVgrow(descLabel, Priority.ALWAYS);
        
        Button readMore = new Button("Подробнее →");
        readMore.getStyleClass().add("culture-card-button");
        readMore.setOnAction(e -> showDetail(item));
        
        card.getChildren().addAll(iconLabel, titleLabel, tatarLabel, descLabel, readMore);
        
        return card;
    }
    
    private void showDetail(CultureItem item) {
        if (detailView == null) return;
        
        detailIcon.setText(item.getIcon());
        detailTitle.setText(item.getTitle());
        detailTitleTatar.setText(item.getTitleTatar());
        
        // Для TextArea
        detailDescription.setText(item.getDescription());
        detailDescription.setWrapText(true);
        detailDescription.setEditable(false);
        
        detailDescriptionTatar.setText(item.getDescriptionTatar());
        detailDescriptionTatar.setWrapText(true);
        detailDescriptionTatar.setEditable(false);
        
        itemsGrid.setVisible(false);
        detailView.setVisible(true);
        detailView.setManaged(true);
    }
    
    private void showGrid() {
        itemsGrid.setVisible(true);
        detailView.setVisible(false);
        detailView.setManaged(false);
    }
}