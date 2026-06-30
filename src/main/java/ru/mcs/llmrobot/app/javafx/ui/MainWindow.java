package ru.mcs.llmrobot.app.javafx.ui;

import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import ru.mcs.llmrobot.core.app.AppState;
import ru.mcs.llmrobot.core.context.ContextBuilder;
import ru.mcs.llmrobot.core.index.IndexManager;
import ru.mcs.llmrobot.core.llm.OpenAiCompatibleClient;
import ru.mcs.llmrobot.core.session.SessionManager;

public class MainWindow extends BorderPane {

    public MainWindow(
            AppState appState,
            SessionManager sessionManager,
            OpenAiCompatibleClient llmClient,
            ContextBuilder contextBuilder,
            IndexManager indexManager
    ) {
        var debugPanel      = new DebugPanel();
        var chatView        = new ChatView(appState, sessionManager, llmClient, contextBuilder, debugPanel);
        var sessionListView = new SessionListView(sessionManager, chatView, indexManager);
        var topBar          = new TopBar(appState, sessionManager, chatView, debugPanel);

        // когда сессия авто-переименовывается — обновляем список и проекты
        chatView.setOnSessionTitleChanged(() -> {
            sessionListView.refresh();
            sessionListView.refreshProjects();
        });

        var mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.getItems().addAll(sessionListView, chatView, debugPanel);
        mainSplit.setDividerPositions(0.20, 0.70);

        setStyle("-fx-background-color: #1e1e2e;");
        setTop(topBar);
        setCenter(mainSplit);

        // авто-выбор первой сессии или создание новой
        var sessions = sessionManager.getAll();
        if (!sessions.isEmpty()) {
            chatView.loadSession(sessions.get(0));
            sessionListView.select(sessions.get(0));
        } else {
            var session = sessionManager.create("New Session");
            chatView.loadSession(session);
            sessionListView.refresh();
            sessionListView.select(session);
        }
    }
}