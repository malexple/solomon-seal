package ru.mcs.llmrobot.app.javafx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.mcs.llmrobot.core.config.ConfigManager;
import ru.mcs.llmrobot.core.context.ContextBuilder;
import ru.mcs.llmrobot.core.llm.OpenAiCompatibleClient;
import ru.mcs.llmrobot.core.session.SessionManager;
import ru.mcs.llmrobot.app.javafx.ui.MainWindow;

public class LlmRobotApp extends Application {

    @Override
    public void start(Stage stage) {
        var configManager = new ConfigManager();
        var sessionManager = new SessionManager();
        var llmClient = new OpenAiCompatibleClient();
        var contextBuilder = new ContextBuilder();

        var mainWindow = new MainWindow(configManager, sessionManager, llmClient, contextBuilder);

        var scene = new Scene(mainWindow, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/style.css") != null
                ? getClass().getResource("/style.css").toExternalForm() : "");

        stage.setTitle("LLM Robot");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
