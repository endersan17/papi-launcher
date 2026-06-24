package org.jackhuang.hmcl.ui.container;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXRadioButton;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import java.io.IOException;
import org.jackhuang.hmcl.container.Container;
import org.jackhuang.hmcl.container.ContainerContentEntry;
import org.jackhuang.hmcl.container.ContainerLaunchException;
import org.jackhuang.hmcl.container.ContainerManager;
import org.jackhuang.hmcl.container.ContainerModpackImportTask;
import org.jackhuang.hmcl.container.LaunchProfile;
import org.jackhuang.hmcl.container.ResourcePackValidator;
import org.jackhuang.hmcl.download.LoaderDetector;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ClassTitle;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;


/**
 * Redesigned container management page.
 * Layout: Left sidebar | Center search+grid | Right detail panel (shown on card click).
 *
 * All container functionality (create, launch, add/remove content, etc.) is preserved.
 */
public class ContainerListPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("container.manage")));

    // Data
    private final JFXTextField searchField;
    private List<Container> allContainers = Collections.emptyList();
    private Container selectedContainer;
    private UUID selectedContainerId;
    private AdvancedListItem refreshItem;
    private boolean isRefreshing = false;
    private boolean detailPanelStale = false;

    // Layout
    private final HBox centerArea;
    private final VBox detailContent;
    private final ScrollPane detailScroll;
    private final GridPane cardGrid = new GridPane();
    private Label headerNameLabel;

    private enum SortOrder { A_Z, Z_A, RECENT }
    private enum ContainerSortOrder { A_Z, Z_A, NEWEST, OLDEST, MOST_MODS }
    private ContainerSortOrder containerSortOrder = ContainerSortOrder.A_Z;

    public ContainerListPage() {
        // --- CENTER: Search + 2-column card grid ---
        cardGrid.setHgap(10);
        cardGrid.setVgap(10);
        {
            ColumnConstraints col1 = new ColumnConstraints();
            col1.setPercentWidth(50);
            ColumnConstraints col2 = new ColumnConstraints();
            col2.setPercentWidth(50);
            cardGrid.getColumnConstraints().addAll(col1, col2);
        }

        // Search field with 100ms debounce (same pattern as ModListPageSkin)
        searchField = new JFXTextField();
        searchField.setPromptText(i18n("search"));
        searchField.getStyleClass().add("container-input-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        VBox sortIconBox = new VBox(-3);
        sortIconBox.setAlignment(Pos.CENTER);
        sortIconBox.getChildren().addAll(
                SVG.KEYBOARD_ARROW_UP.createIcon(Theme.foregroundFillBinding(), 8),
                SVG.KEYBOARD_ARROW_DOWN.createIcon(Theme.foregroundFillBinding(), 8)
        );
        JFXButton sortBtn = new JFXButton();
        sortBtn.setGraphic(wrap(sortIconBox));
        sortBtn.setStyle("-fx-background-color: -papi-surface; -fx-background-radius: 4; -fx-padding: 2 6;");
        Tooltip.install(sortBtn, new Tooltip(i18n("container.sort")));
        sortBtn.setOnAction(e -> showContainerSortMenu(sortBtn));

        HBox searchRow = new HBox(6, searchField, sortBtn);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        PauseTransition pause = new PauseTransition(Duration.millis(100));
        pause.setOnFinished(e -> applyFilter());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> pause.playFromStart());

        ScrollPane gridScroll = new ScrollPane(cardGrid);
        gridScroll.setFitToWidth(true);
        gridScroll.getStyleClass().add("container-scroll-pane");
        VBox.setVgrow(gridScroll, Priority.ALWAYS);

        VBox gridArea = new VBox(8, searchRow, gridScroll);
        gridArea.setFillWidth(true);
        HBox.setHgrow(gridArea, Priority.ALWAYS);

        // --- RIGHT: Detail panel (hidden initially, shown on container selection) ---
        detailContent = new VBox(12);
        detailContent.setFillWidth(true);
        detailContent.setPadding(new Insets(0, 16, 0, 12));

        detailScroll = new ScrollPane(detailContent);
        detailScroll.setFitToWidth(true);
        detailScroll.setPrefWidth(400);
        detailScroll.setMinWidth(350);
        detailScroll.setMaxWidth(500);
        detailScroll.getStyleClass().add("container-scroll-pane");
        detailScroll.setVisible(false);
        detailScroll.setManaged(false);

        AnchorPane detailWrapper = new AnchorPane(detailScroll);
        AnchorPane.setTopAnchor(detailScroll, 0.0);
        AnchorPane.setLeftAnchor(detailScroll, 0.0);
        AnchorPane.setRightAnchor(detailScroll, 0.0);
        AnchorPane.setBottomAnchor(detailScroll, 0.0);

        JFXButton btnCerrar = new JFXButton();
        btnCerrar.setGraphic(wrap(SVG.CLOSE.createIcon(Theme.tertiaryForegroundFillBinding(), 14)));
        btnCerrar.setStyle("-fx-background-color: transparent; -fx-padding: 4; -fx-cursor: hand;");
        btnCerrar.setOnAction(e -> clearSelection());
        btnCerrar.visibleProperty().bind(detailScroll.visibleProperty());
        AnchorPane.setTopAnchor(btnCerrar, 8.0);
        AnchorPane.setRightAnchor(btnCerrar, 8.0);
        detailWrapper.getChildren().add(btnCerrar);

        // Assemble center area: grid takes remaining space, detail panel on right
        centerArea = new HBox(0, gridArea, detailWrapper);
        centerArea.setFillHeight(true);
        setCenter(centerArea);

        // --- LEFT: Sidebar with actions (unchanged) ---
        {
            ScrollPane leftScroll = new ScrollPane();
            VBox.setVgrow(leftScroll, Priority.ALWAYS);
            leftScroll.setFitToWidth(true);

            AdvancedListBox listBox = new AdvancedListBox();
            listBox.add(new ClassTitle(i18n("container.actions")));
            listBox.addNavigationDrawerItem(i18n("container.create"), SVG.ADD_CIRCLE, this::showCreateDialog);
            listBox.addNavigationDrawerItem(i18n("button.refresh"), SVG.REFRESH, this::onRefreshClicked, item -> refreshItem = item);

            leftScroll.setContent(listBox);
            leftScroll.getStyleClass().add("gray-background");
            setLeft(leftScroll);
        }

        refreshList();
    }

    // -------------------------------------------------------------------------
    // Data refresh & filtering
    // -------------------------------------------------------------------------

    /** Fetches all containers from the manager and applies search filter. */
    private void refreshList() {
        allContainers = ContainerManager.getInstance().getContainers();
        if (selectedContainerId != null && findContainerById(allContainers, selectedContainerId) == null) {
            clearSelection();
        }
        applyFilter();
    }

    /** Handles the refresh button: validates structure, purges stale entries, recalculates sizes. */
    private void onRefreshClicked() {
        if (isRefreshing) return;
        isRefreshing = true;

        Node iconNode = refreshItem.getLeftGraphic();

        RotateTransition rotation = null;
        if (AnimationUtils.isAnimationEnabled()) {
            rotation = new RotateTransition(Duration.seconds(1), iconNode);
            rotation.setByAngle(360);
            rotation.setCycleCount(Animation.INDEFINITE);
            rotation.setInterpolator(Interpolator.LINEAR);
            rotation.play();
        }

        refreshItem.setDisable(true);

        RotateTransition finalRotation = rotation;
        final long startTime = System.currentTimeMillis();
        final int[] totalEntries = {0};
        final int[] failedContainers = {0};

        Task<?> task = Task.runAsync(() -> {
            ContainerManager mgr = ContainerManager.getInstance();
            List<Container> containers = mgr.getContainers();

            for (Container container : containers) {
                // Step 1: Validate and fix directory structure
                mgr.ensureContainerDirectories(container);

                // Step 2: Rebuild content.json from disk (purges orphaned entries)
                int result = mgr.rebuildContentFromDisk(container);
                if (result < 0) {
                    failedContainers[0]++;
                } else {
                    totalEntries[0] += result;
                }
            }
        }).whenComplete(Schedulers.javafx(), exc -> {
            if (finalRotation != null) {
                finalRotation.stop();
                iconNode.setRotate(0);
            }
            refreshItem.setDisable(false);
            isRefreshing = false;

            long duration = System.currentTimeMillis() - startTime;
            LOG.info("[Integrity] Refresh completed in " + duration + "ms"
                    + " — total entries: " + totalEntries[0]
                    + ", failed containers: " + failedContainers[0]);

            refreshList();

            if (selectedContainer != null) {
                rebuildDetailPanel();
                refreshCounterLabels();
            }

            if (exc != null) {
                LOG.warning("[Integrity] Container refresh failed", exc);
            } else if (failedContainers[0] > 0) {
                LOG.warning("[Integrity] Refresh completed with " + failedContainers[0] + " container save failures");
            }
        });
        task.start();
    }

    /** Filters containers by search text and rebuilds the card grid. Updates detail panel if needed. */
    private void applyFilter() {
        cardGrid.getChildren().clear();
        String query = searchField.getText();
        List<Container> filtered;
        if (query == null || query.isEmpty()) {
            filtered = new ArrayList<>(allContainers);
        } else {
            String lowerQuery = query.toLowerCase(Locale.ROOT);
            filtered = allContainers.stream()
                    .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(lowerQuery))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        switch (containerSortOrder) {
            case A_Z:
                filtered.sort(Comparator.comparing(Container::getName, String.CASE_INSENSITIVE_ORDER));
                break;
            case Z_A:
                filtered.sort(Comparator.comparing(Container::getName, String.CASE_INSENSITIVE_ORDER).reversed());
                break;
            case NEWEST:
                filtered.sort(Comparator.comparing(Container::getCreatedAt).reversed());
                break;
            case OLDEST:
                filtered.sort(Comparator.comparing(Container::getCreatedAt));
                break;
            case MOST_MODS:
                Map<UUID, Integer> modCounts = new HashMap<>();
                for (Container c : filtered) {
                    modCounts.put(c.getId(), (int) ContainerManager.getInstance().getContent(c).stream()
                            .filter(e -> e.getType() == ContainerContentEntry.Type.MOD).count());
                }
                filtered.sort((a, b) -> Integer.compare(modCounts.get(b.getId()), modCounts.get(a.getId())));
                break;
        }

        if (filtered.isEmpty()) {
            // Show empty state (different message depending on whether containers exist at all)
            String msg = allContainers.isEmpty() ? i18n("container.empty") : i18n("container.search.empty");
            Label emptyLabel = new Label(msg);
            emptyLabel.getStyleClass().add("container-empty-label");
            VBox emptyBox = new VBox(emptyLabel);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPrefHeight(300);
            cardGrid.add(emptyBox, 0, 0, 2, 1);
        } else {
            // Populate 2-column grid with container cards
            int col = 0, row = 0;
            for (Container container : filtered) {
                ContainerCard card = new ContainerCard(container);
                cardGrid.add(card, col, row);
                col = (col + 1) % 2;
                if (col == 0) row++;
            }
        }

        // Re-find selected container by UUID to handle stale references
        if (selectedContainerId != null) {
            Container match = findContainerById(filtered, selectedContainerId);
            if (match != null) {
                selectedContainer = match;
                highlightSelectedCard();
            } else {
                // might be in allContainers but filtered out by search
                match = findContainerById(allContainers, selectedContainerId);
                if (match == null) {
                    clearSelection();
                } else {
                    selectedContainer = match;
                }
            }
        }
    }

    private static Container findContainerById(List<Container> list, UUID id) {
        for (Container c : list) {
            if (c.getId().equals(id)) return c;
        }
        return null;
    }

    private void showContainerSortMenu(JFXButton anchor) {
        PopupMenu popup = new PopupMenu();
        popup.getContent().addAll(
                new IconedMenuItem(null, i18n("container.sort_az"), () -> {
                    containerSortOrder = ContainerSortOrder.A_Z;
                    applyFilter();
                }, null),
                new IconedMenuItem(null, i18n("container.sort_za"), () -> {
                    containerSortOrder = ContainerSortOrder.Z_A;
                    applyFilter();
                }, null),
                new IconedMenuItem(null, i18n("container.sort_newest"), () -> {
                    containerSortOrder = ContainerSortOrder.NEWEST;
                    applyFilter();
                }, null),
                new IconedMenuItem(null, i18n("container.sort_oldest"), () -> {
                    containerSortOrder = ContainerSortOrder.OLDEST;
                    applyFilter();
                }, null),
                new IconedMenuItem(null, i18n("container.sort_most_mods"), () -> {
                    containerSortOrder = ContainerSortOrder.MOST_MODS;
                    applyFilter();
                }, null)
        );
        JFXPopup jfxPopup = new JFXPopup(popup);
        jfxPopup.show(anchor, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, 0);
    }

    // -------------------------------------------------------------------------
    // Selection
    // -------------------------------------------------------------------------

    /** Selects a container, rebuilding the detail panel and showing it with a fade-in. */
    private void selectContainer(Container container) {
        selectedContainer = container;
        selectedContainerId = container.getId();
        rebuildDetailPanel();

        if (AnimationUtils.isAnimationEnabled()) {
            detailScroll.setOpacity(0);
            detailScroll.setVisible(true);
            detailScroll.setManaged(true);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), detailScroll);
            fadeIn.setToValue(1);
            fadeIn.play();
        } else {
            detailScroll.setVisible(true);
            detailScroll.setManaged(true);
        }

        highlightSelectedCard();
    }

    /** Deselects the current container and hides the detail panel with a fade-out. */
    private void clearSelection() {
        UUID prevId = selectedContainerId;

        if (prevId == null) return;

        selectedContainer = null;
        selectedContainerId = null;

        // Remove highlights from all cards
        for (Node node : cardGrid.getChildren()) {
            if (node instanceof ContainerCard) {
                ((ContainerCard) node).setSelected(false);
            }
        }

        if (AnimationUtils.isAnimationEnabled()) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(150), detailScroll);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> {
                detailContent.getChildren().clear();
                detailScroll.setVisible(false);
                detailScroll.setManaged(false);
                detailScroll.setOpacity(1);
            });
            fadeOut.play();
        } else {
            detailContent.getChildren().clear();
            detailScroll.setVisible(false);
            detailScroll.setManaged(false);
        }
    }

    /** Highlights the card matching the selected container. */
    private void highlightSelectedCard() {
        if (selectedContainerId == null) return;
        for (Node node : cardGrid.getChildren()) {
            if (node instanceof ContainerCard) {
                ContainerCard card = (ContainerCard) node;
                card.setSelected(card.container.getId().equals(selectedContainerId));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Rebuild detail panel
    // -------------------------------------------------------------------------

    /** Builds the detail panel content into a new VBox and replaces children atomically via setAll(). */
    private void rebuildDetailPanel() {
        detailPanelStale = true;
        try {
            doRebuildDetailPanel();
        } finally {
            detailPanelStale = false;
        }
    }

    private void doRebuildDetailPanel() {
        if (selectedContainer == null) {
            detailContent.getChildren().clear();
            return;
        }

        Container c = selectedContainer;

        BooleanProperty modsSearch = new SimpleBooleanProperty(false);
        BooleanProperty worldsSearch = new SimpleBooleanProperty(false);
        BooleanProperty rpSearch = new SimpleBooleanProperty(false);
        BooleanProperty spSearch = new SimpleBooleanProperty(false);

        VBox panel = new VBox(12);
        panel.setFillWidth(true);
        panel.getChildren().addAll(
                buildDetailHeader(c),
                buildDetailFooter(c),
                buildMinecraftSection(c),
                buildDetailSection(
                        i18n("container.mods"), buildModsContent(c, modsSearch),
                        () -> showAddContentDialog(c, ContainerContentEntry.Type.MOD)),
                buildDetailSection(
                        i18n("container.worlds"), buildFilterableList(c, ContainerContentEntry.Type.WORLD, SVG.LANDSCAPE, i18n("container.search_worlds"), worldsSearch),
                        () -> showAddContentDialog(c, ContainerContentEntry.Type.WORLD)),
                buildDetailSection(
                        i18n("container.resourcepacks"), buildResourcePacksContent(c, rpSearch),
                        () -> showAddContentDialog(c, ContainerContentEntry.Type.RESOURCE_PACK)),
                buildDetailSection(
                        i18n("container.shaderpacks"), buildShaderPacksContent(c, spSearch),
                        () -> showAddContentDialog(c, ContainerContentEntry.Type.SHADER_PACK)),
                buildModpackSection(c),
                buildProfileSection(c),
                buildGeneralDetailsSection(c)
        );
        detailContent.getChildren().setAll(panel);
    }

    /** Rebuilds content sections in-place when content or profile changes. */
    private void refreshContentSections() {
        if (selectedContainer == null) return;
        rebuildDetailPanel();
    }

    // -------------------------------------------------------------------------
    // Detail panel sections
    // -------------------------------------------------------------------------

    /** Header with large avatar icon and container name. */
    private VBox buildDetailHeader(Container container) {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = wrap(SVG.FOLDER.createIcon(Theme.foregroundFillBinding(), 48));
        avatar.setStyle("-fx-background-color: -papi-surface; -fx-background-radius: 8; -fx-padding: 12;");

        headerNameLabel = new Label(container.getName());
        headerNameLabel.setStyle("-fx-text-fill: -papi-primary; -fx-font-size: 20px; -fx-font-weight: bold;");
        HBox.setHgrow(headerNameLabel, Priority.ALWAYS);

        header.getChildren().addAll(avatar, headerNameLabel);
        return new VBox(header);
    }

    /** Minecraft version row: linked version + Modify Version button. */
    private VBox buildMinecraftSection(Container container) {
        VBox section = new VBox(4);
        section.setPadding(new Insets(8, 0, 0, 0));

        Label title = new Label(i18n("container.details.minecraft"));
        title.getStyleClass().add("container-section-title");

        HBox versionRow = new HBox(8);
        versionRow.setAlignment(Pos.CENTER_LEFT);
        versionRow.setPadding(new Insets(4, 0, 0, 0));

        String ver = container.getLinkedVersionId();
        Label versionLabel;
        if (ver != null) {
            String gameVer = resolveGameVersion(ver);
            String loader = resolveLoader(ver);
            if (loader != null && !loader.isEmpty()) {
                versionLabel = new Label(i18n("container.version.display", gameVer) + " \u00B7 " + loader);
            } else {
                versionLabel = new Label(i18n("container.version.display", gameVer));
            }
        } else {
            versionLabel = new Label(i18n("container.version.default"));
        }
        versionLabel.setStyle("-fx-text-fill: -papi-text-secondary; -fx-font-size: 13px;");

        JFXButton modifyBtn = createSmallButton(i18n("container.details.modify_version"), "#00AFFF",
                () -> showVersionSelection(container));

        versionRow.getChildren().addAll(versionLabel, modifyBtn);
        section.getChildren().addAll(title, versionRow);
        return section;
    }

    /** Profile section: shows current launch profile status and action buttons. */
    private VBox buildProfileSection(Container container) {
        VBox section = new VBox(4);
        section.setPadding(new Insets(8, 0, 0, 0));

        Label title = new Label(i18n("container.profile"));
        title.getStyleClass().add("container-section-title");

        LaunchProfile profile = ContainerManager.getInstance().loadLaunchProfile(container);

        VBox content = new VBox(6);
        content.setPadding(new Insets(4, 0, 0, 12));

        if (profile != null) {
            Label nameLabel = new Label(profile.getName() != null && !profile.getName().isEmpty()
                    ? profile.getName() : i18n("container.profile.create"));
            nameLabel.setStyle("-fx-text-fill: -papi-text; -fx-font-size: 15px; -fx-font-weight: bold;");

            // Chips row: horizontal flow with wrapping
            FlowPane chips = new FlowPane();
            chips.setHgap(6);
            chips.setVgap(6);

            if (profile.getMaxMemory() != null) {
                chips.getChildren().add(buildChip(createSvgPath("M21 16v-2l-8-5V3.5c0-.83-.67-1.5-1.5-1.5S10 2.67 10 3.5V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5z"), profile.getMaxMemory() + " MB"));
            }
            if (profile.getWidth() != null && profile.getHeight() != null) {
                chips.getChildren().add(buildChip(SVG.SCREENSHOT_MONITOR, profile.getWidth() + "x" + profile.getHeight()));
            }
            if (profile.getServerIp() != null && !profile.getServerIp().isEmpty()) {
                chips.getChildren().add(buildChip(SVG.PUBLIC, profile.getServerIp()));
            }
            if (profile.getFullscreen() != null && profile.getFullscreen()) {
                chips.getChildren().add(buildChip(createSvgPath("M5 5h5V3H3v7h2V5zm4 14v2h7v-2h-4.5l4.5-4.5V15h2V3h-7v2h4.5L9 13.5V19z"), i18n("container.profile.fullscreen")));
            }

            HBox actions = new HBox(6);
            actions.getChildren().addAll(
                    createSmallButton(i18n("container.profile.edit"), "-papi-primary", () -> {
                        CreateEditProfileDialog dialog = new CreateEditProfileDialog(container, profile, () -> {
                            rebuildDetailPanel();
                            refreshList();
                        });
                        Controllers.dialog(dialog);
                    }),
                    createSmallButton(i18n("container.profile.delete"), "-papi-error", () -> {
                        String profileName = profile.getName() != null && !profile.getName().isEmpty() ? profile.getName() : i18n("container.profile.create");
                        Controllers.confirm(i18n("container.profile.delete.confirm", container.getName(), profileName),
                                i18n("message.warning"), MessageDialogPane.MessageType.WARNING,
                                () -> {
                                    ContainerManager.getInstance().deleteLaunchProfile(container);
                                    rebuildDetailPanel();
                                    refreshCounterLabels();
                                    refreshList();
                                }, null);
                    })
            );
            content.getChildren().addAll(nameLabel, chips, actions);
        } else {
            Label noneLabel = new Label(i18n("container.profile.none"));
            noneLabel.setStyle("-fx-text-fill: -papi-text-tertiary; -fx-font-size: 12px;");

            JFXButton createBtn = createSmallButton(i18n("container.profile.create"), "-papi-primary", () -> {
                CreateEditProfileDialog dialog = new CreateEditProfileDialog(container, null, () -> {
                            rebuildDetailPanel();
                            refreshList();
                        });
                Controllers.dialog(dialog);
            });
            content.getChildren().addAll(noneLabel, createBtn);
        }

        section.getChildren().addAll(title, content);
        return section;
    }

    /** Creates a chip/badge with an SVG icon and label text. */
    private HBox buildChip(SVG svg, String text) {
        return buildChip(svg.createIcon(Theme.secondaryForegroundFillBinding(), 14), text);
    }

    /** Creates a chip/badge with an inline SVG path icon and label text. */
    private HBox buildChip(Node icon, String text) {
        HBox chip = new HBox(5);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("container-chip");

        StackPane iconWrap = new StackPane(icon);
        Label label = new Label(text);
        chip.getChildren().addAll(iconWrap, label);
        return chip;
    }

    /** Creates a Node from an inline SVG path string with dynamic fill. */
    private static Node createSvgPath(String pathData) {
        SVGPath p = new SVGPath();
        p.setContent(pathData);
        p.fillProperty().bind(Theme.secondaryForegroundFillBinding());
        StackPane pane = new StackPane(p);
        pane.setMinSize(14, 14);
        pane.setMaxSize(14, 14);
        p.setScaleX(14.0 / 24);
        p.setScaleY(14.0 / 24);
        return pane;
    }

    /** Wraps a section with title, optional top-right action button, and content body. */
    private VBox buildDetailSection(String title, VBox content, Runnable addAction) {
        return buildDetailSection(title, content, addAction, null);
    }

    private VBox buildDetailSection(String title, VBox content, Runnable addAction, Runnable downloadAction) {
        VBox section = new VBox(4);
        section.setPadding(new Insets(8, 0, 0, 0));

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("container-section-title");
        header.getChildren().add(titleLabel);

        if (downloadAction != null) {
            header.getChildren().add(createSmallButton(i18n("container.download"), "#00AFFF", downloadAction));
        }

        if (addAction != null) {
            header.getChildren().add(createSmallButton(i18n("container.add"), "-papi-primary", addAction));
        }

        section.getChildren().addAll(header, content);
        return section;
    }

    /** Builds the mods section with a filterable, sortable vertical list. */
    private VBox buildModsContent(Container container, BooleanProperty searchVisible) {
        return buildFilterableList(container, ContainerContentEntry.Type.MOD, SVG.EXTENSION, i18n("container.search_mods"), searchVisible);
    }

    /** Builds the worlds section — delegated to buildFilterableList for consistent vertical list format. */
    private VBox buildWorldsContent(Container container, BooleanProperty searchVisible) {
        return buildFilterableList(container, ContainerContentEntry.Type.WORLD, SVG.LANDSCAPE, i18n("container.search_worlds"), searchVisible);
    }

    /** Builds the resource packs section with a filterable, sortable vertical list. */
    private VBox buildResourcePacksContent(Container container, BooleanProperty searchVisible) {
        return buildFilterableList(container, ContainerContentEntry.Type.RESOURCE_PACK, SVG.ARCHIVE, i18n("container.search_resourcepacks"), searchVisible);
    }

    /** Builds the shader packs section with a filterable, sortable vertical list. */
    private VBox buildShaderPacksContent(Container container, BooleanProperty searchVisible) {
        return buildFilterableList(container, ContainerContentEntry.Type.SHADER_PACK, SVG.ARCHIVE, i18n("container.search_shaderpacks"), searchVisible);
    }

    /**
     * Builds a filterable + sortable vertical list for mods/worlds/shaders/resourcepacks.
     * Contains a search bar with real-time filtering and a sort popup (A-Z, Z-A, Recent).
     */
    private VBox buildFilterableList(Container container, ContainerContentEntry.Type type, SVG iconType, String searchPrompt, BooleanProperty searchVisible) {
        VBox root = new VBox(6);
        root.setPadding(new Insets(4, 0, 0, 12));

        List<ContainerContentEntry> allEntries = ContainerManager.getInstance().getContent(container).stream()
                .filter(e -> e.getType() == type)
                .collect(Collectors.toList());

        // --- Unified search button (visible when closed): text + lupa ---
        JFXButton unifiedSearchBtn = new JFXButton(searchPrompt);
        unifiedSearchBtn.setGraphic(wrap(SVG.SEARCH.createIcon(Color.WHITE, 12)));
        unifiedSearchBtn.setContentDisplay(ContentDisplay.RIGHT);
        unifiedSearchBtn.getStyleClass().add("container-filter-btn");
        unifiedSearchBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(unifiedSearchBtn, Priority.ALWAYS);
        unifiedSearchBtn.setOnAction(e -> searchVisible.set(true));

        // --- Search bar (visible when open) ---
        HBox searchBar = new HBox(6);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setVisible(false);
        searchBar.setManaged(false);

        JFXTextField searchField = new JFXTextField();
        searchField.setPromptText(searchPrompt);
        searchField.getStyleClass().add("container-search-field");
        searchField.setPrefWidth(160);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        // Sort button with stacked arrows ↕
        VBox sortIconBox = new VBox(-3);
        sortIconBox.setAlignment(Pos.CENTER);
        sortIconBox.getChildren().addAll(
                SVG.KEYBOARD_ARROW_UP.createIcon(Theme.foregroundFillBinding(), 8),
                SVG.KEYBOARD_ARROW_DOWN.createIcon(Theme.foregroundFillBinding(), 8)
        );
        JFXButton sortBtn = new JFXButton();
        sortBtn.setGraphic(wrap(sortIconBox));
        sortBtn.getStyleClass().add("container-sort-btn");
        Tooltip.install(sortBtn, new Tooltip(i18n("container.sort")));

        searchBar.getChildren().addAll(searchField, sortBtn);

        // --- X close button (visible when open) ---
        JFXButton closeSearchBtn = new JFXButton();
        closeSearchBtn.setGraphic(wrap(SVG.CLOSE.createIcon(Color.WHITE, 12)));
        closeSearchBtn.getStyleClass().add("container-filter-btn");
        closeSearchBtn.setVisible(false);
        closeSearchBtn.setManaged(false);
        closeSearchBtn.setOnAction(e -> searchVisible.set(false));

        Animation[] searchAnim = {null};
        Animation[] closeAnim = {null};
        searchVisible.addListener((obs, oldVal, newVal) -> {
            // Cancel running animations
            if (searchAnim[0] != null) {
                searchAnim[0].stop();
                searchAnim[0] = null;
            }
            if (closeAnim[0] != null) {
                closeAnim[0].stop();
                closeAnim[0] = null;
            }

            if (detailPanelStale || !AnimationUtils.isAnimationEnabled()) {
                unifiedSearchBtn.setVisible(!newVal);
                unifiedSearchBtn.setManaged(!newVal);
                searchBar.setVisible(newVal);
                searchBar.setManaged(newVal);
                closeSearchBtn.setVisible(newVal);
                closeSearchBtn.setManaged(newVal);
                return;
            }

            if (newVal) {
                // Hide unified button
                unifiedSearchBtn.setVisible(false);
                unifiedSearchBtn.setManaged(false);

                // Show + animate close X button (fade+scale in)
                closeSearchBtn.setScaleX(0);
                closeSearchBtn.setScaleY(0);
                closeSearchBtn.setOpacity(0);
                closeSearchBtn.setVisible(true);
                closeSearchBtn.setManaged(true);

                FadeTransition xFade = new FadeTransition(Duration.millis(100), closeSearchBtn);
                xFade.setToValue(1);
                ScaleTransition xScale = new ScaleTransition(Duration.millis(100), closeSearchBtn);
                xScale.setToX(1);
                xScale.setToY(1);
                closeAnim[0] = new ParallelTransition(xFade, xScale);
                closeAnim[0].play();

                // Animate searchBar in
                searchBar.setScaleX(0);
                searchBar.setOpacity(0);
                searchBar.setVisible(true);
                searchBar.setManaged(true);

                FadeTransition fade = new FadeTransition(Duration.millis(175), searchBar);
                fade.setToValue(1);
                ScaleTransition scale = new ScaleTransition(Duration.millis(175), searchBar);
                scale.setToX(1);

                searchAnim[0] = new ParallelTransition(fade, scale);
                searchAnim[0].play();
            } else {
                // Animate close X button out (fade+scale out)
                FadeTransition xFade = new FadeTransition(Duration.millis(100), closeSearchBtn);
                xFade.setToValue(0);
                ScaleTransition xScale = new ScaleTransition(Duration.millis(100), closeSearchBtn);
                xScale.setToX(0);
                xScale.setToY(0);
                closeAnim[0] = new ParallelTransition(xFade, xScale);
                closeAnim[0].setOnFinished(e -> {
                    if (detailPanelStale) return;
                    closeSearchBtn.setVisible(false);
                    closeSearchBtn.setManaged(false);
                });
                closeAnim[0].play();

                // Animate searchBar out
                FadeTransition fade = new FadeTransition(Duration.millis(175), searchBar);
                fade.setToValue(0);
                ScaleTransition scale = new ScaleTransition(Duration.millis(175), searchBar);
                scale.setToX(0);

                searchAnim[0] = new ParallelTransition(fade, scale);
                searchAnim[0].setOnFinished(e -> {
                    if (detailPanelStale) return;
                    searchBar.setVisible(false);
                    searchBar.setManaged(false);

                    unifiedSearchBtn.setVisible(true);
                    unifiedSearchBtn.setManaged(true);
                });
                searchAnim[0].play();
            }
        });

        HBox searchRow = new HBox(6, unifiedSearchBtn, searchBar, closeSearchBtn);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchBar, Priority.ALWAYS);

        // --- Items container (rebuilt on filter/sort change) ---
        VBox itemsContainer = new VBox(4);

        SortOrder[] currentSort = { SortOrder.A_Z };

        Runnable updateItems = () -> {
            itemsContainer.getChildren().clear();

            String query = searchField.getText();
            List<ContainerContentEntry> filtered = allEntries.stream()
                    .filter(e -> {
                        if (query == null || query.isEmpty()) return true;
                        return e.getFileName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
                    })
                    .sorted((a, b) -> {
                        switch (currentSort[0]) {
                            case A_Z: return a.getFileName().compareToIgnoreCase(b.getFileName());
                            case Z_A: return b.getFileName().compareToIgnoreCase(a.getFileName());
                            case RECENT: return b.getAddedAt().compareTo(a.getAddedAt());
                            default: return 0;
                        }
                    })
                    .collect(Collectors.toList());

            if (filtered.isEmpty()) {
                itemsContainer.getChildren().add(emptyContentLabel());
            } else {
                for (ContainerContentEntry entry : filtered) {
                    itemsContainer.getChildren().add(buildContentRow(container, entry, iconType));
                }
            }
        };

        // Debounced search listener
        PauseTransition searchPause = new PauseTransition(Duration.millis(100));
        searchPause.setOnFinished(e -> updateItems.run());
        searchField.textProperty().addListener((obs, old, val) -> searchPause.playFromStart());

        // Sort popup menu
        sortBtn.setOnAction(e -> {
            PopupMenu popup = new PopupMenu();
            popup.getContent().addAll(
                    new IconedMenuItem(null, i18n("container.sort_az"), () -> { currentSort[0] = SortOrder.A_Z; updateItems.run(); }, null),
                    new IconedMenuItem(null, i18n("container.sort_za"), () -> { currentSort[0] = SortOrder.Z_A; updateItems.run(); }, null),
                    new IconedMenuItem(null, i18n("container.sort_recent"), () -> { currentSort[0] = SortOrder.RECENT; updateItems.run(); }, null)
            );
            JFXPopup jfxPopup = new JFXPopup(popup);
            jfxPopup.show(sortBtn, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, 0);
        });

        updateItems.run();

        root.getChildren().addAll(searchRow, itemsContainer);
        return root;
    }

    /** Builds a single row for the vertical content list: checkbox + icon + name/info + delete button. */
    private HBox buildContentRow(Container container, ContainerContentEntry entry, SVG iconType) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("container-content-row");
        row.setCursor(Cursor.HAND);

        StackPane iconNode = wrap(iconType.createIcon(Theme.foregroundFillBinding(), 20));

        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLabel = new Label(entry.getFileName());
        nameLabel.setStyle("-fx-text-fill: -papi-text-secondary; -fx-font-size: 12px;");

        Label dateLabel = new Label(DateTimeFormatter.ofPattern(i18n("container.date_format"))
                .withZone(ZoneId.systemDefault()).format(entry.getAddedAt()));
        dateLabel.setStyle("-fx-text-fill: -papi-text-tertiary; -fx-font-size: 10px;");

        info.getChildren().addAll(nameLabel, dateLabel);

        JFXButton deleteBtn = new JFXButton();
        deleteBtn.setGraphic(wrap(SVG.DELETE.createIcon(Color.WHITE, 14)));
        deleteBtn.setStyle("-fx-background-color: -papi-error; -fx-background-radius: 4; -fx-padding: 4 6; -fx-cursor: hand;");
        deleteBtn.setOnAction(e -> {
            ContainerManager.getInstance().removeContent(container, entry);
            rebuildDetailPanel();
            refreshCounterLabels();
            refreshList();
        });

        row.getChildren().addAll(iconNode, info, deleteBtn);

        CompletableFuture.supplyAsync(() -> {
            boolean broken = entry.isBroken();
            ResourcePackValidator.ValidationResult vr = null;
            if (!broken && entry.getType() == ContainerContentEntry.Type.RESOURCE_PACK) {
                Path sourcePath = Path.of(entry.getSourcePath());
                if (Files.exists(sourcePath)) {
                    String gameVersion = resolveGameVersion(container.getLinkedVersionId());
                    vr = ResourcePackValidator.validate(sourcePath, gameVersion);
                }
            }
            return new AsyncContentRowResult(broken, vr);
        }).thenAccept(result -> javafx.application.Platform.runLater(() -> {
            if (row.getScene() == null) return;

            if (result.broken) {
                nameLabel.setText(entry.getFileName() + " (" + i18n("container.broken") + ")");
                nameLabel.setStyle("-fx-text-fill: -papi-error; -fx-font-size: 12px;");
            }

            if (result.validationResult != null && !result.validationResult.isOk()) {
                Label warningIcon = new Label("\u26A0\uFE0F");
                warningIcon.setStyle("-fx-text-fill: -papi-warning; -fx-font-size: 14px;");
                String warnText = i18n(result.validationResult.getMessageKey(), result.validationResult.getMessageArgs());
                Tooltip tip;
                if (result.validationResult.isWarning()) {
                    tip = new Tooltip(i18n("container.resource_pack.validation.warning_tooltip", warnText));
                } else {
                    tip = new Tooltip(i18n("container.broken") + ": " + warnText);
                }
                tip.setWrapText(true);
                tip.setMaxWidth(320);
                Tooltip.install(warningIcon, tip);
                int insertPos = Math.max(0, row.getChildren().size() - 1);
                row.getChildren().add(insertPos, warningIcon);
            }
        }));

        return row;
    }

    private static final class AsyncContentRowResult {
        final boolean broken;
        final ResourcePackValidator.ValidationResult validationResult;

        AsyncContentRowResult(boolean broken, ResourcePackValidator.ValidationResult validationResult) {
            this.broken = broken;
            this.validationResult = validationResult;
        }
    }

        private void showInstalledPicker(Container container, ContainerContentEntry.Type type) {
            InstalledContentPickerDialog dialog = new InstalledContentPickerDialog(container, type, () -> {
                rebuildDetailPanel();
                refreshCounterLabels();
                refreshList();
            });
            Controllers.dialog(dialog);
        }

        /** General details: creation date, editable name and description. */
    private VBox buildGeneralDetailsSection(Container container) {
        VBox section = new VBox(4);
        section.setPadding(new Insets(8, 0, 0, 0));

        Label title = new Label(i18n("container.details.general"));
        title.getStyleClass().add("container-section-title");

        Label dateLabel = new Label(i18n("container.details.created") + ": "
                + DateTimeFormatter.ofPattern(i18n("container.date_format"))
                        .withZone(ZoneId.systemDefault())
                        .format(container.getCreatedAt()));
        dateLabel.getStyleClass().add("container-detail-label");
        dateLabel.setPadding(new Insets(4, 0, 0, 12));

        // --- Editable name ---
        VBox nameSection = new VBox(2);
        nameSection.setPadding(new Insets(4, 0, 0, 12));
        Label nameTitle = new Label(i18n("container.create.name") + ":");
        nameTitle.getStyleClass().add("container-detail-label");

        String[] previousName = {container.getName()};
        JFXTextField nameField = new JFXTextField(container.getName());
        nameField.getStyleClass().add("container-input-field");
        nameField.setPromptText(i18n("container.create.name"));

        Label nameWarning = new Label(i18n("input.not_empty"));
        nameWarning.getStyleClass().add("container-name-warning");
        nameWarning.setVisible(false);

        Runnable saveName = () -> {
            String text = nameField.getText().trim();
            if (text.isEmpty()) {
                nameField.setText(previousName[0]);
                nameWarning.setVisible(true);
                PauseTransition hideWarning = new PauseTransition(Duration.seconds(2));
                hideWarning.setOnFinished(ev -> nameWarning.setVisible(false));
                hideWarning.play();
                return;
            }
            if (!text.equals(previousName[0])) {
                previousName[0] = text;
                ContainerManager.getInstance().updateContainerName(selectedContainer, text);
                selectedContainer = ContainerManager.getInstance().getContainer(selectedContainer.getId()).orElse(selectedContainer);
                if (headerNameLabel != null) headerNameLabel.setText(text);
                refreshList();
            }
        };

        nameField.setOnAction(e -> saveName.run());
        nameField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) saveName.run();
        });

        nameSection.getChildren().addAll(nameTitle, nameField, nameWarning);

        // --- Editable description ---
        VBox descSection = new VBox(2);
        descSection.setPadding(new Insets(4, 0, 0, 12));
        Label descTitle = new Label(i18n("container.details.description") + ":");
        descTitle.getStyleClass().add("container-detail-label");

        String[] previousDesc = {container.getDescription() != null ? container.getDescription() : ""};
        TextArea descArea = new TextArea();
        descArea.setText(container.getDescription() != null ? container.getDescription() : "");
        descArea.setPromptText(i18n("container.create.description"));
        descArea.getStyleClass().add("container-input-field");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);

        descArea.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                String text = descArea.getText();
                String trimmed = text.trim();
                if (!trimmed.equals(previousDesc[0])) {
                    previousDesc[0] = trimmed;
                    ContainerManager.getInstance().updateContainerDescription(selectedContainer, trimmed.isEmpty() ? null : trimmed);
                    selectedContainer = ContainerManager.getInstance().getContainer(selectedContainer.getId()).orElse(selectedContainer);
                }
            }
        });

        descSection.getChildren().addAll(descTitle, descArea);

        section.getChildren().addAll(title, dateLabel, nameSection, descSection);
        return section;
    }

    /** Footer with Launch, Open Folder, Delete action buttons. */
    private VBox buildDetailFooter(Container container) {
        VBox footer = new VBox(8);
        footer.setPadding(new Insets(8, 0, 0, 0));

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER);

        JFXButton launchBtn = new JFXButton(i18n("container.launch"));
        launchBtn.getStyleClass().add("container-launch-btn");
        launchBtn.setGraphic(wrap(SVG.ROCKET_LAUNCH.createIcon(Color.BLACK, 16)));
        launchBtn.setOnAction(e -> {
            launchBtn.setDisable(true);
            launchBtn.setText(i18n("container.launching"));
            try {
                ContainerManager.getInstance().launchContainer(container.getId(),
                        () -> {
                            launchBtn.setDisable(false);
                            launchBtn.setText(i18n("container.launch"));
                        },
                        (failures, continueAction) ->
                            showDeployErrorDialog(failures, continueAction)
                );
            } catch (ContainerLaunchException ex) {
                Controllers.dialog(ex.getMessage(),
                        i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                launchBtn.setDisable(false);
                launchBtn.setText(i18n("container.launch"));
            }
        });

        JFXButton openFolderBtn = new JFXButton(i18n("container.open_folder"));
        openFolderBtn.getStyleClass().add("container-open-btn");
        openFolderBtn.setGraphic(wrap(SVG.FOLDER_OPEN.createIcon(Color.BLACK, 16)));
        openFolderBtn.setOnAction(e -> ContainerManager.getInstance().openContainerFolder(container.getId()));

        JFXButton deleteBtn = new JFXButton(i18n("button.delete"));
        deleteBtn.getStyleClass().add("container-delete-btn");
        deleteBtn.setGraphic(wrap(SVG.DELETE.createIcon(Color.WHITE, 16)));
        deleteBtn.setOnAction(e -> {
            Controllers.confirm(i18n("container.delete.confirm", container.getName()),
                    i18n("message.warning"), MessageDialogPane.MessageType.WARNING,
                    () -> {
                        ContainerManager.getInstance().deleteContainer(container.getId());
                        refreshList();
                    }, null);
        });

        actions.getChildren().addAll(launchBtn, openFolderBtn, deleteBtn);
        footer.getChildren().add(actions);
        return footer;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Creates a small inline button with given text color and action. */
    private static JFXButton createSmallButton(String text, String textColor, Runnable action) {
        JFXButton btn = new JFXButton(text);
        btn.getStyleClass().add("container-small-btn");
        btn.setStyle("-fx-text-fill: " + textColor + ";");
        btn.setOnAction(e -> action.run());
        return btn;
    }
    /** Wraps a Node in a StackPane for use as icon container. */
    private static StackPane wrap(Node node) {
        StackPane pane = new StackPane(node);
        pane.setPadding(new Insets(0));
        return pane;
    }

    /** Returns a styled "empty" label for content sections. */
    private static Label emptyContentLabel() {
        Label label = new Label(i18n("container.no_content"));
        label.getStyleClass().add("container-empty-small");
        return label;
    }

    // -------------------------------------------------------------------------
    // Version & loader resolution
    // -------------------------------------------------------------------------

    /** Resolves the Minecraft game version string from a version ID. */
    private String resolveGameVersion(String versionId) {
        try {
            Profile profile = Profiles.getSelectedProfile();
            if (profile == null) return versionId;
            HMCLGameRepository repo = profile.getRepository();
            if (repo == null) return versionId;
            return repo.getGameVersion(versionId).orElse(versionId);
        } catch (Exception e) {
            return versionId;
        }
    }

    /** Detects mod loader name + version (Forge, Fabric, etc.) from a version ID. */
    private String resolveLoader(String versionId) {
        try {
            Profile profile = Profiles.getSelectedProfile();
            if (profile == null) return null;
            HMCLGameRepository repo = profile.getRepository();
            if (repo == null) return null;
            LoaderDetector.DetectedLoader dl = LoaderDetector.detect(repo, versionId);
            if (dl.isVanilla()) return null;
            String result = dl.getType();
            if (dl.getVersion() != null && !dl.getVersion().isEmpty()) {
                result += " " + dl.getVersion();
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Dialogs (unchanged logic from previous implementation)
    // -------------------------------------------------------------------------

    private void showCreateDialog() {
        CreateContainerDialog dialog = new CreateContainerDialog(this::refreshList);
        Controllers.dialog(dialog);
    }

    private void showVersionSelection(Container container) {
        Profile profile = Profiles.getSelectedProfile();
        if (profile == null) return;
        VersionPickerDialog dialog = new VersionPickerDialog(container, () -> {
            selectedContainer = ContainerManager.getInstance().getContainer(container.getId()).orElse(container);
            rebuildDetailPanel();
            refreshList();
        });
        Controllers.dialog(dialog);
    }

    private void showAddContentDialog(Container container, ContainerContentEntry.Type type) {
        showInstalledPicker(container, type);
    }

    // -------------------------------------------------------------------------
    // Modpack section in detail panel
    // -------------------------------------------------------------------------

    private VBox buildModpackSection(Container container) {
        VBox section = new VBox(4);
        section.setPadding(new Insets(8, 0, 0, 0));

        Label title = new Label(i18n("container.modpacks"));
        title.getStyleClass().add("container-section-title");

        HBox buttons = new HBox(8);
        buttons.setPadding(new Insets(4, 0, 0, 12));

        JFXButton importBtn = new JFXButton(i18n("container.modpack.import"));
        importBtn.setStyle("-fx-background-color: -papi-surface; -fx-text-fill: -papi-primary; -fx-background-radius: 4; -fx-padding: 6 16; -fx-font-size: 12px;");
        importBtn.setOnAction(e -> showImportModpackDialog());

        JFXButton exportBtn = new JFXButton(i18n("container.modpack.export"));
        exportBtn.setStyle("-fx-background-color: -papi-surface; -fx-text-fill: -papi-primary; -fx-background-radius: 4; -fx-padding: 6 16; -fx-font-size: 12px;");
        exportBtn.setOnAction(e -> showExportModpackDialog(container));

        buttons.getChildren().addAll(importBtn, exportBtn);
        section.getChildren().addAll(title, buttons);
        return section;
    }

    // -------------------------------------------------------------------------
    // Modpack import / export dialogs
    // -------------------------------------------------------------------------

    private void showImportModpackDialog() {
        if (selectedContainer == null) return;

        if (selectedContainer.getLinkedVersionId() == null) {
            Controllers.dialog(i18n("container.modpack.import.no_version"),
                    i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            return;
        }

        JFXDialogLayout layout = new JFXDialogLayout();
        layout.setStyle("-fx-background-color: -papi-surface-alt;");
        Label heading = new Label(i18n("container.modpack.import.title"));
        heading.setStyle("-fx-text-fill: -papi-text; -fx-font-size: 16px; -fx-font-weight: bold;");
        layout.setHeading(heading);

        ToggleGroup sourceGroup = new ToggleGroup();

        JFXRadioButton fileRadio = new JFXRadioButton(i18n("container.modpack.import.from_file"));
        fileRadio.setToggleGroup(sourceGroup);
        fileRadio.setSelected(true);
        fileRadio.setStyle("-fx-text-fill: -papi-text-secondary; -fx-font-size: 13px;");

        JFXRadioButton launcherRadio = new JFXRadioButton(i18n("container.modpack.import.from_launcher"));
        launcherRadio.setToggleGroup(sourceGroup);
        launcherRadio.setStyle("-fx-text-fill: -papi-text-secondary; -fx-font-size: 13px;");

        HBox radioRow = new HBox(16, fileRadio, launcherRadio);
        radioRow.setPadding(new Insets(8, 0, 8, 0));

        // File chooser label (shown when file mode is active)
        Label fileLabel = new Label(i18n("container.modpack.import.file"));
        fileLabel.setStyle("-fx-text-fill: -papi-text-tertiary; -fx-font-size: 12px;");

        // Installed modpacks list (shown when launcher mode is active)
        VBox installedBox = new VBox(4);
        installedBox.setVisible(false);
        installedBox.setManaged(false);

        Label selectLabel = new Label(i18n("container.modpack.import.select_installed"));
        selectLabel.setStyle("-fx-text-fill: -papi-text-secondary; -fx-font-size: 12px;");

        List<ContainerManager.InstalledModpackInfo> modpacks = ContainerManager.getInstance().getInstalledModpacks();

        VBox listContainer = new VBox(4);
        listContainer.setPrefHeight(200);
        listContainer.setMaxHeight(200);

        if (modpacks.isEmpty()) {
            Label emptyLabel = new Label(i18n("container.modpack.import.no_installed"));
            emptyLabel.setStyle("-fx-text-fill: -papi-text-tertiary; -fx-font-size: 13px;");
            emptyLabel.setPadding(new Insets(16, 0, 0, 0));
            listContainer.getChildren().add(emptyLabel);
        } else {
            ScrollPane listScroll = new ScrollPane(listContainer);
            listScroll.setFitToWidth(true);
            listScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
            VBox.setVgrow(listScroll, javafx.scene.layout.Priority.ALWAYS);

            for (ContainerManager.InstalledModpackInfo mp : modpacks) {
                VBox card = new VBox(3);
                card.setStyle("-fx-background-color: -papi-surface; -fx-background-radius: 6; -fx-padding: 8 12; -fx-border-color: transparent; -fx-border-width: 1.5; -fx-border-radius: 6;");
                card.setCursor(Cursor.HAND);

                Label nameLabel2 = new Label(mp.getModpackName());
                nameLabel2.setStyle("-fx-text-fill: -papi-text-secondary; -fx-font-size: 13px; -fx-font-weight: bold;");

                HBox detailsRow = new HBox(8);
                Label mcLabel = new Label("MC " + mp.getMcVersion());
                mcLabel.setStyle("-fx-text-fill: -papi-text-tertiary; -fx-font-size: 11px;");

                if (mp.getLoaderType() != null && !mp.getLoaderType().isEmpty()) {
                    Label loaderLabel2 = new Label(mp.getLoaderType());
                    loaderLabel2.setStyle("-fx-text-fill: -papi-primary; -fx-font-size: 11px;");
                    detailsRow.getChildren().addAll(mcLabel, loaderLabel2);
                } else {
                    detailsRow.getChildren().add(mcLabel);
                }

                if (mp.getModpackVersion() != null && !mp.getModpackVersion().isEmpty()) {
                    Label verLabel = new Label("v" + mp.getModpackVersion());
                    verLabel.setStyle("-fx-text-fill: -papi-text-tertiary; -fx-font-size: 10px;");
                    detailsRow.getChildren().add(verLabel);
                }

                card.getChildren().addAll(nameLabel2, detailsRow);

                final ContainerManager.InstalledModpackInfo selectedMp = mp;
                card.setOnMouseClicked(e -> {
                    for (Node child : listContainer.getChildren()) {
                        if (child instanceof VBox) {
                            child.setStyle("-fx-background-color: -papi-surface; -fx-background-radius: 6; -fx-padding: 8 12; -fx-border-color: transparent; -fx-border-width: 1.5; -fx-border-radius: 6;");
                        }
                    }
                    card.setStyle("-fx-background-color: -papi-primary-alpha-015; -fx-background-radius: 6; -fx-padding: 8 12; -fx-border-color: -papi-primary; -fx-border-width: 1.5; -fx-border-radius: 6;");
                    card.getProperties().put("selectedModpack", selectedMp);
                });
                // Deselect any previous
                for (Node child : listContainer.getChildren()) {
                    if (child instanceof VBox) {
                        child.getProperties().remove("selectedModpack");
                    }
                }

                listContainer.getChildren().add(card);
            }
        }

        installedBox.getChildren().addAll(selectLabel, listContainer);

        VBox body = new VBox(4);
        body.getChildren().addAll(radioRow, fileLabel, installedBox);
        layout.setBody(body);

        JFXButton cancelBtn = new JFXButton(i18n("button.cancel"));
        cancelBtn.setStyle("-fx-background-color: -papi-surface; -fx-text-fill: -papi-text-secondary; -fx-background-radius: 4; -fx-padding: 6 16;");
        JFXButton importBtn = new JFXButton(i18n("container.modpack.import"));
        importBtn.setStyle("-fx-background-color: -papi-primary; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 6 16;");

        layout.setActions(cancelBtn, importBtn);
        Controllers.dialog(layout);

        // Toggle visibility when radio changes
        sourceGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            boolean isFile = selected == fileRadio;
            fileLabel.setVisible(isFile);
            fileLabel.setManaged(isFile);
            installedBox.setVisible(!isFile);
            installedBox.setManaged(!isFile);
        });

        cancelBtn.setOnAction(e -> layout.fireEvent(new DialogCloseEvent()));

        importBtn.setOnAction(e -> {
            boolean isFile = sourceGroup.getSelectedToggle() == fileRadio;

            if (isFile) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle(i18n("container.modpack.import.file"));
                chooser.getExtensionFilters().setAll(
                        new FileChooser.ExtensionFilter(i18n("container.modpack.import.file"), "*.zip", "*.mrpack")
                );
                File selected = chooser.showOpenDialog(Controllers.getStage());
                if (selected == null) return;

                layout.fireEvent(new DialogCloseEvent());

                try {
                    ContainerModpackImportTask importInnerTask = ContainerManager.getInstance()
                            .importModpack(selectedContainer, selected.toPath());
                    Task<?> importTask = Task.runAsync(() -> {
                        try {
                            importInnerTask.run();
                        } catch (Exception exc) {
                            throw new RuntimeException(exc);
                        }
                    }).whenComplete(Schedulers.javafx(), (v, exc) -> {
                        if (exc != null) {
                            LOG.warning("Modpack import failed", exc);
                            Controllers.dialog(i18n("container.modpack.import.error") + ": " + exc.getMessage(),
                                    i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                        } else {
                            rebuildDetailPanel();
                            refreshCounterLabels();
                            refreshList();
                            List<String> rejected = importInnerTask.getRejectedResourcePacks();
                            if (!rejected.isEmpty()) {
                                showImportRejectionDialog(rejected);
                            } else {
                                Controllers.dialog(i18n("container.modpack.import.success"),
                                        i18n("message.info"), MessageDialogPane.MessageType.INFO);
                            }
                        }
                    });
                    importTask.start();
                } catch (IOException ioe) {
                    LOG.warning("Modpack import failed", ioe);
                    Controllers.dialog(i18n("container.modpack.import.error") + ": " + ioe.getMessage(),
                            i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                }
            } else {
                ContainerManager.InstalledModpackInfo[] selectedMpBox = {null};
                for (Node child : listContainer.getChildren()) {
                    if (child instanceof VBox && child.getProperties().get("selectedModpack") != null) {
                        selectedMpBox[0] = (ContainerManager.InstalledModpackInfo) child.getProperties().get("selectedModpack");
                        break;
                    }
                }
                if (selectedMpBox[0] == null) {
                    Controllers.dialog(i18n("container.modpack.import.select_installed"),
                            i18n("message.warning"), MessageDialogPane.MessageType.WARNING);
                    return;
                }

                final ContainerManager.InstalledModpackInfo selectedMp = selectedMpBox[0];
                layout.fireEvent(new DialogCloseEvent());

                try {
                    ContainerModpackImportTask importInnerTask = ContainerManager.getInstance()
                            .importModpackFromVersion(selectedContainer, selectedMp.getVersionId());
                    Task<?> importTask = Task.runAsync(() -> {
                        try {
                            importInnerTask.run();
                        } catch (Exception exc) {
                            throw new RuntimeException(exc);
                        }
                    }).whenComplete(Schedulers.javafx(), (v, exc) -> {
                        if (exc != null) {
                            LOG.warning("Modpack import failed", exc);
                            Controllers.dialog(i18n("container.modpack.import.error") + ": " + exc.getMessage(),
                                    i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                        } else {
                            rebuildDetailPanel();
                            refreshCounterLabels();
                            refreshList();
                            List<String> rejected = importInnerTask.getRejectedResourcePacks();
                            if (!rejected.isEmpty()) {
                                showImportRejectionDialog(rejected);
                            } else {
                                Controllers.dialog(i18n("container.modpack.import.success"),
                                        i18n("message.info"), MessageDialogPane.MessageType.INFO);
                            }
                        }
                    });
                    importTask.start();
                } catch (IOException ioe) {
                    LOG.warning("Modpack import failed", ioe);
                    Controllers.dialog(i18n("container.modpack.import.error") + ": " + ioe.getMessage(),
                            i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                }
            }
        });
    }

    private void showExportModpackDialog(Container container) {
        JFXDialogLayout layout = new JFXDialogLayout();
        layout.setStyle("-fx-background-color: -papi-surface-alt;");
        Label heading = new Label(i18n("container.modpack.export.title"));
        heading.setStyle("-fx-text-fill: -papi-text; -fx-font-size: 16px; -fx-font-weight: bold;");
        layout.setHeading(heading);

        VBox form = new VBox(8);

        Label nameLabel = new Label(i18n("container.modpack.export.name"));
        nameLabel.setStyle("-fx-text-fill: -papi-text-secondary; -fx-font-size: 12px;");
        JFXTextField nameField = new JFXTextField(container.getName());
        nameField.setStyle("-fx-background-color: -papi-surface; -fx-text-fill: -papi-text-secondary; -fx-font-size: 12px; -fx-padding: 4 8;");

        Label versionLabel = new Label(i18n("container.modpack.export.version"));
        versionLabel.setStyle("-fx-text-fill: -papi-text-secondary; -fx-font-size: 12px;");
        JFXTextField versionField = new JFXTextField("1.0");
        versionField.setStyle("-fx-background-color: -papi-surface; -fx-text-fill: -papi-text-secondary; -fx-font-size: 12px; -fx-padding: 4 8;");

        form.getChildren().addAll(nameLabel, nameField, versionLabel, versionField);
        layout.setBody(form);

        JFXButton cancelBtn = new JFXButton(i18n("button.cancel"));
        cancelBtn.setStyle("-fx-background-color: -papi-surface; -fx-text-fill: -papi-text-secondary; -fx-background-radius: 4; -fx-padding: 6 16;");
        JFXButton exportBtn = new JFXButton(i18n("container.modpack.export"));
        exportBtn.setStyle("-fx-background-color: -papi-primary; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 6 16;");

        layout.setActions(cancelBtn, exportBtn);

        Controllers.dialog(layout);

        cancelBtn.setOnAction(e -> layout.fireEvent(new DialogCloseEvent()));

        exportBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            String version = versionField.getText().trim();
            if (name.isEmpty()) {
                nameField.setStyle("-fx-background-color: -papi-surface; -fx-text-fill: -papi-error; -fx-font-size: 12px; -fx-padding: 4 8;");
                return;
            }

            FileChooser saveChooser = new FileChooser();
            saveChooser.setTitle(i18n("container.modpack.export"));
            saveChooser.setInitialFileName(name + "-" + version + ".zip");
            saveChooser.getExtensionFilters().setAll(
                    new FileChooser.ExtensionFilter("ZIP", "*.zip")
            );
            File outputFile = saveChooser.showSaveDialog(Controllers.getStage());
            if (outputFile == null) return;

            layout.fireEvent(new DialogCloseEvent());

            Task<?> exportTask = Task.runAsync(() -> {
                try {
                    ContainerManager.getInstance()
                            .exportModpack(container, outputFile.toPath(), name, version)
                            .run();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).whenComplete(Schedulers.javafx(), (v, ex) -> {
                if (ex != null) {
                    LOG.warning("Modpack export failed", ex);
                    Controllers.dialog(i18n("container.modpack.export.error") + ": " + ex.getMessage(),
                            i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                } else {
                    Controllers.dialog(i18n("container.modpack.export.success"),
                            i18n("message.info"), MessageDialogPane.MessageType.INFO);
                }
            });

            exportTask.start();
        });
    }

    private void showDeployErrorDialog(List<String> failures, Runnable onContinue) {
        JFXDialogLayout layout = new JFXDialogLayout();
        layout.setStyle("-fx-background-color: -papi-surface-alt;");

        Label heading = new Label(i18n("container.deploy.error.title"));
        heading.setStyle("-fx-text-fill: -papi-text; -fx-font-size: 16px; -fx-font-weight: bold;");
        layout.setHeading(heading);

        VBox body = new VBox(4);
        body.setStyle("-fx-padding: 8 0 0 0;");
        for (String failure : failures) {
            Label item = new Label("  \u2022 " + failure);
            item.setStyle("-fx-text-fill: -papi-error; -fx-font-size: 13px;");
            body.getChildren().add(item);
        }
        Label prompt = new Label(i18n("container.deploy.error.prompt"));
        prompt.setStyle("-fx-text-fill: -papi-text-tertiary; -fx-font-size: 12px; -fx-padding: 12 0 0 0;");
        body.getChildren().add(prompt);
        layout.setBody(body);

        JFXButton continueBtn = new JFXButton(i18n("container.deploy.error.continue"));
        continueBtn.setStyle("-fx-background-color: -papi-surface; -fx-text-fill: -papi-text-secondary; -fx-background-radius: 4; -fx-padding: 6 16; -fx-font-size: 13px;");

        JFXButton cancelBtn = new JFXButton(i18n("button.cancel"));
        cancelBtn.setStyle("-fx-background-color: -papi-primary; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 6 16; -fx-font-size: 13px;");

        layout.setActions(continueBtn, cancelBtn);

        continueBtn.setOnAction(ev -> {
            layout.fireEvent(new DialogCloseEvent());
            onContinue.run();
        });
        cancelBtn.setOnAction(ev -> {
            layout.fireEvent(new DialogCloseEvent());
        });

        Controllers.dialog(layout);
    }

    private void showImportRejectionDialog(List<String> rejections) {
        JFXDialogLayout layout = new JFXDialogLayout();
        layout.setStyle("-fx-background-color: -papi-surface-alt;");

        Label heading = new Label(i18n("container.import.rejected.title"));
        heading.setStyle("-fx-text-fill: -papi-text; -fx-font-size: 16px; -fx-font-weight: bold;");
        layout.setHeading(heading);

        VBox body = new VBox(4);
        body.setStyle("-fx-padding: 8 0 0 0;");
        for (String rejection : rejections) {
            Label item = new Label("  \u2022 " + rejection);
            item.setStyle("-fx-text-fill: -papi-error; -fx-font-size: 13px;");
            body.getChildren().add(item);
        }
        layout.setBody(body);

        JFXButton okBtn = new JFXButton(i18n("button.ok"));
        okBtn.setStyle("-fx-background-color: -papi-primary; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 6 16; -fx-font-size: 13px;");

        layout.setActions(okBtn);
        okBtn.setOnAction(e -> layout.fireEvent(new DialogCloseEvent()));

        Controllers.dialog(layout);
    }

    // -------------------------------------------------------------------------
    // Container card component
    // -------------------------------------------------------------------------

    /**
     * A card in the 2-column grid showing container info, content counters, and date.
     * Layout: [icon + name/version] | [2x2 stat grid] | [date]
     */
    private class ContainerCard extends VBox {
        private final Container container;
        private boolean selected = false;

        private final Label modCount = new Label();
        private final Label worldCount = new Label();
        private final Label rpCount = new Label();
        private final Label spCount = new Label();

        ContainerCard(Container container) {
            this.container = container;

            getStyleClass().add("container-card");
            setMaxHeight(Double.MAX_VALUE);
            setOnMouseClicked(e -> selectContainer(container));

            // --- Version string ------------------------------------------------
            String ver = container.getLinkedVersionId();
            String gameVer = ver != null ? resolveGameVersion(ver) : null;
            String loader = ver != null ? resolveLoader(ver) : null;
            StringBuilder versionText = new StringBuilder();
            if (gameVer != null) versionText.append("Minecraft ").append(gameVer);
            else versionText.append(i18n("container.version.default"));
            if (loader != null && !loader.isEmpty()) versionText.append(" · ").append(loader);
            String versionStr = versionText.toString();

            // --- TOP ZONE: icon + name/version ---------------------------------
            StackPane iconBox = new StackPane(wrap(SVG.FOLDER.createIcon(Theme.foregroundFillBinding(), 22)));
            iconBox.setMinSize(42, 42);
            iconBox.setMaxSize(42, 42);
            iconBox.setStyle("-fx-background-color: -papi-surface-alt; -fx-background-radius: 8;");

            Label nameLabel = new Label(container.getName());
            nameLabel.getStyleClass().add("container-name-label");
            nameLabel.setMaxWidth(Double.MAX_VALUE);

            Label versionLabel = new Label(versionStr);
            versionLabel.getStyleClass().add("container-version-label");
            versionLabel.setMaxWidth(Double.MAX_VALUE);

            VBox textBlock = new VBox(2, nameLabel, versionLabel);
            HBox.setHgrow(textBlock, Priority.ALWAYS);

            HBox topZone = new HBox(10, iconBox, textBlock);
            topZone.setPadding(new Insets(14, 14, 10, 14));
            topZone.setAlignment(Pos.CENTER_LEFT);

            // --- MIDDLE ZONE: 2x2 stat grid ------------------------------------
            Color green = Color.web(Theme.getTheme().getColor());

            GridPane statsGrid = new GridPane();
            ColumnConstraints sc1 = new ColumnConstraints();
            sc1.setPercentWidth(50);
            ColumnConstraints sc2 = new ColumnConstraints();
            sc2.setPercentWidth(50);
            statsGrid.getColumnConstraints().addAll(sc1, sc2);

            statsGrid.add(createStatCell(SVG.EXTENSION.createIcon(green, 16), modCount, i18n("container.mods")), 0, 0);
            statsGrid.add(createStatCell(SVG.LANDSCAPE.createIcon(green, 16), worldCount, i18n("container.worlds")), 1, 0);
            statsGrid.add(createStatCell(SVG.ARCHIVE.createIcon(green, 16), rpCount, i18n("container.resourcepacks")), 0, 1);
            statsGrid.add(createStatCell(SVG.WB_SUNNY.createIcon(green, 16), spCount, i18n("container.shaderpacks")), 1, 1);

            // --- BOTTOM ZONE: date ---------------------------------------------
            Label dateLabel = new Label();
            if (container.getCreatedAt() != null) {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                dateLabel.setText(dtf.format(container.getCreatedAt().atZone(ZoneId.systemDefault())));
            }
            dateLabel.getStyleClass().add("container-date-label");

            HBox dateZone = new HBox(dateLabel);
            dateZone.setAlignment(Pos.CENTER_RIGHT);
            dateZone.setPadding(new Insets(6, 14, 6, 14));

            getChildren().addAll(topZone, statsGrid, dateZone);

            refreshCounters();

            CompletableFuture.supplyAsync(() -> {
                List<ContainerContentEntry> content = ContainerManager.getInstance().getContent(container);
                return content.stream().anyMatch(ContainerContentEntry::isBroken);
            }).thenAccept(hasBroken -> javafx.application.Platform.runLater(() -> {
                if (hasBroken != null && hasBroken && iconBox.getScene() != null) {
                    Label badge = new Label("!");
                    badge.getStyleClass().add("container-broken-badge");
                    StackPane.setAlignment(badge, Pos.TOP_RIGHT);
                    iconBox.getChildren().add(badge);
                }
            }));
        }

        void refreshCounters() {
            List<ContainerContentEntry> content = ContainerManager.getInstance().getContent(container);
            // All entries are enabled by default; this filter is a safety guard
            modCount.setText(String.valueOf(content.stream()
                    .filter(e -> e.getType() == ContainerContentEntry.Type.MOD && e.isEnabled()).count()));
            worldCount.setText(String.valueOf(content.stream()
                    .filter(e -> e.getType() == ContainerContentEntry.Type.WORLD && e.isEnabled()).count()));
            rpCount.setText(String.valueOf(content.stream()
                    .filter(e -> e.getType() == ContainerContentEntry.Type.RESOURCE_PACK && e.isEnabled()).count()));
            spCount.setText(String.valueOf(content.stream()
                    .filter(e -> e.getType() == ContainerContentEntry.Type.SHADER_PACK && e.isEnabled()).count()));
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            if (selected) {
                getStyleClass().add("container-card-selected");
            } else {
                getStyleClass().remove("container-card-selected");
            }
        }
    }

    /** Creates a stat cell HBox: [green-icon] [count + label]. */
    private HBox createStatCell(Node icon, Label countLabel, String labelText) {
        countLabel.getStyleClass().add("container-count-label");
        Label desc = new Label(labelText);
        desc.getStyleClass().add("container-desc-label");

        VBox textBox = new VBox(0, countLabel, desc);
        textBox.setAlignment(Pos.CENTER_LEFT);

        HBox cell = new HBox(6, wrap(icon), textBox);
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.getStyleClass().add("container-stat-cell");
        return cell;
    }

    /** Updates the counter values on the selected container's card without a full grid rebuild. */
    private void refreshCounterLabels() {
        if (selectedContainerId == null) return;
        for (Node node : cardGrid.getChildren()) {
            if (node instanceof ContainerCard) {
                ContainerCard card = (ContainerCard) node;
                if (card.container.getId().equals(selectedContainerId)) {
                    card.refreshCounters();
                    return;
                }
            }
        }
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
