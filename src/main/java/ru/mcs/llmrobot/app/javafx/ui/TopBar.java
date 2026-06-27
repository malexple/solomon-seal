package ru.mcs.llmrobot.app.javafx.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import ru.mcs.llmrobot.core.config.ConfigManager;
import ru.mcs.llmrobot.core.llm.ModelConfig;

public class TopBar extends HBox {

    public TopBar(ConfigManager configManager, ChatView chatView, DebugPanel debugPanel) {
        setStyle("-fx-background-color: #11111b; -fx-border-color: #313244; -fx-border-width: 0 0 1 0;");
        setPadding(new Insets(8, 12, 8, 12));
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);

        var title = new Label("🤖 LLM Robot");
        title.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 14px; -fx-font-weight: bold;");

        // Model/endpoint quick config
        var baseUrlField = new TextField(configManager.get().defaultModel.baseUrl);
        baseUrlField.setPromptText("Base URL");
        baseUrlField.setPrefWidth(220);
        baseUrlField.setStyle(Styles.INPUT);

        var apiKeyField = new PasswordField();
        apiKeyField.setText(configManager.get().defaultModel.apiKey);
        apiKeyField.setPromptText("API Key");
        apiKeyField.setPrefWidth(140);
        apiKeyField.setStyle(Styles.INPUT);

        var modelField = new TextField(configManager.get().defaultModel.modelId);
        modelField.setPromptText("Model ID");
        modelField.setPrefWidth(200);
        modelField.setStyle(Styles.INPUT);

        var applyBtn = new Button("Apply");
        applyBtn.setStyle(Styles.BTN_GHOST);
        applyBtn.setOnAction(e -> {
            var session = chatView.getCurrentSession();
            if (session != null) {
                session.modelConfig.baseUrl = baseUrlField.getText().trim();
                session.modelConfig.apiKey = apiKeyField.getText().trim();
                session.modelConfig.modelId = modelField.getText().trim();
            }
            configManager.get().defaultModel.baseUrl = baseUrlField.getText().trim();
            configManager.get().defaultModel.apiKey = apiKeyField.getText().trim();
            configManager.get().defaultModel.modelId = modelField.getText().trim();
            configManager.save();
        });

        getChildren().addAll(
            title, Spacer.h(),
            new Label("URL:"), baseUrlField,
            new Label("Key:"), apiKeyField,
            new Label("Model:"), modelField,
            applyBtn
        );
        getChildren().filtered(n -> n instanceof Label).forEach(
                n -> ((Label) n).setStyle(Styles.LABEL_MUTED));
    }
}
