package ru.mcs.solomonseal.ui.javafx.ui;

import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import ru.mcs.solomonseal.core.app.AppState;
import ru.mcs.solomonseal.core.context.ContextBuilder;
import ru.mcs.solomonseal.core.index.IndexManager;
import ru.mcs.solomonseal.core.llm.OpenAiCompatibleClient;
import ru.mcs.solomonseal.core.session.SessionManager;
import ru.mcs.solomonseal.core.tools.ToolRegistry;

public class MainWindow extends BorderPane {

    public MainWindow(
            AppState appState,
            SessionManager sessionManager,
            OpenAiCompatibleClient llmClient,
            ContextBuilder contextBuilder
    ) {
        var debugPanel          = new DebugPanel();
        var confirmationHandler = new JavaFxConfirmationHandler();
        var indexManager        = new IndexManager();
        var toolRegistry        = new ToolRegistry();

        var chatView = new ChatView(
                appState, sessionManager, llmClient, contextBuilder,
                debugPanel, toolRegistry, indexManager, confirmationHandler
        );

        var sessionListView = new SessionListView(sessionManager, chatView, indexManager);
        var topBar          = new TopBar(appState, sessionManager, chatView, debugPanel);

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