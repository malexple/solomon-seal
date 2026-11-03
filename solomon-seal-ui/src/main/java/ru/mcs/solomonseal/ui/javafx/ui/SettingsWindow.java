package ru.mcs.solomonseal.ui.javafx.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import ru.mcs.solomonseal.core.app.AppState;
import ru.mcs.solomonseal.core.config.LlmProfile;
import ru.mcs.solomonseal.core.config.ProviderType;

public class SettingsWindow extends Stage {

    private final AppState appState;

    // ── Profiles form fields ──────────────────────────────────────────────────
    private final ListView<LlmProfile> profileList  = new ListView<>();
    private final TextField            nameField    = new TextField();
    private final TextField            urlField     = new TextField();
    private final TextField            modelField   = new TextField();
    private final PasswordField        apiKeyField  = new PasswordField();
    private final ComboBox<ProviderType> typeBox    = new ComboBox<>();
    private final CheckBox             trustSelfSignedCheck = new CheckBox("Trust self-signed certificate (insecure)");
    private LlmProfile editing = null;

    public SettingsWindow(Window owner, AppState appState) {
        this.appState = appState;

        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setTitle("Settings");
        setResizable(true);
        setMinWidth(640);
        setMinHeight(460);

        var tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                tab("LLM Profiles",  buildProfilesPane()),
                tab("Orchestrator",  new OrchestratorSettingsPane(appState))
        );

        setScene(new Scene(tabs, 660, 460));
        refreshList();

        profileList.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, selected) -> loadIntoForm(selected)
        );
    }

    private Tab tab(String title, javafx.scene.Node content) {
        var t = new Tab(title, content);
        t.setStyle("-fx-background-color: #181825; -fx-text-fill: #cdd6f4;");
        return t;
    }

    // ── Profiles pane ────────────────────────────────────────────────────────

    private BorderPane buildProfilesPane() {
        var addBtn = new Button("+ Add");
        addBtn.setStyle(Styles.BTN_GHOST);
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setOnAction(e -> newProfile());

        profileList.setStyle("-fx-background-color: #181825; -fx-border-color: #45475a;");
        profileList.setPrefWidth(180);

        var leftPane = new VBox(6, addBtn, profileList);
        leftPane.setPadding(new Insets(12));
        leftPane.setStyle("-fx-background-color: #181825;");
        VBox.setVgrow(profileList, Priority.ALWAYS);

        typeBox.getItems().addAll(ProviderType.values());
        typeBox.setValue(ProviderType.OPENAI_COMPATIBLE);
        typeBox.setMaxWidth(Double.MAX_VALUE);

        trustSelfSignedCheck.setStyle("-fx-text-fill: #cdd6f4;");

        var form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(16));
        form.setStyle("-fx-background-color: #1e1e2e;");

        addRow(form, 0, "Name",     nameField);
        addRow(form, 1, "Base URL", urlField);
        addRow(form, 2, "Model ID", modelField);
        addRow(form, 3, "API Key",  apiKeyField);
        addRow(form, 4, "Type",     typeBox);
        addRow(form, 5, "",         trustSelfSignedCheck);

        for (var field : new Control[]{nameField, urlField, modelField, apiKeyField}) {
            field.setStyle(Styles.INPUT);
            field.setMaxWidth(Double.MAX_VALUE);
        }

        var sslHint = new Label(
                "Enable only for trusted internal endpoints (localhost, VPN, LAN).\n" +
                        "Disables SSL certificate chain validation."
        );
        sslHint.setStyle(Styles.LABEL_MUTED);
        sslHint.setWrapText(true);
        form.add(sslHint, 1, 6);

        var deleteBtn = new Button("Delete");
        deleteBtn.setStyle(Styles.BTN_GHOST);
        deleteBtn.setOnAction(e -> deleteProfile());

        var saveBtn = new Button("Save");
        saveBtn.setStyle(Styles.BTN_PRIMARY);
        saveBtn.setOnAction(e -> saveProfile());

        var btnBar = new HBox(8, Spacer.h(), deleteBtn, saveBtn);
        btnBar.setPadding(new Insets(0, 16, 16, 16));
        btnBar.setStyle("-fx-background-color: #1e1e2e;");

        ColumnConstraints col1 = new ColumnConstraints(90);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(col1, col2);

        var rightPane = new VBox(form, Spacer.v(), btnBar);
        rightPane.setStyle("-fx-background-color: #1e1e2e;");
        VBox.setVgrow(form, Priority.ALWAYS);

        var root = new BorderPane();
        root.setLeft(leftPane);
        root.setCenter(rightPane);
        root.setStyle("-fx-background-color: #1e1e2e;");
        return root;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void addRow(GridPane grid, int row, String labelText, javafx.scene.Node field) {
        var label = new Label(labelText.isEmpty() ? "" : labelText + ":");
        label.setStyle(Styles.LABEL_MUTED);
        label.setAlignment(Pos.CENTER_RIGHT);
        label.setMaxWidth(Double.MAX_VALUE);
        grid.add(label, 0, row);
        grid.add(field, 1, row);
    }

    private void refreshList() {
        profileList.getItems().setAll(appState.getProfiles());
    }

    private void loadIntoForm(LlmProfile p) {
        if (p == null) return;
        editing = p;
        nameField.setText(p.name);
        urlField.setText(p.baseUrl);
        modelField.setText(p.modelId);
        apiKeyField.setText(appState.getApiKey(p.id));
        typeBox.setValue(p.type);
        trustSelfSignedCheck.setSelected(p.trustSelfSignedCert);
    }

    private void newProfile() {
        editing = null;
        nameField.setText("");
        urlField.setText("http://localhost:11434/v1");
        modelField.setText("");
        apiKeyField.setText("");
        typeBox.setValue(ProviderType.OPENAI_COMPATIBLE);
        trustSelfSignedCheck.setSelected(false);
        profileList.getSelectionModel().clearSelection();
    }

    private void saveProfile() {
        var name   = nameField.getText().trim();
        var url    = urlField.getText().trim();
        var model  = modelField.getText().trim();
        var apiKey = apiKeyField.getText().trim();

        if (name.isEmpty() || url.isEmpty()) {
            showError("Name and Base URL are required.");
            return;
        }

        LlmProfile profile = editing != null ? editing : new LlmProfile();
        profile.name    = name;
        profile.baseUrl = url;
        profile.modelId = model;
        profile.type    = typeBox.getValue();
        profile.trustSelfSignedCert = trustSelfSignedCheck.isSelected();

        appState.setApiKey(profile.id, apiKey);
        appState.saveProfile(profile);

        editing = profile;
        refreshList();
        profileList.getSelectionModel().select(profile);
    }

    private void deleteProfile() {
        if (editing == null) return;
        appState.deleteProfile(editing.id);
        editing = null;
        newProfile();
        refreshList();
    }

    private void showError(String msg) {
        var alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.initOwner(this);
        alert.showAndWait();
    }
}