package ru.mcs.solomonseal.app.javafx.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextFlow;
import ru.mcs.solomonseal.core.agent.OrchestratorService;
import ru.mcs.solomonseal.core.app.AppState;
import ru.mcs.solomonseal.core.config.LlmProfile;
import ru.mcs.solomonseal.core.context.ContextBuilder;
import ru.mcs.solomonseal.core.index.IndexManager;
import ru.mcs.solomonseal.core.llm.OpenAiCompatibleClient;
import ru.mcs.solomonseal.core.llm.RuntimeProfile;
import ru.mcs.solomonseal.core.session.*;
import ru.mcs.solomonseal.core.tools.ConfirmationHandler;
import ru.mcs.solomonseal.core.tools.ExecutionContext;
import ru.mcs.solomonseal.core.tools.ToolRegistry;

public class ChatView extends BorderPane {

    private final AppState             appState;
    private final SessionManager       sessionManager;
    private final OpenAiCompatibleClient llmClient;
    private final ContextBuilder       contextBuilder;
    private final DebugPanel           debugPanel;
    private final ToolRegistry         toolRegistry;
    private final IndexManager         indexManager;
    private final ConfirmationHandler  confirmationHandler;
    private final OrchestratorService orchestratorService;

    private Session              currentSession;
    private VBox                 messageList;
    private ScrollPane           scrollPane;
    private TextArea             composer;
    private Button               sendBtn;
    private Label                modeLabel;
    private Label                statusLabel;
    private Label                tokenLabel;
    private javafx.scene.text.Text streamingText;
    private Runnable             onSessionTitleChanged;

    public ChatView(
            AppState appState,
            SessionManager sessionManager,
            OpenAiCompatibleClient llmClient,
            ContextBuilder contextBuilder,
            DebugPanel debugPanel,
            ToolRegistry toolRegistry,
            IndexManager indexManager,
            ConfirmationHandler confirmationHandler
    ) {
        this.appState             = appState;
        this.sessionManager       = sessionManager;
        this.llmClient            = llmClient;
        this.contextBuilder       = contextBuilder;
        this.debugPanel           = debugPanel;
        this.toolRegistry         = toolRegistry;
        this.indexManager         = indexManager;
        this.confirmationHandler  = confirmationHandler;
        this.orchestratorService = new OrchestratorService(
                appState.getConfig(), toolRegistry, indexManager
        );
        buildUI();
    }

    public void setOnSessionTitleChanged(Runnable callback) {
        this.onSessionTitleChanged = callback;
    }



    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        messageList = new VBox(8);
        messageList.setPadding(new Insets(12));
        messageList.setStyle("-fx-background-color: #1e1e2e;");

        scrollPane = new ScrollPane(messageList);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #1e1e2e; -fx-background: #1e1e2e;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        modeLabel   = new Label("ASK");
        modeLabel.setStyle(Styles.BADGE_BLUE);
        tokenLabel  = new Label("0 tokens");
        tokenLabel.setStyle(Styles.LABEL_MUTED);
        statusLabel = new Label("");
        statusLabel.setStyle(Styles.LABEL_MUTED);

        var askBtn   = modeButton("ASK");
        var planBtn  = modeButton("PLAN");
        var buildBtn = modeButton("BUILD");
        askBtn.setOnAction(e   -> setMode(SessionMode.ASK));
        planBtn.setOnAction(e  -> setMode(SessionMode.PLAN));
        buildBtn.setOnAction(e -> setMode(SessionMode.BUILD));

        var modeSwitcher = new HBox(4, askBtn, planBtn, buildBtn);
        modeSwitcher.setAlignment(Pos.CENTER_LEFT);

        var modeBar = new HBox(12, modeLabel, modeSwitcher, Spacer.h(), tokenLabel, statusLabel);
        modeBar.setAlignment(Pos.CENTER_LEFT);
        modeBar.setPadding(new Insets(6, 12, 6, 12));
        modeBar.setStyle("-fx-background-color: #181825; -fx-border-color: #313244; -fx-border-width: 0 0 1 0;");

