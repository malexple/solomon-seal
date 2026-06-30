package ru.mcs.llmrobot.app.javafx.ui;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import ru.mcs.llmrobot.core.tools.ConfirmationHandler;

import java.util.concurrent.CompletableFuture;

/**
 * Показывает диалог подтверждения в JavaFX потоке.
 */
public class JavaFxConfirmationHandler implements ConfirmationHandler {

    @Override
    public CompletableFuture<Boolean> confirm(String toolName, String description, String details) {
        var future = new CompletableFuture<Boolean>();

        Platform.runLater(() -> {
            var dialog = new Dialog<ButtonType>();
            dialog.setTitle("Confirm: " + toolName);
            dialog.setHeaderText(description);

            var detailArea = new TextArea(details);
            detailArea.setEditable(false);
            detailArea.setWrapText(true);
            detailArea.setPrefRowCount(6);
            detailArea.setStyle(Styles.CODE_AREA);

            var content = new VBox(8,
                    new Label("The agent wants to execute the following action:"),
                    detailArea
            );
            content.setStyle("-fx-padding: 8;");
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().setStyle("-fx-background-color: #1e1e2e;");

            var allowBtn = new ButtonType("Allow", ButtonBar.ButtonData.OK_DONE);
            var denyBtn  = new ButtonType("Deny",  ButtonBar.ButtonData.CANCEL_CLOSE);
            dialog.getDialogPane().getButtonTypes().addAll(allowBtn, denyBtn);

            // Стили кнопок
            var node = dialog.getDialogPane().lookupButton(allowBtn);
            node.setStyle(Styles.BTN_PRIMARY);
            var denyNode = dialog.getDialogPane().lookupButton(denyBtn);
            denyNode.setStyle(Styles.BTN_GHOST);

            dialog.showAndWait().ifPresentOrElse(
                    result -> future.complete(result == allowBtn),
                    ()     -> future.complete(false)
            );
        });

        return future;
    }
}