package ru.mcs.solomonseal.ui.spi;

public interface ToolbarActionExtension {
    String label();
    String icon(); // optional CSS class or icon path
    Runnable action();
}
