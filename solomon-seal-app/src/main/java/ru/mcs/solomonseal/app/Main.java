package ru.mcs.solomonseal.app;

import javafx.application.Application;
import javafx.stage.Stage;
import ru.mcs.solomonseal.core.plugin.PluginRuntime;
import ru.mcs.solomonseal.ui.javafx.LlmRobotApp;

import java.nio.file.Path;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load plugins from plugins/ directory
        PluginRuntime.loadPlugins(Path.of("plugins"));

        // Launch JavaFX app
        new LlmRobotApp().start(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
