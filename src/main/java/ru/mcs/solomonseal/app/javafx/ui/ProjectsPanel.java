package ru.mcs.solomonseal.app.javafx.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import ru.mcs.solomonseal.core.index.IndexManager;
import ru.mcs.solomonseal.core.projects.Project;
import ru.mcs.solomonseal.core.session.Session;
import ru.mcs.solomonseal.core.session.SessionManager;

import java.io.File;

public class ProjectsPanel extends VBox {

    private final SessionManager sessionManager;
    private final IndexManager   indexManager;
    private final VBox           projectList = new VBox(4);
    private Session              currentSession;

    public ProjectsPanel(SessionManager sessionManager, IndexManager indexManager) {
        this.sessionManager = sessionManager;
        this.indexManager   = indexManager;

        setStyle("-fx-background-color: #181825; -fx-border-color: #313244; -fx-border-width: 1 0 0 0;");
        setPadding(new Insets(8));
        setSpacing(6);

        var addBtn = new Button("+ Add");
        addBtn.setStyle(Styles.BTN_GHOST);
        addBtn.setPadding(new Insets(3, 8, 3, 8));
        addBtn.setOnAction(e -> addProject());

        var label = new Label("Projects");
        label.setStyle(Styles.LABEL_MUTED);

        var header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(label, Spacer.h(), addBtn);

        getChildren().addAll(header, projectList);
    }

    public void loadSession(Session session) {
        this.currentSession = session;
        refresh();
    }

    public void refresh() {
        projectList.getChildren().clear();
        if (currentSession == null || currentSession.projects.isEmpty()) {
            var empty = new Label("No projects");
            empty.setStyle(Styles.LABEL_MUTED);
            empty.setPadding(new Insets(2, 4, 2, 4));
            projectList.getChildren().add(empty);
            return;
        }
        for (var project : currentSession.projects) {
            projectList.getChildren().add(buildProjectRow(project));
        }
    }

    // ── Row ───────────────────────────────────────────────────────────────────

    private HBox buildProjectRow(Project project) {
        var nameLabel = new Label("📁 " + project.displayName());
        nameLabel.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 12px;");

        var pathLabel = new Label(project.rootPath);
        pathLabel.setStyle(Styles.LABEL_MUTED);
        pathLabel.setMaxWidth(160);
        Tooltip.install(nameLabel, new Tooltip(project.rootPath));

        var info = new VBox(1, nameLabel, pathLabel);

        if (project.indexed) {
            var badge = new Label("indexed");
            badge.setStyle(
                    "-fx-background-color: #1a3a2a;" +
                            "-fx-text-fill: #a6e3a1;" +
                            "-fx-font-size: 9px;" +
                            "-fx-background-radius: 3;" +
                            "-fx-padding: 1 5 1 5;"
            );
            info.getChildren().add(badge);
        }

        var indexBtn  = buildIconBtn("⟳", "#89b4fa", "Index project");
        var removeBtn = buildIconBtn("×", "#6c7086", "Remove project");

        indexBtn.setOnAction(e -> indexProject(project, indexBtn));
        removeBtn.setOnAction(e -> removeProject(project));
        removeBtn.setOnMouseEntered(e -> removeBtn.setStyle(iconStyle("#f38ba8")));
        removeBtn.setOnMouseExited(e  -> removeBtn.setStyle(iconStyle("#6c7086")));

        var row = new HBox(4, info, Spacer.h(), indexBtn, removeBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 4, 3, 4));
        row.setStyle(
                "-fx-background-color: #1e1e2e;" +
                        "-fx-background-radius: 6;" +
                        "-fx-border-color: #313244;" +
                        "-fx-border-radius: 6;"
        );
        return row;
    }

    private Button buildIconBtn(String text, String color, String tooltipText) {
        var btn = new Button(text);
        btn.setStyle(iconStyle(color));
        btn.setTooltip(new Tooltip(tooltipText));
        return btn;
    }

    private String iconStyle(String color) {
        return "-fx-background-color: transparent;" +
                "-fx-text-fill: " + color + ";" +
                "-fx-font-size: 13px;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 0 4 0 4;";
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void indexProject(Project project, Button indexBtn) {
        indexBtn.setDisable(true);
        indexBtn.setText("...");

        indexManager.indexAsync(
                project,
                (file, current, total) -> javafx.application.Platform.runLater(() ->
                        indexBtn.setText(current + "/" + total)
                ),
                () -> javafx.application.Platform.runLater(() -> {
                    indexBtn.setDisable(false);
                    indexBtn.setText("✓");
                    indexBtn.setStyle(iconStyle("#a6e3a1"));
                    sessionManager.save(currentSession);
                    refresh();
                }),
                err -> javafx.application.Platform.runLater(() -> {
                    indexBtn.setText("✗");
                    indexBtn.setDisable(false);
                    var alert = new Alert(Alert.AlertType.ERROR,
                            "Indexing failed: " + err.getMessage(), ButtonType.OK);
                    alert.setHeaderText(null);
                    alert.showAndWait();
                })
        );
    }

    private void addProject() {
        if (currentSession == null) return;

        var chooser = new DirectoryChooser();
        chooser.setTitle("Select Project Folder");
        File dir = chooser.showDialog(getScene().getWindow());
        if (dir == null) return;

        // Алиас
        var aliasDialog = new TextInputDialog(dir.getName());
        aliasDialog.setTitle("Project Alias");
        aliasDialog.setHeaderText("Enter a short alias for this project");
        aliasDialog.setContentText("Alias:");
        aliasDialog.getEditor().setStyle(Styles.INPUT);
        String alias = aliasDialog.showAndWait()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .orElse(dir.getName());

        // Язык
        var langDialog = new ChoiceDialog<>(
                "java", "java", "kotlin", "python", "javascript", "typescript", "other"
        );
        langDialog.setTitle("Project Language");
        langDialog.setHeaderText("Select the primary language of this project");
        langDialog.setContentText("Language:");
        String language = langDialog.showAndWait().orElse("java");

        var project = new Project(alias, dir.getAbsolutePath(), language);
        currentSession.addProject(project);
        sessionManager.save(currentSession);
        refresh();
    }

    private void removeProject(Project project) {
        if (currentSession == null) return;

        var alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove project \"" + project.displayName() + "\" from this session?",
                ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.initOwner(getScene().getWindow());

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                currentSession.removeProject(project.id);
                sessionManager.save(currentSession);
                refresh();
            }
        });
    }
}