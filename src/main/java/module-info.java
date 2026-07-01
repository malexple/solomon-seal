module ru.mcs.solomonseal {
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires javafx.controls;
    requires javafx.base;
    requires org.slf4j;
    requires org.xerial.sqlitejdbc;
    requires com.github.javaparser.core;

    exports ru.mcs.solomonseal.core.session;
    exports ru.mcs.solomonseal.core.context;
    exports ru.mcs.solomonseal.core.llm;
    exports ru.mcs.solomonseal.core.tools;
    exports ru.mcs.solomonseal.core.projects;
    exports ru.mcs.solomonseal.core.tokens;
    exports ru.mcs.solomonseal.core.config;
    exports ru.mcs.solomonseal.core.app;
    exports ru.mcs.solomonseal.app.javafx;
    exports ru.mcs.solomonseal.app.javafx.ui;
    exports ru.mcs.solomonseal.app.tui;
    exports ru.mcs.solomonseal.core.index;
    exports ru.mcs.solomonseal.core.agent;

    opens ru.mcs.solomonseal.core.agent to com.fasterxml.jackson.databind;
    opens ru.mcs.solomonseal.core.session to com.fasterxml.jackson.databind;
    opens ru.mcs.solomonseal.core.llm     to com.fasterxml.jackson.databind;
    opens ru.mcs.solomonseal.core.context to com.fasterxml.jackson.databind;
    opens ru.mcs.solomonseal.core.config  to com.fasterxml.jackson.databind;
    opens  ru.mcs.solomonseal.core.projects to com.fasterxml.jackson.databind;
}