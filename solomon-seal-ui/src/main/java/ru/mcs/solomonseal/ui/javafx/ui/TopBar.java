package ru.mcs.solomonseal.ui.javafx.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import ru.mcs.solomonseal.core.app.AppState;
import ru.mcs.solomonseal.core.config.LlmProfile;
import ru.mcs.solomonseal.core.session.SessionManager;

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

        var profileBox = new ComboBox<LlmProfile>();
        profileBox.setStyle(Styles.COMBO_BOX);
        profileBox.setPrefWidth(200);

        // ── отображение выбранного элемента ──────────────────────────────────
        profileBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(LlmProfile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Select profile...");
                    setStyle("-fx-text-fill: #6c7086; -fx-background-color: transparent;");
                } else {
                    setText(item.name);
                    setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: transparent;");
                }
            }
        });

        // ── отображение элементов в выпадающем списке ─────────────────────────
        profileBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(LlmProfile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.name);
                    setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: transparent;");
                }
            }
        });

        final boolean[] updating = {false};

        profileBox.getItems().setAll(appState.getProfiles());
        var active = appState.getActiveProfile();
        if (active != null) profileBox.setValue(active);

        profileBox.setOnAction(e -> {
            if (updating[0]) return;
            var selected = profileBox.getValue();
            if (selected != null) {
                appState.setActiveProfile(selected.id);
                var session = chatView.getCurrentSession();
                if (session != null) {
                    session.profileId = selected.id;
                    sessionManager.save(session);
                }
            }
        });

        appState.onProfileChange(() -> {
            updating[0] = true;
            try {
                profileBox.getItems().setAll(appState.getProfiles());
                var a = appState.getActiveProfile();
                if (a != null) profileBox.setValue(a);
            } finally {
                updating[0] = false;
            }
        });

        var settingsBtn = new Button("⚙");
        settingsBtn.setStyle(Styles.BTN_GHOST);
        settingsBtn.setTooltip(new Tooltip("LLM Profile Settings"));
        settingsBtn.setOnAction(e -> {
            new SettingsWindow(getScene().getWindow(), appState).show();
        });

        getChildren().addAll(title, Spacer.h(), profileBox, settingsBtn);
    }
}