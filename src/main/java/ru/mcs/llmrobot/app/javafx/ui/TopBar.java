package ru.mcs.llmrobot.app.javafx.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import ru.mcs.llmrobot.core.app.AppState;
import ru.mcs.llmrobot.core.session.SessionManager;

public class TopBar extends HBox {

    public TopBar(
            AppState appState,
            SessionManager sessionManager,
            ChatView chatView,
            DebugPanel debugPanel
    ) {
        setStyle("-fx-background-color: #11111b; -fx-border-color: #313244; -fx-border-width: 0 0 1 0;");
        setPadding(new Insets(8, 12, 8, 12));
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);

        var title = new Label("🤖 LLM Robot");
        title.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 14px; -fx-font-weight: bold;");

        var profile = appState.getActiveProfile();

        var baseUrlField = new TextField(profile != null ? profile.baseUrl : "");
        baseUrlField.setPromptText("Base URL");
        baseUrlField.setPrefWidth(220);
        baseUrlField.setStyle(Styles.INPUT);

        var apiKeyField = new PasswordField();
        apiKeyField.setText(profile != null ? profile.apiKey : "");
        apiKeyField.setPromptText("API Key");
        apiKeyField.setPrefWidth(140);
        apiKeyField.setStyle(Styles.INPUT);

        var modelField = new TextField(profile != null ? profile.modelId : "");
        modelField.setPromptText("Model ID");
        modelField.setPrefWidth(200);
        modelField.setStyle(Styles.INPUT);

        var applyBtn = new Button("Apply");
        applyBtn.setStyle(Styles.BTN_GHOST);
        applyBtn.setOnAction(e -> {
            var url   = baseUrlField.getText().trim();
            var key   = apiKeyField.getText().trim();
            var model = modelField.getText().trim();

            // обновляем активный профиль через AppState
            var active = appState.getActiveProfile();
            if (active != null) {
                active.baseUrl  = url;
                active.apiKey   = key;
                active.modelId  = model;
                appState.saveProfile(active);   // сохраняет в config.json + нотифицирует
            }

            // применяем к текущей сессии
            var session = chatView.getCurrentSession();
            if (session != null) {
                session.profileId = active != null ? active.id : null;
                sessionManager.save(session);
            }
        });

        // обновлять поля при смене профиля извне (SettingsWindow и т.д.)
        appState.onProfileChange(() -> {
            var p = appState.getActiveProfile();
            if (p != null) {
                baseUrlField.setText(p.baseUrl);
                apiKeyField.setText(p.apiKey);
                modelField.setText(p.modelId);
            }
        });

        getChildren().addAll(
                title, Spacer.h(),
                new Label("URL:"), baseUrlField,
                new Label("Key:"), apiKeyField,
                new Label("Model:"), modelField,
                applyBtn
        );
        getChildren().filtered(n -> n instanceof Label)
                .forEach(n -> ((Label) n).setStyle(Styles.LABEL_MUTED));
    }
}