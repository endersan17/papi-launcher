package org.jackhuang.hmcl.ui.container;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.container.Container;
import org.jackhuang.hmcl.container.ContainerContentEntry;
import org.jackhuang.hmcl.container.ContainerManager;
import org.jackhuang.hmcl.container.ContainerValidationException;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class InstalledContentPickerDialog extends JFXDialogLayout {
    private final Container container;
    private final Runnable onConfirm;

    private final Map<ContainerContentEntry.Type, List<ContainerManager.AvailableContent>> itemsByType = new HashMap<>();
    private final Set<String> installedFileNames = new HashSet<>();
    private final Set<ContainerManager.AvailableContent> selectedItems = new HashSet<>();

    private final Label[] tabLabels;
    private final JFXTextField searchField;
    private final GridPane gridPane;

    private ContainerContentEntry.Type activeTab;

    private static final ContainerContentEntry.Type[] TAB_TYPES = {
            ContainerContentEntry.Type.MOD,
            ContainerContentEntry.Type.WORLD,
            ContainerContentEntry.Type.RESOURCE_PACK,
            ContainerContentEntry.Type.SHADER_PACK
    };

    private static final String[] TAB_I18N_KEYS = {
            "container.mods",
            "container.worlds",
            "container.resourcepacks",
            "container.shaderpacks"
    };

    public InstalledContentPickerDialog(Container container, ContainerContentEntry.Type initialType, Runnable onConfirm) {
        this.container = container;
        this.onConfirm = onConfirm;

        getStylesheets().add(getClass().getResource("/assets/css/container-dialog.css").toExternalForm());
        getStyleClass().add("container-dialog-bg");
        setPrefSize(520, 560);

        Label titleLabel = new Label(i18n("container.add_content.title"));
        titleLabel.getStyleClass().add("container-dialog-title");
        setHeading(titleLabel);

        itemsByType.put(ContainerContentEntry.Type.MOD, ContainerManager.getInstance().listAvailableMods());
        itemsByType.put(ContainerContentEntry.Type.WORLD, ContainerManager.getInstance().listAvailableWorlds());
        itemsByType.put(ContainerContentEntry.Type.RESOURCE_PACK, ContainerManager.getInstance().listAvailableResourcePacks());
        itemsByType.put(ContainerContentEntry.Type.SHADER_PACK, ContainerManager.getInstance().listAvailableShaderPacks());

        List<ContainerContentEntry> existing = ContainerManager.getInstance().getContent(container);
        for (ContainerContentEntry entry : existing) {
            if (entry.getSourcePath() != null) {
                try {
                    installedFileNames.add(new File(entry.getSourcePath()).getCanonicalPath());
                } catch (IOException e) {
                    installedFileNames.add(entry.getFileName());
                }
            } else {
                installedFileNames.add(entry.getFileName());
            }
        }

        HBox tabsRow = new HBox();
        tabsRow.getStyleClass().add("container-picker-tab-bar");
        tabLabels = new Label[4];
        for (int i = 0; i < 4; i++) {
            int idx = i;
            Label tab = new Label(i18n(TAB_I18N_KEYS[i]));
            tab.getStyleClass().add("container-picker-tab");
            tab.setCursor(Cursor.HAND);
            tab.setOnMouseClicked(e -> setActiveTab(TAB_TYPES[idx]));
            tabsRow.getChildren().add(tab);
            tabLabels[i] = tab;
        }

        HBox searchBox = new HBox(6);
        searchBox.setPadding(new Insets(12, 0, 12, 0));
        searchBox.setAlignment(Pos.CENTER_LEFT);

        searchField = new JFXTextField();
        searchField.getStyleClass().add("container-picker-search");
        searchField.setPromptText(i18n("container.add_content.search"));
        HBox.setHgrow(searchField, javafx.scene.layout.Priority.ALWAYS);
        searchBox.getChildren().add(searchField);

        PauseTransition pause = new PauseTransition(Duration.millis(100));
        pause.setOnFinished(e -> rebuildGrid());
        searchField.textProperty().addListener((obs, old, val) -> pause.playFromStart());

        gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        ColumnConstraints pickerCol = new ColumnConstraints();
        pickerCol.setPercentWidth(50);
        pickerCol.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(pickerCol, pickerCol);

        ScrollPane gridScroll = new ScrollPane(gridPane);
        gridScroll.setFitToWidth(true);
        gridScroll.getStyleClass().add("container-dialog-scroll-pane");
        gridScroll.setPrefHeight(360);
        VBox.setVgrow(gridScroll, javafx.scene.layout.Priority.ALWAYS);

        VBox body = new VBox(tabsRow, searchBox, gridScroll);
        VBox.setVgrow(gridScroll, javafx.scene.layout.Priority.ALWAYS);
        setBody(body);

        JFXButton addBtn = new JFXButton(i18n("container.installed_dialog.add_selected"));
        addBtn.getStyleClass().add("container-dialog-save-btn");
        addBtn.setOnAction(e -> {
            boolean hasError = false;
            for (Map.Entry<ContainerContentEntry.Type, List<ContainerManager.AvailableContent>> entry : itemsByType.entrySet()) {
                for (ContainerManager.AvailableContent item : entry.getValue()) {
                    if (selectedItems.contains(item)) {
                        try {
                            ContainerManager.getInstance().addContent(container, entry.getKey(), item.getPath());
                        } catch (ContainerValidationException ex) {
                            Controllers.dialog(ex.getMessage(),
                                    i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                            hasError = true;
                        }
                    }
                }
            }
            if (!hasError) {
                if (onConfirm != null) onConfirm.run();
                fireEvent(new DialogCloseEvent());
            }
        });

        JFXButton cancelBtn = new JFXButton(i18n("button.cancel"));
        cancelBtn.getStyleClass().add("container-dialog-cancel-btn");
        cancelBtn.setOnAction(e -> fireEvent(new DialogCloseEvent()));

        HBox actions = new HBox(10, cancelBtn, addBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(12, 0, 0, 0));
        setActions(actions);

        setActiveTab(initialType);
    }

    private void setActiveTab(ContainerContentEntry.Type type) {
        activeTab = type;
        for (int i = 0; i < 4; i++) {
            if (TAB_TYPES[i] == type) {
                tabLabels[i].getStyleClass().setAll("container-picker-tab", "container-picker-tab-active");
            } else {
                tabLabels[i].getStyleClass().setAll("container-picker-tab");
            }
        }
        searchField.clear();
        rebuildGrid();
    }

    private void rebuildGrid() {
        gridPane.getChildren().clear();

        String query = searchField.getText();
        List<ContainerManager.AvailableContent> items = itemsByType.get(activeTab);
        List<ContainerManager.AvailableContent> filtered = items;
        if (query != null && !query.isEmpty()) {
            String lowerQuery = query.toLowerCase(Locale.ROOT);
            filtered = items.stream()
                    .filter(item -> item.getFileName().toLowerCase(Locale.ROOT).contains(lowerQuery))
                    .collect(Collectors.toList());
        }

        if (filtered.isEmpty()) {
            Label emptyLabel = new Label(i18n("container.installed_dialog.no_items"));
            emptyLabel.getStyleClass().add("container-picker-empty");
            StackPane emptyPane = new StackPane(emptyLabel);
            emptyPane.setPrefHeight(200);
            gridPane.add(emptyPane, 0, 0, 2, 1);
            return;
        }

        int col = 0, row = 0;
        for (ContainerManager.AvailableContent item : filtered) {
            VBox card = createCard(item, activeTab);
            gridPane.add(card, col, row);
            col = (col + 1) % 2;
            if (col == 0) row++;
        }
    }

    private VBox createCard(ContainerManager.AvailableContent item, ContainerContentEntry.Type type) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("container-picker-card");

        boolean installed = isInstalled(item);
        boolean selected = selectedItems.contains(item);

        StackPane icon = wrap(getIconForType(type).createIcon(Theme.foregroundFillBinding(), 32));
        icon.getStyleClass().add("container-picker-icon");

        Label nameLabel = new Label(item.getFileName());
        nameLabel.getStyleClass().add("container-picker-name");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(140);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setMaxHeight(32);

        Label statusBadge;
        if (installed) {
            statusBadge = new Label(i18n("container.add_content.installed"));
            statusBadge.getStyleClass().add("container-picker-badge-installed");
        } else if (selected) {
            statusBadge = new Label(i18n("container.add_content.selected"));
            statusBadge.getStyleClass().add("container-picker-badge-selected");
            card.getStyleClass().add("container-picker-card-selected");
        } else {
            statusBadge = new Label(i18n("container.add_content.not_added"));
            statusBadge.getStyleClass().add("container-picker-badge-notadded");
        }

        card.getChildren().addAll(icon, nameLabel, statusBadge);

        if (!installed) {
            card.setCursor(Cursor.HAND);
            card.setOnMouseClicked(e -> {
                if (selectedItems.contains(item)) {
                    selectedItems.remove(item);
                } else {
                    selectedItems.add(item);
                }
                rebuildGrid();
            });
        }

        return card;
    }

    private static SVG getIconForType(ContainerContentEntry.Type type) {
        switch (type) {
            case MOD: return SVG.EXTENSION;
            case WORLD: return SVG.LANDSCAPE;
            case RESOURCE_PACK:
            case SHADER_PACK: return SVG.ARCHIVE;
            default: return SVG.EXTENSION;
        }
    }

    private boolean isInstalled(ContainerManager.AvailableContent item) {
        try {
            String candidatePath = new File(item.getPath().toString()).getCanonicalPath();
            if (installedFileNames.contains(candidatePath)) return true;
        } catch (IOException ignored) {}
        return installedFileNames.contains(item.getFileName());
    }

    private static StackPane wrap(Node node) {
        StackPane pane = new StackPane(node);
        pane.setPadding(new Insets(0));
        return pane;
    }
}
