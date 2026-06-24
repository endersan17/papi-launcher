package org.jackhuang.hmcl.ui.container;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXTextField;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.container.ContainerManager;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class CreateContainerDialog extends JFXDialogLayout {
    private final JFXTextField nameField = new JFXTextField();
    private final JFXTextField descriptionField = new JFXTextField();
    private final Runnable onCreated;

    public CreateContainerDialog(Runnable onCreated) {
        this.onCreated = onCreated;
        setStyle("-fx-background-color: #0A0A0F;");

        Label titleLabel = new Label(i18n("container.create.title"));
        titleLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 18px; -fx-font-weight: bold;");
        setHeading(titleLabel);

        nameField.setLabelFloat(true);
        nameField.setPromptText(i18n("container.create.name"));
        nameField.setStyle("-fx-text-fill: #FFFFFF; -fx-prompt-text-fill: #666666;");

        descriptionField.setLabelFloat(true);
        descriptionField.setPromptText(i18n("container.create.description"));
        descriptionField.setStyle("-fx-text-fill: #FFFFFF; -fx-prompt-text-fill: #666666;");

        VBox body = new VBox(12, nameField, descriptionField);
        body.setStyle("-fx-padding: 8 0 0 0;");
        setBody(body);

        SpinnerPane acceptPane = new SpinnerPane();
        acceptPane.getStyleClass().add("small-spinner-pane");
        JFXButton acceptButton = new JFXButton(i18n("button.ok"));
        acceptButton.getStyleClass().add("dialog-accept");
        acceptPane.setContent(acceptButton);

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");

        setActions(acceptPane, cancelButton);

        cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        acceptButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;

            acceptPane.showSpinner();
            acceptButton.setDisable(true);
            cancelButton.setDisable(true);

            Task.runAsync(() -> ContainerManager.getInstance().createContainer(name,
                    Profiles.getSelectedProfile().getSelectedVersion(),
                    descriptionField.getText().trim()))
                    .whenComplete(Schedulers.javafx(), (container, exc) -> {
                        acceptPane.hideSpinner();
                        acceptButton.setDisable(false);
                        cancelButton.setDisable(false);
                        if (exc != null) {
                            LOG.warning("Failed to create container", exc);
                            return;
                        }
                        if (onCreated != null) {
                            onCreated.run();
                        }
                        fireEvent(new DialogCloseEvent());
                    }).start();
        });

        onEscPressed(this, cancelButton::fire);
    }
}
