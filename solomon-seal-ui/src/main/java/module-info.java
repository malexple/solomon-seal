module ru.mcs.solomonseal.ui {
    requires ru.mcs.solomonseal.spi;
    requires ru.mcs.solomonseal.core;
    requires ru.mcs.solomonseal.ui.spi;
    requires javafx.controls;
    requires javafx.web;
    requires com.fasterxml.jackson.databind;

    exports ru.mcs.solomonseal.ui.javafx;
    exports ru.mcs.solomonseal.ui.javafx.ui;
}