        composer = new TextArea();
        composer.setPromptText("Type your message… (Ctrl+Enter to send)");
        composer.setWrapText(true);
        composer.setPrefRowCount(3);
        composer.setMaxHeight(120);
        composer.setStyle(Styles.TEXTAREA);
        composer.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.ENTER)
                sendMessage();
        });

        sendBtn = new Button("Send");
        sendBtn.setStyle(Styles.BTN_PRIMARY);
        sendBtn.setDefaultButton(true);
        sendBtn.setOnAction(e -> sendMessage());

        var composerBar = new HBox(8, composer, sendBtn);
        composerBar.setPadding(new Insets(8, 12, 8, 12));
        composerBar.setAlignment(Pos.BOTTOM_RIGHT);
        composerBar.setStyle("-fx-background-color: #181825; -fx-border-color: #313244; -fx-border-width: 1 0 0 0;");
        HBox.setHgrow(composer, Priority.ALWAYS);

        setTop(modeBar);
        setCenter(scrollPane);
        setBottom(composerBar);
        setStyle("-fx-background-color: #1e1e2e;");
    }

    // ── Session ───────────────────────────────────────────────────────────────

    public void loadSession(Session session) {
        this.currentSession = session;

        if (session.profileId == null || session.profileId.isBlank()) {
            var p = appState.getActiveProfile();
            if (p != null) {
                session.profileId = p.id;
                sessionManager.save(session);
            }
        }

        messageList.getChildren().clear();
        for (var msg : session.history) {
            appendBubble(msg.role, msg.content);
        }
        scrollToBottom();
        if (session.mode != null) modeLabel.setText(session.mode.name());
    }

    public Session getCurrentSession() {
        return currentSession;
    }

    /** Создаёт ExecutionContext для текущей сессии */
    public ExecutionContext buildExecutionContext() {
        return new ExecutionContext(currentSession, indexManager, confirmationHandler);
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    private void sendMessage() {
        if (currentSession == null) return;
        var text = composer.getText().trim();
        if (text.isBlank()) return;

        composer.clear();
        sendBtn.setDisable(true);
        statusLabel.setText("Thinking…");

        LlmProfile profile = appState.getActiveProfile();
        if (profile == null) {
            statusLabel.setText("No profile selected");
            sendBtn.setDisable(false);
            return;
        }

        currentSession.addMessage(Message.user(text));
        appendBubble(Role.USER, text);
        sessionManager.save(currentSession);
        maybeAutoTitle(text);

        // ── PLAN / BUILD → AgentLoop ──────────────────────────────────────
        if (currentSession.mode == SessionMode.PLAN || currentSession.mode == SessionMode.BUILD) {
            var agentBubble = new javafx.scene.text.Text("Running agent…");
            agentBubble.setStyle("-fx-fill: #89b4fa;");
            appendBubbleNode(Role.ASSISTANT, agentBubble);

            orchestratorService.run(
                    currentSession, text, confirmationHandler,
                    step -> Platform.runLater(() -> agentBubble.setText(
                            agentBubble.getText() + "\n" + step))
            ).thenAccept(result -> Platform.runLater(() -> {
                if (result.success()) {
                    // Лог инструментов
                    if (!result.toolCallLog().isEmpty()) {
                        var log = String.join("\n", result.toolCallLog());
                        agentBubble.setText(log);
                    }
                    // Финальный ответ передаём в основной LLM
                    sendToMainLlm(profile, result.finalAnswer(), text);
                } else {
                    agentBubble.setText("Agent error: " + result.error());
                    sendBtn.setDisable(false);
                    statusLabel.setText("Error");
                }
            }));
            return;
        }

        // ── ASK → прямой запрос к LLM ────────────────────────────────────
        sendToMainLlm(profile, null, text);
    }

    /**
     * Отправляет запрос к основной LLM.
     * agentContext — дополнительный контекст от оркестратора (или null).
     */
    private void sendToMainLlm(LlmProfile profile, String agentContext, String userText) {
        var apiKey  = appState.getApiKey(profile.id);
        var runtime = RuntimeProfile.of(profile, apiKey);

        var result = contextBuilder.build(currentSession, runtime, userText, agentContext);
        debugPanel.show(result.debug());
        tokenLabel.setText(result.debug().estimatedTokens() + " tokens");

        var sb = new StringBuilder();
        streamingText = new javafx.scene.text.Text();
        streamingText.setStyle("-fx-fill: #cdd6f4;");
        appendBubbleNode(Role.ASSISTANT, streamingText);

        llmClient.stream(
                runtime, result.request(),
                token -> Platform.runLater(() -> {
                    sb.append(token);
                    streamingText.setText(sb.toString());
                    scrollToBottom();
                }),
                () -> Platform.runLater(() -> {
                    var mainAnswer = sb.toString();
                    currentSession.addMessage(Message.assistant(mainAnswer));
                    sessionManager.save(currentSession);

                    // ── PLAN/BUILD: запускаем второй AgentLoop чтобы применить код ──
                    if (currentSession.mode == SessionMode.PLAN
                            || currentSession.mode == SessionMode.BUILD) {
                        applyWithAgent(mainAnswer, userText);
                    } else {
                        sendBtn.setDisable(false);
                        statusLabel.setText("");
                    }
                }),
                err -> Platform.runLater(() -> {
                    streamingText.setText("Error: " + err.getMessage());
                    sendBtn.setDisable(false);
                    statusLabel.setText("Error");
                })
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void maybeAutoTitle(String userInput) {
        if (currentSession == null) return;
        if (!"New Session".equals(currentSession.title)) return;

        var title = userInput.length() > 45
                ? userInput.substring(0, 45).trim() + "…"
                : userInput.trim();
        title = title.replace('\n', ' ').replace('\r', ' ');
        currentSession.title = title;
        sessionManager.save(currentSession);

        if (onSessionTitleChanged != null)
            Platform.runLater(onSessionTitleChanged);
    }

    private void appendBubble(Role role, String content) {
        var text = new javafx.scene.text.Text(content != null ? content : "");
        text.setStyle("-fx-fill: #cdd6f4;");
        appendBubbleNode(role, text);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private void setMode(SessionMode mode) {
        if (currentSession != null) {
            currentSession.mode = mode;
            modeLabel.setText(mode.name());
            sessionManager.save(currentSession);
        }
    }

    private Button modeButton(String label) {
        var btn = new Button(label);
        btn.setStyle(Styles.BTN_GHOST);
        return btn;
    }

    private void appendBubbleNode(Role role, javafx.scene.Node contentNode) {
        appendBubbleNode(role, contentNode, null);
    }

    private void appendBubbleNode(Role role, javafx.scene.Node contentNode, String rawContent) {
        var label = new Label(role == Role.USER ? "You" : "Assistant");
        label.setStyle(role == Role.USER ? Styles.LABEL_USER : Styles.LABEL_ASSISTANT);

        var textFlow = new TextFlow(contentNode);
        textFlow.setMaxWidth(700);
        textFlow.setStyle(role == Role.USER ? Styles.BUBBLE_USER : Styles.BUBBLE_ASSISTANT);

        // ── Copy button ──────────────────────────────────────────────────
        var copyBtn = new Button("⧉");
        copyBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #6c7086;" +
                        "-fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0 4 0 4;"
        );
        copyBtn.setTooltip(new Tooltip("Copy message"));
        copyBtn.setOnAction(e -> copyToClipboard(
                rawContent != null ? rawContent : extractTextFrom(contentNode)
        ));
        copyBtn.setOnMouseEntered(e -> copyBtn.setStyle(
                copyBtn.getStyle().replace("#6c7086", "#89b4fa")
        ));
        copyBtn.setOnMouseExited(e -> copyBtn.setStyle(
                copyBtn.getStyle().replace("#89b4fa", "#6c7086")
        ));

        var header = new HBox(6, label, Spacer.h(), copyBtn);
        header.setAlignment(role == Role.USER ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        // ── Context menu (правый клик → Copy) ───────────────────────────
        var contextMenu = new ContextMenu();
        var copyItem = new MenuItem("Copy");
        copyItem.setOnAction(e -> copyToClipboard(
                rawContent != null ? rawContent : extractTextFrom(contentNode)
        ));
        contextMenu.getItems().add(copyItem);
        textFlow.setOnContextMenuRequested(e ->
                contextMenu.show(textFlow, e.getScreenX(), e.getScreenY())
        );

        var box = new VBox(2, header, textFlow);
        box.setAlignment(role == Role.USER ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        Platform.runLater(() -> {
            messageList.getChildren().add(box);
            scrollToBottom();
        });
    }

    private void copyToClipboard(String text) {
        var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        var content = new javafx.scene.input.ClipboardContent();
        content.putString(text != null ? text : "");
        clipboard.setContent(content);
        statusLabel.setText("Copied to clipboard");
    }

    private String extractTextFrom(javafx.scene.Node node) {
        if (node instanceof javafx.scene.text.Text t) return t.getText();
        return "";
    }

    /**
     * Второй проход AgentLoop — маленькая модель применяет код
     * который сгенерировала основная модель.
     */
    private void applyWithAgent(String mainModelAnswer, String originalUserText) {
        statusLabel.setText("Applying changes…");

        // Задание для маленькой модели: применить то что сказала основная
        var applyPrompt = """
            The main AI has analyzed the codebase and provided the following solution:
            
            %s
            
            Your task: apply these changes to the actual files using edit_file or write_file tools.
            - Read the relevant files first if needed
            - Apply exactly what is described above
            - After applying, confirm what was changed
            """.formatted(mainModelAnswer);

        var applyBubble = new javafx.scene.text.Text("Applying changes…");
        applyBubble.setStyle("-fx-fill: #a6e3a1;");  // зелёный — отличается от основного ответа
        appendBubbleNode(Role.ASSISTANT, applyBubble);

        orchestratorService.run(
                currentSession,
                applyPrompt,
                confirmationHandler,
                step -> Platform.runLater(() ->
                        applyBubble.setText(applyBubble.getText() + "\n" + step))
        ).thenAccept(applyResult -> Platform.runLater(() -> {
            if (applyResult.success()) {
                var summary = applyResult.finalAnswer();
                if (summary != null && !summary.isBlank()) {
                    applyBubble.setText(applyBubble.getText() + "\n\n✓ " + summary);
                } else {
                    applyBubble.setText(applyBubble.getText() + "\n\n✓ Done");
                }
                currentSession.addMessage(Message.assistant(
                        "Applied: " + (summary != null ? summary : "changes applied")));
                sessionManager.save(currentSession);
            } else {
                applyBubble.setText(applyBubble.getText() + "\n\n✗ " + applyResult.error());
            }
            sendBtn.setDisable(false);
            statusLabel.setText("");
        }));
    }
}