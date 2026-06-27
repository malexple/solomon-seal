package ru.mcs.llmrobot.app.javafx.ui;

import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import ru.mcs.llmrobot.core.config.ConfigManager;
import ru.mcs.llmrobot.core.context.ContextBuilder;
import ru.mcs.llmrobot.core.llm.OpenAiCompatibleClient;
import ru.mcs.llmrobot.core.session.Session;
import ru.mcs.llmrobot.core.session.SessionManager;

/**
 * Main window layout:
 * [ SessionList (left) | ChatView (center) | DebugPanel (right, toggleable) ]
 */
public class MainWindow extends BorderPane {

    private final SessionManager sessionManager;
    private final ConfigManager configManager;
    private final ChatView chatView;
    private final SessionListView sessionListView;
    private final DebugPanel debugPanel;
    private final TopBar topBar;

    public MainWindow(
            ConfigManager configManager,
            SessionManager sessionManager,
            OpenAiCompatibleClient llmClient,
            ContextBuilder contextBuilder
    ) {
        this.sessionManager = sessionManager;
        this.configManager = configManager;

        debugPanel = new DebugPanel();
        chatView = new ChatView(sessionManager, llmClient, contextBuilder, debugPanel);
        sessionListView = new SessionListView(sessionManager, chatView);
        topBar = new TopBar(configManager, chatView, debugPanel);

        var mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.getItems().addAll(sessionListView, chatView, debugPanel);
        mainSplit.setDividerPositions(0.20, 0.70);

        setStyle("-fx-background-color: #1e1e2e;");
        setTop(topBar);
        setCenter(mainSplit);

        // Auto-select or create first session
        var sessions = sessionManager.getAll();
        if (!sessions.isEmpty()) {
            chatView.loadSession(sessions.get(0));
            sessionListView.select(sessions.get(0));
        } else {
            var session = sessionManager.create("New Session");
            chatView.loadSession(session);
            sessionListView.refresh();
        }
    }
}
