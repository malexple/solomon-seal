package ru.mcs.llmrobot.app.javafx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.mcs.llmrobot.core.app.AppState;
import ru.mcs.llmrobot.core.config.ConfigManager;
import ru.mcs.llmrobot.core.context.ContextBuilder;
import ru.mcs.llmrobot.core.llm.OpenAiCompatibleClient;
import ru.mcs.llmrobot.core.session.SessionManager;
import ru.mcs.llmrobot.app.javafx.ui.MainWindow;

public class LlmRobotApp extends Application {

    @Override
    public void start(Stage stage) {
        var configManager   = new ConfigManager();
        var appState        = new AppState(configManager);
        var sessionManager  = new SessionManager();
        var llmClient       = new OpenAiCompatibleClient();
        var contextBuilder  = new ContextBuilder();

        var mainWindow = new MainWindow(appState, sessionManager, llmClient, contextBuilder);

        stage.setTitle("LLM Robot");
        stage.setScene(new Scene(mainWindow, 1200, 800));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
