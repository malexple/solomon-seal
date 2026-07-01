package ru.mcs.solomonseal.app.javafx.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import ru.mcs.solomonseal.core.session.Session;
import ru.mcs.solomonseal.core.session.SessionManager;

import java.util.List;
import java.util.function.Consumer;

/**
 * Строка поиска по сессиям.
 * Фильтрует по заголовку + тексту сообщений.
 */
public class SessionSearchBar extends HBox {

    private final TextField        searchField = new TextField();
    private final SessionManager   sessionManager;
    private final Consumer<List<Session>> onResults;

    public SessionSearchBar(SessionManager sessionManager, Consumer<List<Session>> onResults) {
        this.sessionManager = sessionManager;
        this.onResults      = onResults;

        searchField.setPromptText("🔍 Search sessions…");
        searchField.setStyle(Styles.INPUT);
        searchField.textProperty().addListener((obs, old, text) -> search(text.trim()));

        var clearBtn = new Button("✕");
        clearBtn.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #6c7086;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 0 6 0 6;"
        );
        clearBtn.setOnAction(e -> searchField.clear());
        clearBtn.visibleProperty().bind(searchField.textProperty().isNotEmpty());

        setSpacing(4);
        setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        setPadding(new Insets(4, 8, 4, 8));
        getChildren().addAll(searchField, clearBtn);
        HBox.setHgrow(searchField, Priority.ALWAYS);
    }

    private void search(String query) {
        var all = sessionManager.getAll();
        if (query.isBlank()) {
            onResults.accept(all);
            return;
        }
        var lower = query.toLowerCase();
        var filtered = all.stream()
                .filter(s -> matchesSession(s, lower))
                .toList();
        onResults.accept(filtered);
    }

    private boolean matchesSession(Session s, String lower) {
        if (s.title != null && s.title.toLowerCase().contains(lower)) return true;
        return s.history.stream()
                .anyMatch(m -> m.content != null && m.content.toLowerCase().contains(lower));
    }

    public void refresh() {
        search(searchField.getText().trim());
    }
}