package ru.mcs.solomonseal.app.javafx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.mcs.solomonseal.core.app.AppState;
import ru.mcs.solomonseal.core.config.ConfigManager;
import ru.mcs.solomonseal.core.config.SecretsManager;
import ru.mcs.solomonseal.core.context.ContextBuilder;
import ru.mcs.solomonseal.core.llm.OpenAiCompatibleClient;
import ru.mcs.solomonseal.core.session.SessionManager;
import ru.mcs.solomonseal.app.javafx.ui.MainWindow;

public class LlmRobotApp extends Application {

    @Override
    public void start(Stage stage) {
        var configManager  = new ConfigManager();
        var secretsManager = new SecretsManager();
        var appState       = new AppState(configManager, secretsManager);
        var sessionManager = new SessionManager();
        var llmClient      = new OpenAiCompatibleClient();
        var contextBuilder = new ContextBuilder();

        var mainWindow = new MainWindow(appState, sessionManager, llmClient, contextBuilder);
        stage.setTitle("LLM Robot");
        stage.setScene(new Scene(mainWindow, 1280, 800));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}