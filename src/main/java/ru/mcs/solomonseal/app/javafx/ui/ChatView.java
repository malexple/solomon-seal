package ru.mcs.solomonseal.app.javafx.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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
    private Text                 streamingText;
    private Runnable             onSessionTitleChanged;

    private final List<File> attachedFiles = new ArrayList<>();
    private FlowPane attachmentPane;

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
                appState.getConfig(), llmClient, toolRegistry, indexManager
        );
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

        attachmentPane = new FlowPane(4, 4);
        attachmentPane.setPadding(new Insets(4, 0, 4, 0));
        attachmentPane.setStyle("-fx-background-color: #181825;");
        attachmentPane.setVisible(false);
        attachmentPane.setManaged(false);

        Button attachBtn = new Button("📎");
        attachBtn.setStyle(Styles.BTN_GHOST);
        attachBtn.setTooltip(new Tooltip("Attach file(s)"));
        attachBtn.setOnAction(e -> attachFiles());

        sendBtn = new Button("Send");
        sendBtn.setStyle(Styles.BTN_PRIMARY);
        sendBtn.setDefaultButton(true);
        sendBtn.setOnAction(e -> sendMessage());

        VBox composerArea = new VBox(attachmentPane, composer);
        VBox.setVgrow(composer, Priority.ALWAYS);
        composerArea.setStyle("-fx-background-color: #181825;");

        HBox composerBar = new HBox(8, attachBtn, composerArea, sendBtn);
        composerBar.setPadding(new Insets(8, 12, 8, 12));
        composerBar.setAlignment(Pos.BOTTOM_RIGHT);
        composerBar.setStyle("-fx-background-color: #181825; -fx-border-color: #313244; -fx-border-width: 1 0 0 0;");
        HBox.setHgrow(composerArea, Priority.ALWAYS);

        setTop(modeBar);
        setCenter(scrollPane);
        setBottom(composerBar);
        setStyle("-fx-background-color: #1e1e2e;");
    }

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

    public ExecutionContext buildExecutionContext() {
        return new ExecutionContext(currentSession, indexManager, confirmationHandler);
    }

    private void sendMessage() {
        if (currentSession == null) return;
        String userText = composer.getText().trim();
        if (userText.isBlank() && attachedFiles.isEmpty()) return;

        StringBuilder fullMessage = new StringBuilder(userText);
        for (File file : attachedFiles) {
            try {
                String content = Files.readString(file.toPath());
                if (content.length() > 50000) {
                    content = content.substring(0, 50000) + "\n... [truncated]";
                }
                fullMessage.append("\n\n=== File: ").append(file.getName()).append(" ===\n")
                        .append(content)
                        .append("\n=== End of ").append(file.getName()).append(" ===\n");
            } catch (IOException e) {
                fullMessage.append("\n\n=== File: ").append(file.getName())
                        .append(" (ERROR: ").append(e.getMessage()).append(") ===\n");
            }
        }
        String finalText = fullMessage.toString();

        composer.clear();
        clearAttachments();

        sendBtn.setDisable(true);
        statusLabel.setText("Thinking…");

        LlmProfile profile = appState.getActiveProfile();
        if (profile == null) {
            statusLabel.setText("No profile selected");
            sendBtn.setDisable(false);
            return;
        }

        appendBubble(Role.USER, finalText);
        Platform.runLater(() -> {
            messageList.layout();
            scrollPane.layout();
            scrollToBottom();
        });
        maybeAutoTitle(finalText);

        if (currentSession.mode == SessionMode.PLAN || currentSession.mode == SessionMode.BUILD) {
            var agentBubble = new Text("Running agent…");
            agentBubble.setStyle("-fx-fill: #89b4fa;");
            appendBubbleNode(Role.ASSISTANT, agentBubble, null);

            orchestratorService.run(
                    currentSession, finalText, confirmationHandler,
                    step -> Platform.runLater(() -> agentBubble.setText(
                            agentBubble.getText() + "\n" + step))
            ).thenAccept(result -> Platform.runLater(() -> {
                if (result.success()) {
                    if (!result.toolCallLog().isEmpty()) {
                        var log = String.join("\n", result.toolCallLog());
                        agentBubble.setText(log);
                    }
                    sendToMainLlm(profile, result.finalAnswer(), finalText);
                } else {
                    agentBubble.setText("Agent error: " + result.error());
                    sendBtn.setDisable(false);
                    statusLabel.setText("Error");
                }
            }));
            return;
        }

        sendToMainLlm(profile, null, finalText);
    }

    private void sendToMainLlm(LlmProfile profile, String agentContext, String userText) {
        var apiKey  = appState.getApiKey(profile.id);
        var runtime = RuntimeProfile.of(profile, apiKey);

        var result = contextBuilder.build(currentSession, runtime, userText, agentContext);
        debugPanel.show(result.debug());
        tokenLabel.setText(result.debug().estimatedTokens() + " tokens");

        var sb = new StringBuilder();
        TextArea streamingArea = createMessageTextArea();
        appendBubbleNode(Role.ASSISTANT, streamingArea, null);

        llmClient.stream(
                runtime, result.request(),
                token -> Platform.runLater(() -> {
                    sb.append(token);
                    streamingArea.setText(sb.toString());
                    scrollToBottom();
                }),
                () -> Platform.runLater(() -> {
                    var mainAnswer = sb.toString();
                    currentSession.addMessage(Message.user(userText));
                    currentSession.addMessage(Message.assistant(mainAnswer));
                    sessionManager.save(currentSession);

                    if (currentSession.mode == SessionMode.PLAN
                            || currentSession.mode == SessionMode.BUILD) {
                        applyWithAgent(mainAnswer, userText);
                    } else {
                        sendBtn.setDisable(false);
                        statusLabel.setText("");
                    }
                }),
                err -> Platform.runLater(() -> {
                    streamingArea.setText("Error: " + err.getMessage());
                    currentSession.addMessage(Message.user(userText));
                    sessionManager.save(currentSession);
                    sendBtn.setDisable(false);
                    statusLabel.setText("Error");
                })
        );
    }

    private void attachFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select files to attach");
        List<File> selected = fileChooser.showOpenMultipleDialog(getScene().getWindow());
        if (selected != null) {
            for (File file : selected) {
                addAttachment(file);
            }
        }
    }

    private void addAttachment(File file) {
        attachedFiles.add(file);
        updateAttachmentPane();
    }

    private void clearAttachments() {
        attachedFiles.clear();
        updateAttachmentPane();
    }

    private void updateAttachmentPane() {
        attachmentPane.getChildren().clear();
        if (attachedFiles.isEmpty()) {
            attachmentPane.setVisible(false);
            attachmentPane.setManaged(false);
            return;
        }
        attachmentPane.setVisible(true);
        attachmentPane.setManaged(true);
        for (File file : attachedFiles) {
            Label fileLabel = new Label(file.getName());
            fileLabel.setStyle("-fx-text-fill: #89b4fa; -fx-background-color: #313244; -fx-padding: 2 8 2 8; -fx-border-radius: 4; -fx-background-radius: 4;");
            Button removeBtn = new Button("×");
            removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #f38ba8; -fx-cursor: hand;");
            removeBtn.setOnAction(e -> {
                attachedFiles.remove(file);
                updateAttachmentPane();
            });
            HBox item = new HBox(4, fileLabel, removeBtn);
            item.setAlignment(Pos.CENTER_LEFT);
            attachmentPane.getChildren().add(item);
        }
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

        if (onSessionTitleChanged != null)
            Platform.runLater(onSessionTitleChanged);
    }

    private TextArea createMessageTextArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        area.setBackground(Background.EMPTY);
        area.setBorder(Border.EMPTY);
        area.setStyle(
                "-fx-text-fill: #cdd6f4; " +
                        "-fx-font-size: 14px; " +
                        "-fx-control-inner-background: transparent; " +
                        "-fx-background-color: transparent; " +
                        "-fx-border-color: transparent; " +
                        "-fx-border-width: 0; " +
                        "-fx-padding: 0; " +
                        "-fx-background-insets: 0;"
        );
        area.setPrefHeight(Region.USE_COMPUTED_SIZE);
        area.setMaxWidth(700);
        return area;
    }

    private void appendBubble(Role role, String content) {
        TextArea area = createMessageTextArea();
        area.setText(content != null ? content : "");
        appendBubbleNode(role, area, content);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            scrollPane.setVvalue(1.0);
            scrollPane.layout();
        });
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

    private void appendBubbleNode(Role role, javafx.scene.Node contentNode, String rawContent) {
        var label = new Label(role == Role.USER ? "You" : "Assistant");
        label.setStyle(role == Role.USER ? Styles.LABEL_USER : Styles.LABEL_ASSISTANT);

        VBox contentBox = new VBox(contentNode);
        contentBox.setMaxWidth(700);
        contentBox.setStyle(role == Role.USER ? Styles.BUBBLE_USER : Styles.BUBBLE_ASSISTANT);

        Button copyBtn = new Button("⧉");
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

        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setOnAction(e -> copyToClipboard(
                rawContent != null ? rawContent : extractTextFrom(contentNode)
        ));
        contextMenu.getItems().add(copyItem);
        contentBox.setOnContextMenuRequested(e ->
                contextMenu.show(contentBox, e.getScreenX(), e.getScreenY())
        );

        var box = new VBox(2, header, contentBox);
        box.setAlignment(role == Role.USER ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        Platform.runLater(() -> {
            messageList.getChildren().add(box);
            messageList.layout();
            scrollPane.layout();
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
        if (node instanceof TextArea ta) return ta.getText();
        if (node instanceof Text t) return t.getText();
        return "";
    }

    private void applyWithAgent(String mainModelAnswer, String originalUserText) {
        statusLabel.setText("Applying changes…");

        var applyPrompt = """
            The main AI has analyzed the codebase and provided the following solution:
            
            %s
            
            Your task: apply these changes to the actual files using edit_file or write_file tools.
            - Read the relevant files first if needed
            - Apply exactly what is described above
            - After applying, confirm what was changed
            """.formatted(mainModelAnswer);

        TextArea applyArea = createMessageTextArea();
        applyArea.setText("Applying changes…");
        appendBubbleNode(Role.ASSISTANT, applyArea, null);

        orchestratorService.run(
                currentSession,
                applyPrompt,
                confirmationHandler,
                step -> Platform.runLater(() -> {
                    applyArea.appendText("\n" + step);
                })
        ).thenAccept(applyResult -> Platform.runLater(() -> {
            if (applyResult.success()) {
                var summary = applyResult.finalAnswer();
                if (summary != null && !summary.isBlank()) {
                    applyArea.appendText("\n\n✓ " + summary);
                } else {
                    applyArea.appendText("\n\n✓ Done");
                }
                currentSession.addMessage(Message.assistant(
                        "Applied: " + (summary != null ? summary : "changes applied")));
                sessionManager.save(currentSession);
            } else {
                applyArea.appendText("\n\n✗ " + applyResult.error());
            }
            sendBtn.setDisable(false);
            statusLabel.setText("");
        }));
    }
}