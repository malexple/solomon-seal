package ru.mcs.solomonseal.spi;

public interface AppContext {
    Session getActiveSession();
    void sendMessage(String text);
    void setStatus(String text);
    ToolRegistry getToolRegistry();
}
