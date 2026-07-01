package ru.mcs.solomonseal.app.javafx.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import ru.mcs.solomonseal.core.llm.DebugPayload;

public class DebugPanel extends VBox {

    private final TextArea rawJsonArea;
    private final Label endpointLabel;
    private final Label modelLabel;
    private final Label tokensLabel;

    public DebugPanel() {
        setStyle("-fx-background-color: #181825;");
        setPadding(new Insets(8));
        setSpacing(6);
        setMinWidth(200);

        var title = new Label("Debug / Payload");
        title.setStyle(Styles.LABEL_MUTED);

        endpointLabel = new Label("—");
        endpointLabel.setStyle(Styles.LABEL_SMALL);
        endpointLabel.setWrapText(true);

        modelLabel = new Label("—");
        modelLabel.setStyle(Styles.LABEL_SMALL);

        tokensLabel = new Label("—");
        tokensLabel.setStyle(Styles.LABEL_SMALL);

        rawJsonArea = new TextArea();
        rawJsonArea.setEditable(false);
        rawJsonArea.setWrapText(false);
        rawJsonArea.setStyle(Styles.CODE_AREA);
        VBox.setVgrow(rawJsonArea, Priority.ALWAYS);

        getChildren().addAll(
            title,
            new Label("Endpoint:"), endpointLabel,
            new Label("Model:"), modelLabel,
            new Label("~Tokens:"), tokensLabel,
            new Separator(),
            new Label("Raw Request:"),
            rawJsonArea
        );

        getChildren().filtered(n -> n instanceof Label && ((Label) n).getText().endsWith(":"))
                     .forEach(n -> ((Label) n).setStyle(Styles.LABEL_MUTED));
    }

    public void show(DebugPayload payload) {
        endpointLabel.setText(payload.endpoint());
        modelLabel.setText(payload.modelId());
        tokensLabel.setText(String.valueOf(payload.estimatedTokens()));
        rawJsonArea.setText(payload.rawJsonRequest());
    }
}
