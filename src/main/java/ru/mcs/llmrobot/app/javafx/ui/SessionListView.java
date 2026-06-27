package ru.mcs.llmrobot.app.javafx.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import ru.mcs.llmrobot.core.session.Session;
import ru.mcs.llmrobot.core.session.SessionManager;

public class SessionListView extends VBox {

    private final SessionManager sessionManager;
    private final ChatView chatView;
    private final ListView<Session> list = new ListView<>();

    public SessionListView(SessionManager sessionManager, ChatView chatView) {
        this.sessionManager = sessionManager;
        this.chatView = chatView;

        var newBtn = new Button("+ New Session");
        newBtn.setMaxWidth(Double.MAX_VALUE);
        newBtn.setStyle(Styles.BTN_PRIMARY);
        newBtn.setOnAction(e -> createSession());

        var label = new Label("Sessions");
        label.setStyle(Styles.LABEL_MUTED);

        list.setStyle(Styles.LIST_VIEW);
        list.setCellFactory(lv -> new SessionCell());
        list.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, session) -> { if (session != null) chatView.loadSession(session); });

        refresh();

        setStyle("-fx-background-color: #181825;");
        setPadding(new Insets(8));
        setSpacing(6);
        getChildren().addAll(label, newBtn, list);
        VBox.setVgrow(list, javafx.scene.layout.Priority.ALWAYS);
    }

    public void refresh() {
        list.getItems().setAll(sessionManager.getAll());
    }

    public void select(Session session) {
        list.getSelectionModel().select(session);
    }

    private void createSession() {
        var session = sessionManager.create("New Session");
        refresh();
        select(session);
    }

    private class SessionCell extends ListCell<Session> {
        @Override
        protected void updateItem(Session item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.title != null ? item.title : "Untitled");
                setStyle(isSelected() ? Styles.CELL_SELECTED : Styles.CELL_NORMAL);
            }
        }
    }
}
