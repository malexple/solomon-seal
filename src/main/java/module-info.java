module ru.mcs.llmrobot {
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires javafx.controls;
    requires javafx.base;
    requires org.slf4j;
    requires org.xerial.sqlitejdbc;
    requires com.github.javaparser.core;

    exports ru.mcs.llmrobot.core.session;
    exports ru.mcs.llmrobot.core.context;
    exports ru.mcs.llmrobot.core.llm;
    exports ru.mcs.llmrobot.core.tools;
    exports ru.mcs.llmrobot.core.projects;
    exports ru.mcs.llmrobot.core.tokens;
    exports ru.mcs.llmrobot.core.config;
    exports ru.mcs.llmrobot.core.app;
    exports ru.mcs.llmrobot.app.javafx;
    exports ru.mcs.llmrobot.app.javafx.ui;
    exports ru.mcs.llmrobot.app.tui;
    exports ru.mcs.llmrobot.core.index;

    opens ru.mcs.llmrobot.core.session to com.fasterxml.jackson.databind;
    opens ru.mcs.llmrobot.core.llm     to com.fasterxml.jackson.databind;
    opens ru.mcs.llmrobot.core.context to com.fasterxml.jackson.databind;
    opens ru.mcs.llmrobot.core.config  to com.fasterxml.jackson.databind;
    opens  ru.mcs.llmrobot.core.projects to com.fasterxml.jackson.databind;
}