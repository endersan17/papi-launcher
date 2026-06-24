/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.main;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.IconedTwoLineListItem;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.io.InputStream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class AboutPage extends StackPane {

    public AboutPage() {
        ComponentList papiLauncher = new ComponentList();
        {
            IconedTwoLineListItem launcher = new IconedTwoLineListItem();
            launcher.setImage(FXUtils.newBuiltinImage(resolveHiDPIImage("/assets/img/icon.png")));
            launcher.setTitle("PAPI LAUNCHER");
            launcher.setSubtitle(Metadata.VERSION);
            launcher.setExternalLink(Metadata.PAPI_WEBSITE_URL);

            IconedTwoLineListItem author = new IconedTwoLineListItem();
            author.setImage(FXUtils.newBuiltinImage(resolveHiDPIImage("/assets/img/endersan17-profile.png")));
            author.setTitle("endersan17");
            author.setSubtitle(i18n("about.papi_launcher.author.statement"));
            author.setExternalLink(Metadata.PAPI_AUTHOR_GITHUB_URL);

            IconedTwoLineListItem soystormy = new IconedTwoLineListItem();
            soystormy.setImage(FXUtils.newBuiltinImage(resolveHiDPIImage("/assets/img/soystormy-profile.png")));
            soystormy.setTitle(i18n("about.soystormy"));
            soystormy.setSubtitle(i18n("about.soystormy.statement"));
            soystormy.setExternalLink("https://github.com/JuanStormy23");

            IconedTwoLineListItem discordLink = new IconedTwoLineListItem();
            discordLink.setImage(FXUtils.newBuiltinImage(resolveHiDPIImage("/assets/img/discord.png")));
            discordLink.setTitle(i18n("feedback.discord"));
            discordLink.setSubtitle(i18n("feedback.papi_community.discord.statement"));
            discordLink.setExternalLink(Metadata.PAPI_DISCORD_URL);

            IconedTwoLineListItem githubLink = new IconedTwoLineListItem();
            githubLink.setImage(FXUtils.newBuiltinImage(resolveHiDPIImage("/assets/img/github.png")));
            githubLink.setTitle(i18n("feedback.github"));
            githubLink.setSubtitle(i18n("feedback.papi_community.github.statement"));
            githubLink.setExternalLink(Metadata.PAPI_ISSUES_URL);

            papiLauncher.getContent().setAll(launcher, author, soystormy, discordLink, githubLink);
        }

        ComponentList hmclOriginal = new ComponentList();
        {
            IconedTwoLineListItem launcher = new IconedTwoLineListItem();
            launcher.setImage(FXUtils.newBuiltinImage(resolveHiDPIImage("/assets/img/icon-HMCL.png")));
            launcher.setTitle("Hello Minecraft! Launcher (HMCL)");
            launcher.setSubtitle(i18n("about.hmcl_original.author.statement"));
            launcher.setExternalLink(Metadata.PUBLISH_URL);

            IconedTwoLineListItem author = new IconedTwoLineListItem();
            author.setImage(FXUtils.newBuiltinImage(resolveHiDPIImage("/assets/img/yellow_fish.png")));
            author.setTitle("huanghongxun");
            author.setSubtitle(i18n("about.author.statement"));
            author.setExternalLink(Metadata.HMCL_BILIBILI_URL);

            hmclOriginal.getContent().setAll(launcher, author);
        }

        ComponentList thanks = loadIconedTwoLineList("/assets/about/thanks.json");

        ComponentList deps = loadIconedTwoLineList("/assets/about/deps.json");

        ComponentList legal = new ComponentList();
        {
            IconedTwoLineListItem copyright = new IconedTwoLineListItem();
            copyright.setTitle(i18n("about.copyright"));
            copyright.setSubtitle(i18n("about.copyright.statement"));
            copyright.setExternalLink(Metadata.ABOUT_URL);

            IconedTwoLineListItem claim = new IconedTwoLineListItem();
            claim.setTitle(i18n("about.claim"));
            claim.setSubtitle(i18n("about.claim.statement"));
            claim.setExternalLink(Metadata.EULA_URL);

            IconedTwoLineListItem openSource = new IconedTwoLineListItem();
            openSource.setTitle(i18n("about.open_source"));
            openSource.setSubtitle(i18n("about.open_source.statement"));
            openSource.setExternalLink(Metadata.HMCL_GITHUB_URL);

            legal.getContent().setAll(copyright, claim, openSource);
        }

        VBox content = new VBox(16);
        content.setPadding(new Insets(10));
        content.getChildren().setAll(
                ComponentList.createComponentListTitle(i18n("about.papi_launcher")),
                papiLauncher,

                ComponentList.createComponentListTitle(i18n("about.hmcl_original")),
                hmclOriginal,

                ComponentList.createComponentListTitle(i18n("about.thanks_to")),
                thanks,

                ComponentList.createComponentListTitle(i18n("about.dependency")),
                deps,

                ComponentList.createComponentListTitle(i18n("about.legal")),
                legal
        );


        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        getChildren().setAll(scrollPane);
    }

    private static String resolveHiDPIImage(String basePath) {
        if (Platform.isFxApplicationThread()) {
            try {
                Screen screen = Screen.getPrimary();
                if (screen != null) {
                    double scale = screen.getOutputScaleX();
                    String suffix = null;
                    if (scale > 4.0) {
                        suffix = "@8x";
                    } else if (scale > 2.0) {
                        suffix = "@4x";
                    } else if (scale > 1.0) {
                        suffix = "@2x";
                    } else {
                        return basePath;
                    }
                    int dotIndex = basePath.lastIndexOf('.');
                    if (dotIndex > 0) {
                        return basePath.substring(0, dotIndex) + suffix + basePath.substring(dotIndex);
                    }
                }
            } catch (Exception e) {
                // fallback to base path
            }
        }
        return basePath;
    }

    private static ComponentList loadIconedTwoLineList(String path) {
        ComponentList componentList = new ComponentList();

        InputStream input = FXUtils.class.getResourceAsStream(path);
        if (input == null) {
            LOG.warning("Resources not found: " + path);
            return componentList;
        }

        try {
            JsonArray array = JsonUtils.fromJsonFully(input, JsonArray.class);

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                IconedTwoLineListItem item = new IconedTwoLineListItem();

                if (obj.has("image")) {
                    String image = obj.get("image").getAsString();
                    item.setImage(image.startsWith("/")
                            ? FXUtils.newBuiltinImage(resolveHiDPIImage(image))
                            : new Image(image));
                }

                if (obj.has("title"))
                    item.setTitle(obj.get("title").getAsString());
                else if (obj.has("titleLocalized"))
                    item.setTitle(i18n(obj.get("titleLocalized").getAsString()));

                if (obj.has("subtitle"))
                    item.setSubtitle(obj.get("subtitle").getAsString());
                else if (obj.has("subtitleLocalized"))
                    item.setSubtitle(i18n(obj.get("subtitleLocalized").getAsString()));

                if (obj.has("externalLink"))
                    item.setExternalLink(obj.get("externalLink").getAsString());

                componentList.getContent().add(item);
            }
        } catch (IOException | JsonParseException e) {
            LOG.warning("Failed to load list: " + path, e);
        }

        return componentList;
    }
}
