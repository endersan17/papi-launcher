package org.jackhuang.hmcl.ui.container;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXTextArea;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.container.Container;
import org.jackhuang.hmcl.container.ContainerManager;
import org.jackhuang.hmcl.container.LaunchProfile;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;

import java.io.File;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class CreateEditProfileDialog extends JFXDialogLayout {
    private final Container container;
    private final LaunchProfile profile;
    private int activeTabIndex = 0;

    private final JFXTextField nameField;
    private final JFXTextArea descriptionArea;
    private final JFXTextField widthField;
    private final JFXTextField heightField;
    private final JFXCheckBox fullscreenCheck;
    private final JFXTextField serverIpField;
    private final JFXTextField maxMemoryField;
    private final JFXTextField minMemoryField;
    private final JFXTextField javaDirField;
    private final JFXTextField wrapperField;
    private final JFXTextArea jvmArgsArea;
    private final JFXTextArea gameArgsArea;
    private final JFXToggleButton forceJavaCompatToggle;
    private final JFXToggleButton experimentalRenderingToggle;

    private final JFXButton tabGeneral;
    private final JFXButton tabResolution;
    private final JFXButton tabAdvanced;
    private final VBox contentStack;
    private final VBox generalContent;
    private final VBox resolutionContent;
    private final ScrollPane advancedScroll;

    public CreateEditProfileDialog(Container container, LaunchProfile existing, Runnable onSaved) {
        this.container = container;
        this.profile = existing != null ? existing.copy() : new LaunchProfile();

        getStylesheets().add(getClass().getResource("/assets/css/container-dialog.css").toExternalForm());
        getStyleClass().add("container-dialog-bg");

        setPrefWidth(520);
        setPadding(new Insets(24, 24, 16, 24));

        boolean isEdit = existing != null;

        Label titleLabel = new Label(i18n(isEdit ? "container.profile.edit" : "container.profile.create"));
        titleLabel.getStyleClass().add("container-dialog-title");

        Region headerSep = new Region();
        headerSep.getStyleClass().add("container-dialog-separator");
        headerSep.setMaxWidth(Double.MAX_VALUE);

        VBox headerBox = new VBox(12, titleLabel, headerSep);
        setHeading(headerBox);

        nameField = createTextField(profile.getName());
        nameField.setPromptText(i18n("container.profile.name"));

        descriptionArea = new JFXTextArea(profile.getDescription() != null ? profile.getDescription() : "");
        descriptionArea.setPromptText(i18n("container.profile.description"));
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);
        StackPane descriptionWrapper = createTextAreaWrapper(descriptionArea);

        widthField = createTextField(profile.getWidth() != null ? String.valueOf(profile.getWidth()) : "");
        heightField = createTextField(profile.getHeight() != null ? String.valueOf(profile.getHeight()) : "");

        fullscreenCheck = new JFXCheckBox();
        fullscreenCheck.setSelected(profile.getFullscreen() != null && profile.getFullscreen());
        fullscreenCheck.getStyleClass().add("container-dialog-check");

        serverIpField = createTextField(profile.getServerIp());
        serverIpField.setPromptText("play.servidor.com:25565");

        maxMemoryField = createTextField(profile.getMaxMemory() != null ? String.valueOf(profile.getMaxMemory()) : "");
        minMemoryField = createTextField(profile.getMinMemory() != null ? String.valueOf(profile.getMinMemory()) : "");

        // HIGH 3: Inline numeric validation — reject non-digit input
        maxMemoryField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) maxMemoryField.setText(oldVal);
        });
        minMemoryField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) minMemoryField.setText(oldVal);
        });
        widthField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) widthField.setText(oldVal);
        });
        heightField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) heightField.setText(oldVal);
        });

        javaDirField = createTextField(profile.getJavaDir());
        wrapperField = createTextField(profile.getWrapper());

        jvmArgsArea = new JFXTextArea(profile.getJvmArgs() != null ? profile.getJvmArgs() : "");
        jvmArgsArea.setPromptText("-XX:+UseG1GC -XX:MaxGCPauseMillis=200");
        jvmArgsArea.setPrefRowCount(3);
        jvmArgsArea.setWrapText(true);
        StackPane jvmArgsWrapper = createTextAreaWrapper(jvmArgsArea);

        gameArgsArea = new JFXTextArea(profile.getGameArgs() != null ? profile.getGameArgs() : "");
        gameArgsArea.setPromptText("--add-modules ALL-MODULE-PATH");
        gameArgsArea.setPrefRowCount(3);
        gameArgsArea.setWrapText(true);
        StackPane gameArgsWrapper = createTextAreaWrapper(gameArgsArea);

        forceJavaCompatToggle = createToggle(profile.getForceJavaCompat() != null && profile.getForceJavaCompat());
        experimentalRenderingToggle = createToggle(profile.getExperimentalRendering() != null && profile.getExperimentalRendering());

        tabGeneral = createTab(i18n("container.profile.tab.general"));
        tabResolution = createTab(i18n("container.profile.tab.resolution"));
        tabAdvanced = createTab(i18n("container.profile.tab.advanced"));

        tabGeneral.setOnAction(e -> setActiveTab(0));
        tabResolution.setOnAction(e -> setActiveTab(1));
        tabAdvanced.setOnAction(e -> setActiveTab(2));

        HBox tabBar = new HBox(10, tabGeneral, tabResolution, tabAdvanced);
        tabBar.setPadding(new Insets(0, 0, 14, 0));
        tabBar.setAlignment(Pos.CENTER);

        Region tabSep = new Region();
        tabSep.getStyleClass().add("container-dialog-separator");
        tabSep.setMaxWidth(Double.MAX_VALUE);

        generalContent = new VBox(16);
        generalContent.setPadding(new Insets(16, 0, 0, 0));
        generalContent.getChildren().addAll(
                fieldGroup(i18n("container.profile.name"), nameField),
                fieldGroup(i18n("container.profile.description"), descriptionWrapper)
        );

        resolutionContent = new VBox(16);
        resolutionContent.setPadding(new Insets(16, 0, 0, 0));

        HBox resRow = new HBox(16);
        VBox widthGroup = fieldGroup(i18n("container.profile.width"), widthField);
        VBox heightGroup = fieldGroup(i18n("container.profile.height"), heightField);
        HBox.setHgrow(widthGroup, Priority.ALWAYS);
        HBox.setHgrow(heightGroup, Priority.ALWAYS);
        resRow.getChildren().addAll(widthGroup, heightGroup);

        resolutionContent.getChildren().addAll(
                resRow,
                checkField(i18n("container.profile.fullscreen"), fullscreenCheck),
                fieldGroup(i18n("container.profile.server_ip"), serverIpField)
        );

        VBox advancedContent = new VBox(16);
        advancedContent.setPadding(new Insets(16, 0, 0, 0));
        advancedContent.getStyleClass().add("container-dialog-advanced-bg");

        HBox memRow = new HBox(16);
        VBox minMemGroup = fieldGroup(i18n("container.profile.min_memory"), minMemoryField);
        VBox maxMemGroup = fieldGroup(i18n("container.profile.max_memory"), maxMemoryField);
        HBox.setHgrow(minMemGroup, Priority.ALWAYS);
        HBox.setHgrow(maxMemGroup, Priority.ALWAYS);
        memRow.getChildren().addAll(minMemGroup, maxMemGroup);

        JFXButton javaDirBtn = new JFXButton(i18n("container.open_folder"));
        javaDirBtn.getStyleClass().add("container-dialog-java-btn");
        javaDirBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(i18n("container.profile.java_dir"));
            if (!javaDirField.getText().isEmpty()) {
                File dir = new File(javaDirField.getText());
                if (dir.isDirectory()) chooser.setInitialDirectory(dir);
            }
            File selected = chooser.showDialog(Controllers.getStage());
            if (selected != null) {
                javaDirField.setText(selected.getAbsolutePath());
            }
        });
        HBox javaDirRow = new HBox(10, javaDirField, javaDirBtn);
        HBox.setHgrow(javaDirField, Priority.ALWAYS);

        // HIGH 4: Wrapper executable field
        JFXButton wrapperBrowseBtn = new JFXButton(i18n("container.open_folder"));
        wrapperBrowseBtn.getStyleClass().add("container-dialog-java-btn");
        wrapperBrowseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(i18n("container.profile.wrapper"));
            if (!wrapperField.getText().isEmpty()) {
                File wrapperFile = new File(wrapperField.getText());
                if (wrapperFile.isFile()) chooser.setInitialDirectory(wrapperFile.getParentFile());
            }
            File selected = chooser.showOpenDialog(Controllers.getStage());
            if (selected != null) {
                wrapperField.setText(selected.getAbsolutePath());
            }
        });
        HBox wrapperRow = new HBox(10, wrapperField, wrapperBrowseBtn);
        HBox.setHgrow(wrapperField, Priority.ALWAYS);
        Label wrapperHelp = new Label(i18n("container.profile.wrapper.help"));
        wrapperHelp.setStyle("-fx-text-fill: -papi-text-tertiary; -fx-font-size: 10px;");

        VBox experimentalSection = new VBox(12);
        experimentalSection.getStyleClass().add("container-dialog-card");
        Label experimentalTitle = new Label(i18n("container.profile.experimental"));
        experimentalTitle.getStyleClass().add("container-dialog-section-title");
        experimentalSection.getChildren().addAll(
                experimentalTitle,
                toggleRow(i18n("container.profile.force_java_compat"), forceJavaCompatToggle),
                toggleRow(i18n("container.profile.experimental_rendering"), experimentalRenderingToggle)
        );

        advancedContent.getChildren().addAll(
                memRow,
                fieldGroup(i18n("container.profile.java_dir"), javaDirRow),
                fieldGroup(i18n("container.profile.wrapper"), wrapperRow),
                wrapperHelp,
                fieldGroup(i18n("container.profile.jvm_args"), jvmArgsWrapper),
                fieldGroup(i18n("container.profile.game_args"), gameArgsWrapper),
                experimentalSection
        );

        advancedScroll = new ScrollPane(advancedContent);
        advancedScroll.setFitToWidth(true);
        advancedScroll.getStyleClass().addAll("edge-to-edge", "container-dialog-scroll");
        advancedScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        contentStack = new VBox();
        contentStack.getChildren().addAll(generalContent, resolutionContent, advancedScroll);
        for (int i = 1; i < 3; i++) {
            contentStack.getChildren().get(i).setVisible(false);
            contentStack.getChildren().get(i).setManaged(false);
        }

        VBox body = new VBox(0, tabBar, tabSep, contentStack);
        VBox.setVgrow(contentStack, Priority.ALWAYS);
        setBody(body);

        updateTabStyles();

        Region bottomSep = new Region();
        bottomSep.getStyleClass().add("container-dialog-separator");
        bottomSep.setMaxWidth(Double.MAX_VALUE);

        JFXButton cancelBtn = new JFXButton(i18n("button.cancel"));
        cancelBtn.getStyleClass().add("container-dialog-cancel-btn");
        cancelBtn.setOnAction(e -> fireEvent(new DialogCloseEvent()));

        JFXButton saveBtn = new JFXButton(i18n("button.ok"));
        saveBtn.getStyleClass().add("container-dialog-save-btn");
        saveBtn.setOnAction(e -> {
            profile.setName(nameField.getText());
            profile.setDescription(emptyToNull(descriptionArea.getText()));

            try {
                String minMem = minMemoryField.getText().trim();
                profile.setMinMemory(minMem.isEmpty() ? null : Integer.parseInt(minMem));
            } catch (NumberFormatException ignored) {}

            try {
                String maxMem = maxMemoryField.getText().trim();
                profile.setMaxMemory(maxMem.isEmpty() ? null : Integer.parseInt(maxMem));
            } catch (NumberFormatException ignored) {}

            profile.setJvmArgs(emptyToNull(jvmArgsArea.getText()));
            profile.setGameArgs(emptyToNull(gameArgsArea.getText()));

            try {
                String w = widthField.getText().trim();
                profile.setWidth(w.isEmpty() ? null : Integer.parseInt(w));
            } catch (NumberFormatException ignored) {}

            try {
                String h = heightField.getText().trim();
                profile.setHeight(h.isEmpty() ? null : Integer.parseInt(h));
            } catch (NumberFormatException ignored) {}

            profile.setFullscreen(fullscreenCheck.isSelected() ? Boolean.TRUE : null);
            profile.setServerIp(emptyToNull(serverIpField.getText()));
            profile.setJavaDir(emptyToNull(javaDirField.getText()));
            profile.setWrapper(emptyToNull(wrapperField.getText()));
            profile.setForceJavaCompat(forceJavaCompatToggle.isSelected() ? Boolean.TRUE : null);
            profile.setExperimentalRendering(experimentalRenderingToggle.isSelected() ? Boolean.TRUE : null);

            ContainerManager.getInstance().saveLaunchProfile(container, profile);
            fireEvent(new DialogCloseEvent());
            if (onSaved != null) onSaved.run();
        });

        HBox actionBtnBox = new HBox(12, cancelBtn, saveBtn);
        actionBtnBox.setAlignment(Pos.CENTER_RIGHT);
        VBox actionBox = new VBox(14, bottomSep, actionBtnBox);
        setActions(actionBox);
    }

    private void setActiveTab(int index) {
        if (index == activeTabIndex) return;

        // LOW 9: Save scroll position before switching away from Advanced tab
        double[] savedScroll = {0};
        if (activeTabIndex == 2) {
            savedScroll[0] = advancedScroll.getVvalue();
        }

        contentStack.getChildren().get(activeTabIndex).setVisible(false);
        contentStack.getChildren().get(activeTabIndex).setManaged(false);
        contentStack.getChildren().get(index).setVisible(true);
        contentStack.getChildren().get(index).setManaged(true);
        activeTabIndex = index;

        // LOW 9: Restore scroll position when switching back to Advanced tab
        if (index == 2 && savedScroll[0] > 0) {
            Platform.runLater(() -> advancedScroll.setVvalue(savedScroll[0]));
        }

        updateTabStyles();
    }

    private void updateTabStyles() {
        tabGeneral.getStyleClass().remove("container-dialog-tab-active");
        tabResolution.getStyleClass().remove("container-dialog-tab-active");
        tabAdvanced.getStyleClass().remove("container-dialog-tab-active");
        switch (activeTabIndex) {
            case 0: tabGeneral.getStyleClass().add("container-dialog-tab-active"); break;
            case 1: tabResolution.getStyleClass().add("container-dialog-tab-active"); break;
            case 2: tabAdvanced.getStyleClass().add("container-dialog-tab-active"); break;
            default: break;
        }
    }

    private static JFXButton createTab(String text) {
        JFXButton tab = new JFXButton(text);
        tab.getStyleClass().add("container-dialog-tab");
        return tab;
    }

    private static JFXTextField createTextField(String value) {
        JFXTextField field = new JFXTextField(value != null ? value : "");
        field.getStyleClass().add("container-dialog-input");
        return field;
    }

    private static JFXToggleButton createToggle(boolean selected) {
        JFXToggleButton toggle = new JFXToggleButton();
        toggle.setSelected(selected);
        toggle.getStyleClass().add("container-dialog-toggle");
        toggle.setSize(8);
        return toggle;
    }

    private static StackPane createTextAreaWrapper(JFXTextArea textArea) {
        textArea.getStyleClass().add("container-dialog-textarea");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(textArea.widthProperty());
        clip.heightProperty().bind(textArea.heightProperty());
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        textArea.setClip(clip);
        StackPane wrapper = new StackPane(textArea);
        wrapper.getStyleClass().add("container-dialog-textarea-wrapper");
        return wrapper;
    }

    private static VBox fieldGroup(String labelText, javafx.scene.Node field) {
        Label label = new Label(labelText);
        label.getStyleClass().add("container-dialog-label");
        VBox group = new VBox(6, label, field);
        return group;
    }

    private static HBox checkField(String labelText, JFXCheckBox checkBox) {
        Label label = new Label(labelText);
        label.getStyleClass().add("container-dialog-label");
        HBox row = new HBox(10, checkBox, label);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static HBox toggleRow(String text, JFXToggleButton toggle) {
        Label label = new Label(text);
        label.getStyleClass().add("container-dialog-toggle-label");
        HBox row = new HBox(10, label, toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }
}
