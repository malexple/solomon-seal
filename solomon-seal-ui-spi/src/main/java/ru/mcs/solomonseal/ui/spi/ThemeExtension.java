package ru.mcs.solomonseal.ui.spi;

public interface ThemeExtension {
    String id();
    String name();
    void apply(javafx.scene.Scene scene);
}
