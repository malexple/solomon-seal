module ru.mcs.solomonseal.app {
    requires ru.mcs.solomonseal.core;
    requires ru.mcs.solomonseal.ui;
    requires javafx.controls;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    requires java.sql;
    requires java.net.http;

    exports ru.mcs.solomonseal.app;
    opens ru.mcs.solomonseal.app to javafx.graphics;
}