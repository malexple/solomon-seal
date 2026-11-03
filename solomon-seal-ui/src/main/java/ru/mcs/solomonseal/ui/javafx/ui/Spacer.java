package ru.mcs.solomonseal.ui.javafx.ui;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class Spacer {

    private Spacer() {}

    public static Region h() {
        var r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static Region v() {
        var r = new Region();
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }
}