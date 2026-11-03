package ru.mcs.solomonseal.ui.javafx;

import ru.mcs.solomonseal.core.tools.ToolRegistry;
import ru.mcs.solomonseal.spi.AppContext;
import ru.mcs.solomonseal.spi.Session;
import ru.mcs.solomonseal.ui.javafx.ui.ChatView;

public class AppContextImpl implements AppContext {

    private final Session activeSession;
    private final ToolRegistry toolRegistry;
    private final ChatView chatView;

    public AppContextImpl(Session activeSession, ToolRegistry toolRegistry, ChatView chatView) {
        this.activeSession = activeSession;
        this.toolRegistry = toolRegistry;
        this.chatView = chatView;
    }

    @Override
    public Session getActiveSession() {
        return activeSession;
    }

    @Override
    public void sendMessage(String text) {
        chatView.appendMessage(text);
    }

    @Override
    public void setStatus(String text) {
        // Status bar update
    }

    @Override
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }
}