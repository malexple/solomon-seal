package ru.mcs.llmrobot.app.javafx.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import ru.mcs.llmrobot.core.projects.Project;
import ru.mcs.llmrobot.core.session.Session;
import ru.mcs.llmrobot.core.session.SessionManager;

import java.io.File;

/**
 * Панель управления проектами текущей сессии.
 * Показывается под списком сессий.
 *
 * Layout:
 * ┌─────────────────────────┐
 * │ Projects  [+ Add]       │
 * ├─────────────────────────┤
 * │ 📁 backend  [×]         │
 * │    /home/user/backend   │
 * │ 📁 frontend [×]         │
 * │    /home/user/frontend  │
 * └─────────────────────────┘
 */
public class ProjectsPanel extends VBox {

    private final SessionManager sessionManager;
    private final VBox projectList = new VBox(4);
    private Session currentSession;

    public ProjectsPanel(SessionManager sessionManager) {
        this.sessionManager = sessionManager;

        setStyle("-fx-background-color: #181825; -fx-border-color: #313244; -fx-border-width: 1 0 0 0;");
        setPadding(new Insets(8));
        setSpacing(6);

        var addBtn = new Button("+ Add");
        addBtn.setStyle(Styles.BTN_GHOST);
        addBtn.setPadding(new Insets(3, 8, 3, 8));
        addBtn.setOnAction(e -> addProject());

        var header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        var label = new Label("Projects");
        label.setStyle(Styles.LABEL_MUTED);
        header.getChildren().addAll(label, Spacer.h(), addBtn);

        projectList.setPadding(new Insets(0));

        getChildren().addAll(header, projectList);
    }

    /** Загружает проекты текущей сессии */
    public void loadSession(Session session) {
        this.currentSession = session;
        refresh();
    }

    /** Перерисовывает список проектов */
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

    private HBox buildProjectRow(Project project) {
        var nameLabel = new Label("📁 " + project.displayName());
        nameLabel.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 12px;");

        var pathLabel = new Label(project.rootPath);
        pathLabel.setStyle(Styles.LABEL_MUTED);
        pathLabel.setMaxWidth(160);

        // tooltip показывает полный путь
        var tooltip = new Tooltip(project.rootPath);
        Tooltip.install(nameLabel, tooltip);

        var removeBtn = new Button("×");
        removeBtn.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #6c7086;" +
                        "-fx-font-size: 13px;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 0 4 0 4;"
        );
        removeBtn.setOnAction(e -> removeProject(project));
        removeBtn.setOnMouseEntered(e -> removeBtn.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #f38ba8;" +
                        "-fx-font-size: 13px;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 0 4 0 4;"
        ));
        removeBtn.setOnMouseExited(e -> removeBtn.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #6c7086;" +
                        "-fx-font-size: 13px;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 0 4 0 4;"
        ));

        var info = new VBox(1, nameLabel, pathLabel);

        var row = new HBox(4, info, Spacer.h(), removeBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 4, 3, 4));
        row.setStyle(
                "-fx-background-color: #1e1e2e;" +
                        "-fx-background-radius: 6;" +
                        "-fx-border-color: #313244;" +
                        "-fx-border-radius: 6;"
        );

        // indexed badge
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

        return row;
    }

    private void addProject() {
        if (currentSession == null) return;

        var chooser = new DirectoryChooser();
        chooser.setTitle("Select Project Folder");
        File dir = chooser.showDialog(getScene().getWindow());
        if (dir == null) return;

        // диалог ввода алиаса
        var dialog = new TextInputDialog(dir.getName());
        dialog.setTitle("Project Alias");
        dialog.setHeaderText("Enter a short alias for this project");
        dialog.setContentText("Alias:");
        dialog.getEditor().setStyle(Styles.INPUT);

        var result = dialog.showAndWait();
        String alias = result.orElse(dir.getName()).trim();
        if (alias.isBlank()) alias = dir.getName();

        // выбор языка
        var langDialog = new ChoiceDialog<>("java", "java", "kotlin", "python", "javascript", "typescript", "other");
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