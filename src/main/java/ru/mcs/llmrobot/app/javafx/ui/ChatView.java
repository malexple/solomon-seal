package ru.mcs.llmrobot.app.javafx.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextFlow;
import ru.mcs.llmrobot.core.app.AppState;
import ru.mcs.llmrobot.core.config.LlmProfile;
import ru.mcs.llmrobot.core.context.ContextBuilder;
import ru.mcs.llmrobot.core.llm.OpenAiCompatibleClient;
import ru.mcs.llmrobot.core.llm.RuntimeProfile;
import ru.mcs.llmrobot.core.session.*;

public class ChatView extends BorderPane {

    private final AppState appState;
    private final SessionManager sessionManager;
    private final OpenAiCompatibleClient llmClient;
    private final ContextBuilder contextBuilder;
    private final DebugPanel debugPanel;

    private Session currentSession;
    private VBox messageList;
    private ScrollPane scrollPane;
    private TextArea composer;
    private Button sendBtn;
    private Label modeLabel;
    private Label statusLabel;
    private Label tokenLabel;
    private javafx.scene.text.Text streamingText;
    private Runnable onSessionTitleChanged;

    public ChatView(
            AppState appState,
            SessionManager sessionManager,
            OpenAiCompatibleClient llmClient,
            ContextBuilder contextBuilder,
            DebugPanel debugPanel
    ) {
        this.appState       = appState;
        this.sessionManager = sessionManager;
        this.llmClient      = llmClient;
        this.contextBuilder = contextBuilder;
        this.debugPanel     = debugPanel;
        buildUI();
    }

    public void setOnSessionTitleChanged(Runnable callback) {
        this.onSessionTitleChanged = callback;
    }

    private void buildUI() {
        messageList = new VBox(8);
        messageList.setPadding(new Insets(12));
        messageList.setStyle("-fx-background-color: #1e1e2e;");

        scrollPane = new ScrollPane(messageList);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #1e1e2e; -fx-background: #1e1e2e;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        modeLabel  = new Label("ASK");
        modeLabel.setStyle(Styles.BADGE_BLUE);
        tokenLabel = new Label("0 tokens");
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
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                sendMessage();
            }
        });
        composer.skinProperty().addListener((obs, o, n) -> {
            if (n != null) {
                var content = composer.lookup(".content");
                if (content != null) {
                    content.setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #181825;");
                }
            }
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

    public void loadSession(Session session) {
        this.currentSession = session;

        // если сессия без профиля — привязываем активный
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
        if (session.mode != null) {
            modeLabel.setText(session.mode.name());
        }
    }

    private void sendMessage() {
        var text = composer.getText().trim();
        if (text.isBlank()) return;
        composer.clear();
        sendBtn.setDisable(true);

        // ① Получаем профиль
        LlmProfile profile = appState.getActiveProfile();
        if (profile == null) {
            statusLabel.setText("No profile selected");
            sendBtn.setDisable(false);
            return;
        }

        // ② Достаём apiKey из SecretsManager и создаём RuntimeProfile
        String apiKey = appState.getApiKey(profile.id);
        RuntimeProfile runtime = RuntimeProfile.of(profile, apiKey);  // ← вот это добавь

        // ③ Строим запрос ДО добавления в историю
        var result = contextBuilder.build(currentSession, runtime, text);
        debugPanel.show(result.debug());
        tokenLabel.setText(result.debug().estimatedTokens() + " tokens");

        // ④ Теперь добавляем в историю
        var userMsg = Message.user(text);
        currentSession.addMessage(userMsg);
        appendBubble(Role.USER, text);
        sessionManager.save(currentSession);

        maybeAutoTitle(text);

        // ⑤ Стриминг — передаём runtime вместо profile
        var sb = new StringBuilder();
        streamingText = new javafx.scene.text.Text();
        streamingText.setStyle("-fx-fill: #cdd6f4;");
        appendBubbleNode(Role.ASSISTANT, streamingText);

        llmClient.stream(
                runtime,           // ← RuntimeProfile вместо LlmProfile
                result.request(),
                token -> Platform.runLater(() -> {
                    sb.append(token);
                    streamingText.setText(sb.toString());
                    scrollToBottom();
                }),
                () -> Platform.runLater(() -> {
                    currentSession.addMessage(Message.assistant(sb.toString()));
                    sessionManager.save(currentSession);
                    sendBtn.setDisable(false);
                    statusLabel.setText("");
                }),
                err -> Platform.runLater(() -> {
                    streamingText.setText("Error: " + err.getMessage());
                    sendBtn.setDisable(false);
                    statusLabel.setText("Error");
                })
        );
    }

    private void maybeAutoTitle(String userInput) {
        if (currentSession == null) return;
        if (!"New Session".equals(currentSession.title)) return;

        var title = userInput.length() > 45
                ? userInput.substring(0, 45).trim() + "…"
                : userInput.trim();
        title = title.replace('\n', ' ').replace('\r', ' ');
        currentSession.title = title;
        sessionManager.save(currentSession);

        if (onSessionTitleChanged != null) {
            Platform.runLater(onSessionTitleChanged);
        }
    }

    private void appendBubble(Role role, String content) {
        var text = new javafx.scene.text.Text(content);
        text.setStyle("-fx-fill: #cdd6f4;");
        appendBubbleNode(role, text);
    }

    private void appendBubbleNode(Role role, javafx.scene.Node contentNode) {
        var label = new Label(role == Role.USER ? "You" : "Assistant");
        label.setStyle(role == Role.USER ? Styles.LABEL_USER : Styles.LABEL_ASSISTANT);

        var textFlow = new TextFlow(contentNode);
        textFlow.setMaxWidth(700);
        textFlow.setStyle(role == Role.USER ? Styles.BUBBLE_USER : Styles.BUBBLE_ASSISTANT);

        var box = new VBox(2, label, textFlow);
        box.setAlignment(role == Role.USER ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        Platform.runLater(() -> {
            messageList.getChildren().add(box);
            scrollToBottom();
        });
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

    public Session getCurrentSession() {
        return currentSession;
    }
}