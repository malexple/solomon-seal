package ru.mcs.solomonseal.ui.spi;

public interface ModeSwitchExtension {
    String modeName();
    void onModeEntered();
    void onModeExited();
}
