package ru.mcs.solomonseal.ui.javafx.ui;

import javafx.application.Platform;
import javafx.scene.control.*;
import ru.mcs.solomonseal.spi.ConfirmationHandler;

import java.util.concurrent.CompletableFuture;

/**
 * Показывает Alert.CONFIRMATION на JavaFX-потоке.
 * Все вызовы из фоновых потоков безопасны — переключение на Platform.runLater внутри.
 */
public class JavaFxConfirmationHandler implements ConfirmationHandler {

    @Override
    public CompletableFuture<Boolean> confirm(
            String toolName,
            String description,
            String details
    ) {
        var future = new CompletableFuture<Boolean>();

        Platform.runLater(() -> {
            var alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirm: " + toolName);
            alert.setHeaderText(description);

            // details в scrollable textarea чтобы длинные команды не рвали диалог
            var area = new TextArea(details);
            area.setEditable(false);
            area.setWrapText(true);
            area.setPrefRowCount(Math.min(8, details.split("\n").length + 1));
            area.setStyle(
                    "-fx-control-inner-background: #1e1e2e;" +
                            "-fx-text-fill: #cdd6f4;" +
                            "-fx-font-family: monospace;" +
                            "-fx-font-size: 12px;"
            );
            alert.getDialogPane().setContent(area);
            alert.getDialogPane().setStyle("-fx-background-color: #181825;");
            alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

            alert.showAndWait().ifPresentOrElse(
                    btn -> future.complete(btn == ButtonType.YES),
                    ()  -> future.complete(false)
            );
        });

        return future;
    }
}