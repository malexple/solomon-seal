package ru.mcs.solomonseal.app.javafx.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import ru.mcs.solomonseal.core.index.IndexManager;
import ru.mcs.solomonseal.core.session.Session;
import ru.mcs.solomonseal.core.session.SessionManager;

public class SessionListView extends VBox {

    private final SessionManager   sessionManager;
    private final ChatView         chatView;
    private final ProjectsPanel    projectsPanel;
    private final SessionSearchBar searchBar;
    private final ListView<Session> list = new ListView<>();

    public SessionListView(SessionManager sessionManager, ChatView chatView, IndexManager indexManager) {
        this.sessionManager = sessionManager;
        this.chatView       = chatView;
        this.projectsPanel  = new ProjectsPanel(sessionManager, indexManager);
        this.searchBar      = new SessionSearchBar(sessionManager, this::showSessions);

        var newBtn = new Button("+ New");
        newBtn.setMaxWidth(Double.MAX_VALUE);
        newBtn.setStyle(Styles.BTN_PRIMARY);
        newBtn.setOnAction(e -> createSession());

        list.setStyle(Styles.LIST_VIEW);
        list.setCellFactory(lv -> new SessionCell());
        list.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, session) -> {
                    if (session != null) {
                        chatView.loadSession(session);
                        projectsPanel.loadSession(session);
                    }
                }
        );

        refresh();

        setStyle("-fx-background-color: #181825;");
        setPadding(new Insets(8));
        setSpacing(4);
        getChildren().addAll(searchBar, newBtn, list, projectsPanel);
        VBox.setVgrow(list, Priority.ALWAYS);
    }

    public void refresh() {
        searchBar.refresh();
    }

    private void showSessions(java.util.List<Session> sessions) {
        var selected = list.getSelectionModel().getSelectedItem();
        list.getItems().setAll(sessions);
        if (selected != null && sessions.contains(selected))
            list.getSelectionModel().select(selected);
    }

    public void select(Session session) {
        list.getSelectionModel().select(session);
        projectsPanel.loadSession(session);
    }

    public void refreshProjects() {
        projectsPanel.refresh();
    }

    private void createSession() {
        var session = sessionManager.create("New Session");
        refresh();
        select(session);
        chatView.loadSession(session);
    }

    private class SessionCell extends ListCell<Session> {
        @Override
        protected void updateItem(Session item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle(Styles.CELL_NORMAL);
            } else {
                // Режим сессии в заголовке
                var mode  = item.mode != null ? " [" + item.mode.name() + "]" : "";
                var title = (item.title != null ? item.title : "Untitled") + mode;
                setText(title);

                // Количество сообщений как подсказка
                var count = item.history != null ? item.history.size() : 0;
                setTooltip(new Tooltip(count + " messages"));
                setStyle(isSelected() ? Styles.CELL_SELECTED : Styles.CELL_NORMAL);
            }
        }
    }
}