package ru.mcs.solomonseal.app.javafx.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import ru.mcs.solomonseal.core.app.AppState;

/**
 * Панель настроек локального оркестратора.
 * Встраивается как вкладка в SettingsWindow.
 */
public class OrchestratorSettingsPane extends VBox {

    public OrchestratorSettingsPane(AppState appState) {
        setSpacing(12);
        setPadding(new Insets(16));
        setStyle("-fx-background-color: #1e1e2e;");

        var profile = appState.getConfig().orchestrator;

        var enabledCheck = new CheckBox("Enable local orchestrator LLM");
        enabledCheck.setSelected(profile.enabled);
        enabledCheck.setStyle("-fx-text-fill: #cdd6f4;");

        var urlField = new TextField(profile.baseUrl);
        urlField.setStyle(Styles.INPUT);
        urlField.setPromptText("http://localhost:11434/v1");

        var apiKeyField = new TextField(profile.apiKey);
        apiKeyField.setStyle(Styles.INPUT);
        apiKeyField.setPromptText("ollama");

        var modelField = new TextField(profile.modelId);
        modelField.setStyle(Styles.INPUT);
        modelField.setPromptText("qwen2.5-coder:7b");

        var maxStepsSpinner = new Spinner<Integer>(1, 30, profile.maxSteps);
        maxStepsSpinner.setEditable(true);
        maxStepsSpinner.setStyle(Styles.INPUT);

        var trustSelfSignedCheck = new CheckBox("Trust self-signed certificate (insecure)");
        trustSelfSignedCheck.setSelected(profile.trustSelfSignedCert);
        trustSelfSignedCheck.setStyle("-fx-text-fill: #cdd6f4;");

        var saveBtn = new Button("Save");
        saveBtn.setStyle(Styles.BTN_PRIMARY);
        saveBtn.setOnAction(e -> {
            profile.enabled             = enabledCheck.isSelected();
            profile.baseUrl             = urlField.getText().trim();
            profile.apiKey              = apiKeyField.getText().trim();
            profile.modelId             = modelField.getText().trim();
            profile.maxSteps            = maxStepsSpinner.getValue();
            profile.trustSelfSignedCert = trustSelfSignedCheck.isSelected();
            appState.saveConfig();
        });

        // Отключаем поля если оркестратор выключен
        enabledCheck.selectedProperty().addListener((obs, o, enabled) -> {
            urlField.setDisable(!enabled);
            apiKeyField.setDisable(!enabled);
            modelField.setDisable(!enabled);
            maxStepsSpinner.setDisable(!enabled);
            trustSelfSignedCheck.setDisable(!enabled);
        });
        urlField.setDisable(!profile.enabled);
        apiKeyField.setDisable(!profile.enabled);
        modelField.setDisable(!profile.enabled);
        maxStepsSpinner.setDisable(!profile.enabled);
        trustSelfSignedCheck.setDisable(!profile.enabled);

        var hint = new Label(
                "Recommended: Ollama with qwen2.5-coder:7b or deepseek-coder:6.7b\n" +
                        "If disabled — context is assembled from SQLite index without LLM."
        );
        hint.setStyle(Styles.LABEL_MUTED);
        hint.setWrapText(true);

        var sslHint = new Label(
                "Enable only for trusted internal endpoints (localhost, VPN, LAN).\n" +
                        "Disables SSL certificate chain validation."
        );
        sslHint.setStyle(Styles.LABEL_MUTED);
        sslHint.setWrapText(true);

        getChildren().addAll(
                enabledCheck,
                new Separator(),
                label("Base URL"), urlField,
                label("API Key"),  apiKeyField,
                label("Model"),    modelField,
                label("Max agent steps"), maxStepsSpinner,
                trustSelfSignedCheck,
                sslHint,
                new Separator(),
                hint,
                saveBtn
        );
    }

    private Label label(String text) {
        var l = new Label(text);
        l.setStyle(Styles.LABEL_MUTED);
        return l;
    }
}