/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.Tooltip;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ClassTitle;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.util.Locale;

import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;

public final class AccountListPage extends DecoratorAnimatedPage implements DecoratorPage {
    static final BooleanProperty RESTRICTED = new SimpleBooleanProperty(true);


    static {
        // Always allow offline accounts - removed restriction
        RESTRICTED.set(false);
    }

    private final ObservableList<AccountListItem> items;
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("account.manage")));
    private final ListProperty<Account> accounts = new SimpleListProperty<>(this, "accounts", FXCollections.observableArrayList());
    private final ListProperty<AuthlibInjectorServer> authServers = new SimpleListProperty<>(this, "authServers", FXCollections.observableArrayList());
    private final ObjectProperty<Account> selectedAccount;

    public AccountListPage() {
        items = MappedObservableList.create(accounts, AccountListItem::new);
        selectedAccount = createSelectedItemPropertyFor(items, Account.class);
    }

    public ObjectProperty<Account> selectedAccountProperty() {
        return selectedAccount;
    }

    public ListProperty<Account> accountsProperty() {
        return accounts;
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public ListProperty<AuthlibInjectorServer> authServersProperty() {
        return authServers;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AccountListPageSkin(this);
    }

    private void showSkinTutorialDialog() {
        JFXDialogLayout dialog = new JFXDialogLayout();
        dialog.setStyle("-fx-background-color: #0A0A0A; -fx-text-fill: #FFFFFF;");

        Label title = new Label(i18n("account.skin_tutorial.title"));
        title.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 18px; -fx-font-weight: bold;");
        dialog.setHeading(title);

        VBox body = new VBox(24);
        body.setPadding(new Insets(8));

        // --- Skin Restorer Section ---
        VBox srSection = new VBox(8);
        srSection.setStyle("-fx-background-color: #141420; -fx-background-radius: 6; -fx-padding: 12; -fx-border-color: #00FF9D; -fx-border-radius: 6; -fx-border-width: 0 0 0 3;");

        Label srTitle = new Label(i18n("account.skin_tutorial.sr_title"));
        srTitle.setStyle("-fx-text-fill: #00FF9D; -fx-font-size: 15px; -fx-font-weight: bold;");

        VBox srContent = new VBox(6);
        srContent.getChildren().addAll(
                createStepLabel(i18n("account.skin_tutorial.sr_step1")),
                createUrlLabel(null, "https://skinsrestorer.net/upload"),
                createStepLabel(i18n("account.skin_tutorial.sr_step2")),
                createCheckLabel(),
                createUrlLabel(i18n("account.skin_tutorial.forge_fabric"), "https://modrinth.com/mod/skinrestorer"),
                createUrlLabel(i18n("account.skin_tutorial.paper_spigot"), "https://aternos.org/addons/a/spigot/2124"),
                createNoteLabel(i18n("account.skin_tutorial.aternos_note")),
                createNoteLabel(i18n("account.skin_tutorial.vanilla_note"))
        );
        srSection.getChildren().addAll(srTitle, srContent);

        body.getChildren().addAll(srSection);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        dialog.setBody(scroll);

        JFXButton closeBtn = new JFXButton(i18n("account.skin_tutorial.close"));
        closeBtn.getStyleClass().add("dialog-accept");
        closeBtn.setOnAction(e -> dialog.fireEvent(new DialogCloseEvent()));
        dialog.setActions(closeBtn);

        Controllers.dialog(dialog);
    }

    private static Label createStepLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12px;");
        label.setWrapText(true);
        return label;
    }

    private static Label createCheckLabel() {
        Label label = new Label(i18n("account.skin_tutorial.check_command") + "  /skin  " + i18n("account.skin_tutorial.installed"));
        label.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12px;");
        label.setWrapText(true);
        return label;
    }

    private static Node createUrlLabel(String prefix, String url) {
        if (prefix != null) {
            Label text = new Label(prefix);
            text.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12px;");
            text.setWrapText(true);
            Hyperlink link = new Hyperlink(url);
            link.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 12px;");
            link.setOnAction(e -> FXUtils.openLink(url));
            HBox row = new HBox(4, text, link);
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        } else {
            Hyperlink link = new Hyperlink(url);
            link.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 12px;");
            link.setOnAction(e -> FXUtils.openLink(url));
            return link;
        }
    }

    private static Label createNoteLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: rgba(255,255,255,0.5); -fx-font-size: 11px; -fx-font-style: italic;");
        label.setWrapText(true);
        return label;
    }

    private static class AccountListPageSkin extends DecoratorAnimatedPageSkin<AccountListPage> {

        private final ObservableList<AdvancedListItem> authServerItems;

        public AccountListPageSkin(AccountListPage skinnable) {
            super(skinnable);

            {
                VBox boxMethods = new VBox();
                {
                    boxMethods.getStyleClass().add("advanced-list-box-content");
                    FXUtils.setLimitWidth(boxMethods, 200);

                    AdvancedListItem microsoftItem = new AdvancedListItem();
                    microsoftItem.getStyleClass().add("navigation-drawer-item");
                    microsoftItem.setActionButtonVisible(false);
                    microsoftItem.setTitle(i18n("account.methods.microsoft"));
                    microsoftItem.setLeftGraphic(wrap(SVG.MICROSOFT));
                    microsoftItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_MICROSOFT)));

                    AdvancedListItem offlineItem = new AdvancedListItem();
                    offlineItem.getStyleClass().add("navigation-drawer-item");
                    offlineItem.setActionButtonVisible(false);
                    offlineItem.setTitle(i18n("account.methods.offline"));
                    offlineItem.setLeftGraphic(wrap(SVG.PERSON));
                    offlineItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_OFFLINE)));

                    VBox boxAuthServers = new VBox();
                    authServerItems = MappedObservableList.create(skinnable.authServersProperty(), server -> {
                        AdvancedListItem item = new AdvancedListItem();
                        item.getStyleClass().add("navigation-drawer-item");
                        item.setLeftGraphic(wrap(SVG.DRESSER));
                        item.setOnAction(e -> Controllers.dialog(new CreateAccountPane(server)));

                        JFXButton btnRemove = new JFXButton();
                        btnRemove.setOnAction(e -> {
                            Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                                skinnable.authServersProperty().remove(server);
                            }, null);
                            e.consume();
                        });
                        btnRemove.getStyleClass().add("toggle-icon4");
                        btnRemove.setGraphic(SVG.CLOSE.createIcon(Theme.blackFill(), 14));
                        item.setRightGraphic(btnRemove);

                        ObservableValue<String> title = BindingMapping.of(server, AuthlibInjectorServer::getName);
                        item.titleProperty().bind(title);
                        String host = "";
                        try {
                            host = NetworkUtils.toURI(server.getUrl()).getHost();
                        } catch (IllegalArgumentException e) {
                            LOG.warning("Unparsable authlib-injector server url " + server.getUrl(), e);
                        }
                        item.subtitleProperty().set(host);
                        Tooltip tooltip = new Tooltip();
                        tooltip.textProperty().bind(Bindings.format("%s (%s)", title, server.getUrl()));
                        FXUtils.installFastTooltip(item, tooltip);

                        return item;
                    });
                    Bindings.bindContent(boxAuthServers.getChildren(), authServerItems);

                    ClassTitle title = new ClassTitle(i18n("account.create").toUpperCase(Locale.ROOT));
                    // Always show offline and auth server options without restriction
                    boxMethods.getChildren().setAll(title, microsoftItem, offlineItem, boxAuthServers);
                }

                AdvancedListItem addAuthServerItem = new AdvancedListItem();
                {
                    addAuthServerItem.getStyleClass().add("navigation-drawer-item");
                    addAuthServerItem.setTitle(i18n("account.injector.add"));
                    addAuthServerItem.setSubtitle(i18n("account.methods.authlib_injector"));
                    addAuthServerItem.setActionButtonVisible(false);
                    addAuthServerItem.setLeftGraphic(wrap(SVG.ADD_CIRCLE));
                    addAuthServerItem.setOnAction(e -> Controllers.dialog(new AddAuthlibInjectorServerPane()));
                    VBox.setMargin(addAuthServerItem, new Insets(0, 0, 12, 0));
                }

                ScrollPane scrollPane = new ScrollPane(boxMethods);
                VBox.setVgrow(scrollPane, Priority.ALWAYS);

                AdvancedListItem skinTutorialItem = new AdvancedListItem();
                skinTutorialItem.getStyleClass().add("navigation-drawer-item");
                skinTutorialItem.setTitle(i18n("account.skin_tutorial.button"));
                skinTutorialItem.setActionButtonVisible(false);
                skinTutorialItem.setLeftGraphic(wrap(SVG.INFO.createIcon(Theme.blackFill(), 16)));
                skinTutorialItem.setOnAction(e -> skinnable.showSkinTutorialDialog());
                VBox toolbar = new VBox(4, addAuthServerItem, skinTutorialItem);
                setLeft(scrollPane, toolbar);
            }

            ScrollPane scrollPane = new ScrollPane();
            VBox list = new VBox();
            {
                scrollPane.setFitToWidth(true);

                list.maxWidthProperty().bind(scrollPane.widthProperty());
                list.setSpacing(10);
                list.getStyleClass().add("card-list");

                Bindings.bindContent(list.getChildren(), skinnable.items);

                scrollPane.setContent(list);
                FXUtils.smoothScrolling(scrollPane);

                setCenter(scrollPane);
            }
        }
    }
}
