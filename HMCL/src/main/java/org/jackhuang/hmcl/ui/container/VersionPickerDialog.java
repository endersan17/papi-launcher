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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.container.Container;
import org.jackhuang.hmcl.container.ContainerManager;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ReleaseType;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class VersionPickerDialog extends JFXDialogLayout {
    private final Container container;
    private final Runnable onConfirm;

    private String selectedVersionId;
    private final List<VersionInfo> allVersions = new ArrayList<>();

    private final HBox tabsRow;
    private final Label[] tabLabels;
    private final List<String> tabLoaderNames;
    private final JFXTextField searchField;
    private final GridPane gridPane;

    private String activeLoaderFilter;

    private static final String TAB_ALL = null;

    public VersionPickerDialog(Container container, Runnable onConfirm) {
        this.container = container;
        this.onConfirm = onConfirm;
        this.selectedVersionId = container.getLinkedVersionId();

        getStylesheets().add(getClass().getResource("/assets/css/container-dialog.css").toExternalForm());
        getStyleClass().add("container-dialog-bg");

        HBox titleBox = new HBox(8);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Region accent = new Region();
        accent.getStyleClass().add("container-dialog-accent");
        Label titleLabel = new Label(i18n("container.select_version"));
        titleLabel.getStyleClass().add("container-dialog-title");
        titleBox.getChildren().addAll(accent, titleLabel);
        setHeading(titleBox);

        Profile profile = Profiles.getSelectedProfile();
        HMCLGameRepository repo = profile != null ? profile.getRepository() : null;
        if (repo != null) {
            for (Version v : repo.getVersions()) {
                if (v.isHidden()) continue;
                try {
                    VersionInfo info = buildVersionInfo(repo, v);
                    allVersions.add(info);
                } catch (Exception ignored) {
                }
            }
            allVersions.sort((a, b) -> {
                boolean aIsCur = a.versionId.equals(selectedVersionId);
                boolean bIsCur = b.versionId.equals(selectedVersionId);
                if (aIsCur != bIsCur) return aIsCur ? -1 : 1;
                int cmp = b.gameVersion.compareToIgnoreCase(a.gameVersion);
                if (cmp != 0) return cmp;
                return a.versionId.compareToIgnoreCase(b.versionId);
            });
        }

        tabsRow = new HBox();
        tabLoaderNames = new ArrayList<>();
        tabLoaderNames.add("All");
        tabLoaderNames.add("Fabric");
        tabLoaderNames.add("Forge");
        tabLoaderNames.add("OptiFine");
        tabLoaderNames.add("NeoForge");
        tabLoaderNames.add("Otros");
        tabLabels = new Label[tabLoaderNames.size()];
        for (int i = 0; i < tabLoaderNames.size(); i++) {
            int idx = i;
            String name = tabLoaderNames.get(i);
            Label tab = new Label(name);
            tab.getStyleClass().add("container-dialog-tab");
            tab.setCursor(Cursor.HAND);
            tab.setOnMouseClicked(e -> setActiveTab(name.equals("All") ? TAB_ALL : name));
            tabsRow.getChildren().add(tab);
            tabLabels[i] = tab;
        }

        HBox searchBox = new HBox(6);
        searchBox.setPadding(new Insets(12, 0, 12, 0));
        searchBox.setAlignment(Pos.CENTER_LEFT);

        searchField = new JFXTextField();
        searchField.getStyleClass().add("container-picker-search");
        searchField.setPromptText(i18n("container.version_search"));
        HBox.setHgrow(searchField, javafx.scene.layout.Priority.ALWAYS);
        searchBox.getChildren().add(searchField);

        PauseTransition pause = new PauseTransition(Duration.millis(100));
        pause.setOnFinished(e -> rebuildGrid());
        searchField.textProperty().addListener((obs, old, val) -> pause.playFromStart());

        gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        ColumnConstraints versionCol = new ColumnConstraints();
        versionCol.setPercentWidth(33.33);
        versionCol.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(versionCol, versionCol, versionCol);

        ScrollPane gridScroll = new ScrollPane(gridPane);
        gridScroll.setFitToWidth(true);
        gridScroll.getStyleClass().add("container-dialog-scroll-pane");
        gridScroll.setPrefHeight(360);
        VBox.setVgrow(gridScroll, javafx.scene.layout.Priority.ALWAYS);

        VBox body = new VBox(tabsRow, searchBox, gridScroll);
        VBox.setVgrow(gridScroll, javafx.scene.layout.Priority.ALWAYS);
        setBody(body);

        JFXButton acceptBtn = new JFXButton(i18n("button.ok"));
        acceptBtn.getStyleClass().add("container-dialog-save-btn");
        acceptBtn.setOnAction(e -> {
            if (selectedVersionId != null) {
                ContainerManager.getInstance().setContainerVersion(container, selectedVersionId);
            }
            if (onConfirm != null) onConfirm.run();
            fireEvent(new DialogCloseEvent());
        });

        JFXButton cancelBtn = new JFXButton(i18n("button.cancel"));
        cancelBtn.getStyleClass().add("container-dialog-cancel-btn");
        cancelBtn.setOnAction(e -> fireEvent(new DialogCloseEvent()));

        HBox actions = new HBox(10, cancelBtn, acceptBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(12, 0, 0, 0));
        setActions(actions);

        setActiveTab(TAB_ALL);
    }

    private void setActiveTab(String loaderType) {
        activeLoaderFilter = loaderType;
        for (int i = 0; i < tabLabels.length; i++) {
            String text = tabLabels[i].getText();
            boolean isActive = (loaderType == TAB_ALL && "All".equals(text))
                    || (loaderType != null && loaderType.equals(text));
            if (isActive) {
                tabLabels[i].getStyleClass().setAll("container-dialog-tab", "container-dialog-tab-active");
            } else {
                tabLabels[i].getStyleClass().setAll("container-dialog-tab");
            }
        }
        searchField.clear();
        rebuildGrid();
    }

    private void rebuildGrid() {
        gridPane.getChildren().clear();

        String query = searchField.getText() != null ? searchField.getText().toLowerCase(Locale.ROOT) : "";

        List<VersionInfo> filtered = allVersions.stream()
                .filter(info -> {
                    if (activeLoaderFilter == TAB_ALL) return true;
                    return activeLoaderFilter.equals(info.loaderType);
                })
                .filter(info -> {
                    if (query.isEmpty()) return true;
                    return info.versionId.toLowerCase(Locale.ROOT).contains(query)
                            || info.gameVersion.toLowerCase(Locale.ROOT).contains(query);
                })
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            String msg = query.isEmpty() ? "No hay instancias instaladas" : i18n("container.version_search.empty");
            Label emptyLabel = new Label(msg);
            emptyLabel.getStyleClass().add("container-picker-empty");
            StackPane emptyPane = new StackPane(emptyLabel);
            emptyPane.setPrefHeight(200);
            gridPane.add(emptyPane, 0, 0, 3, 1);
            return;
        }

        int col = 0, row = 0;
        for (VersionInfo info : filtered) {
            VBox card = createCard(info);
            gridPane.add(card, col, row);
            col = (col + 1) % 3;
            if (col == 0) row++;
        }
    }

    private VBox createCard(VersionInfo info) {
        boolean isSelected = info.versionId.equals(selectedVersionId);

        VBox card = new VBox(6);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setCursor(Cursor.HAND);
        card.getStyleClass().add("container-version-card");

        SVG iconType = getIconForLoader(info.loaderType);
        StackPane icon = wrap(iconType.createIcon(Theme.foregroundFillBinding(), 28));
        icon.getStyleClass().add("container-version-icon");

        String displayName = "Otros".equals(info.loaderType)
                ? info.gameVersion
                : info.loaderType != null
                        ? info.loaderType + " " + info.gameVersion
                        : "Vanilla " + info.gameVersion;
        Label nameLabel = new Label(displayName);
        nameLabel.getStyleClass().add("container-version-name");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(130);
        nameLabel.setAlignment(Pos.CENTER);

        VBox detailsBox = new VBox(2);
        detailsBox.setAlignment(Pos.CENTER);

        if (info.releaseTime != null) {
            String dateStr = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    .withZone(ZoneId.systemDefault())
                    .format(info.releaseTime);
            Label dateLabel = new Label(dateStr);
            dateLabel.getStyleClass().add("container-version-date");
            detailsBox.getChildren().add(dateLabel);
        }

        if (isSelected) {
            card.getStyleClass().add("container-version-card-selected");
            Label selectedBadge = new Label(i18n("container.version.selected"));
            selectedBadge.getStyleClass().add("container-version-selected-badge");
            detailsBox.getChildren().add(selectedBadge);
        }

        card.getChildren().addAll(icon, nameLabel, detailsBox);

        card.setOnMouseClicked(e -> {
            selectedVersionId = info.versionId;
            rebuildGrid();
        });

        return card;
    }

    private static VersionInfo buildVersionInfo(HMCLGameRepository repo, Version v) {
        String versionId = v.getId();
        String gameVersion = repo.getGameVersion(v).orElse(versionId);
        ReleaseType releaseType = v.getType();
        Instant releaseTime = v.getReleaseTime();

        String lowerId = versionId.toLowerCase(Locale.ROOT);
        String loaderType;
        if (lowerId.contains("neoforge") || lowerId.contains("neo-forge")) {
            loaderType = "NeoForge";
        } else if (lowerId.contains("forge")) {
            loaderType = "Forge";
        } else if (lowerId.contains("fabric")) {
            loaderType = "Fabric";
        } else if (lowerId.contains("optifine") || lowerId.contains("optifabric")) {
            loaderType = "OptiFine";
        } else {
            loaderType = "Otros";
        }

        return new VersionInfo(versionId, gameVersion, loaderType, releaseTime, releaseType);
    }

    private static SVG getIconForLoader(String loaderType) {
        if (loaderType == null || "Otros".equals(loaderType)) return SVG.HOME;
        switch (loaderType) {
            case "Fabric": return SVG.EXTENSION;
            case "Forge":
            case "NeoForge": return SVG.SETTINGS;
            case "OptiFine": return SVG.WB_SUNNY;
            default: return SVG.HOME;
        }
    }

    private static StackPane wrap(Node node) {
        StackPane pane = new StackPane(node);
        pane.setPadding(new Insets(0));
        return pane;
    }

    private static class VersionInfo {
        final String versionId;
        final String gameVersion;
        final String loaderType;
        final Instant releaseTime;
        final ReleaseType releaseType;

        VersionInfo(String versionId, String gameVersion, String loaderType,
                    Instant releaseTime, ReleaseType releaseType) {
            this.versionId = versionId;
            this.gameVersion = gameVersion;
            this.loaderType = loaderType;
            this.releaseTime = releaseTime;
            this.releaseType = releaseType;
        }
    }
}
