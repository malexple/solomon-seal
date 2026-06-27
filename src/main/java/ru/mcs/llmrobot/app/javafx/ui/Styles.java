package ru.mcs.llmrobot.app.javafx.ui;

public final class Styles {
    private Styles() {}

    public static final String BTN_PRIMARY =
        "-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; " +
        "-fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand;";

    public static final String BTN_GHOST =
        "-fx-background-color: transparent; -fx-text-fill: #cdd6f4; " +
        "-fx-border-color: #45475a; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;";

    public static final String LABEL_MUTED =
        "-fx-text-fill: #6c7086; -fx-font-size: 11px;";

    public static final String LABEL_SMALL =
        "-fx-text-fill: #a6adc8; -fx-font-size: 11px; -fx-wrap-text: true;";

    public static final String LABEL_USER =
        "-fx-text-fill: #89b4fa; -fx-font-size: 11px; -fx-font-weight: bold;";

    public static final String LABEL_ASSISTANT =
        "-fx-text-fill: #a6e3a1; -fx-font-size: 11px; -fx-font-weight: bold;";

    public static final String BUBBLE_USER =
        "-fx-background-color: #313244; -fx-background-radius: 12 12 2 12; -fx-padding: 8 12 8 12;";

    public static final String BUBBLE_ASSISTANT =
        "-fx-background-color: #1e1e2e; -fx-border-color: #313244; " +
        "-fx-border-radius: 12 12 12 2; -fx-background-radius: 12 12 12 2; -fx-padding: 8 12 8 12;";

    public static final String TEXTAREA =
        "-fx-background-color: #313244; -fx-text-fill: #cdd6f4; " +
        "-fx-border-color: #45475a; -fx-border-radius: 6; -fx-background-radius: 6; " +
        "-fx-font-family: monospace;";

    public static final String CODE_AREA =
        "-fx-background-color: #11111b; -fx-text-fill: #a6adc8; " +
        "-fx-font-family: monospace; -fx-font-size: 11px;";

    public static final String INPUT =
        "-fx-background-color: #313244; -fx-text-fill: #cdd6f4; " +
        "-fx-border-color: #45475a; -fx-border-radius: 4; -fx-background-radius: 4;";

    public static final String LIST_VIEW =
        "-fx-background-color: #181825; -fx-border-color: transparent;";

    public static final String CELL_NORMAL =
        "-fx-text-fill: #cdd6f4; -fx-background-color: transparent; -fx-padding: 6 8;";

    public static final String CELL_SELECTED =
        "-fx-text-fill: #89b4fa; -fx-background-color: #313244; -fx-padding: 6 8;";

    public static final String BADGE_BLUE =
        "-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; " +
        "-fx-background-radius: 4; -fx-padding: 2 8; -fx-font-weight: bold; -fx-font-size: 11px;";
}
