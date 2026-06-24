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
package org.jackhuang.hmcl.ui.main;

import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.IconedTwoLineListItem;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

import org.jackhuang.hmcl.Metadata;

public class FeedbackPage extends SpinnerPane {

    public FeedbackPage() {
        VBox content = new VBox();
        content.setPadding(new Insets(10));
        content.setSpacing(10);
        content.setFillWidth(true);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        setContent(scrollPane);

        ComponentList papiCommunity = new ComponentList();
        {
            IconedTwoLineListItem discord = new IconedTwoLineListItem();
            discord.setImage(FXUtils.newBuiltinImage("/assets/img/discord.png"));
            discord.setTitle(i18n("feedback.papi_community.discord"));
            discord.setSubtitle(i18n("feedback.papi_community.discord.statement"));
            discord.setExternalLink(Metadata.PAPI_DISCORD_URL);

            IconedTwoLineListItem github = new IconedTwoLineListItem();
            github.setImage(FXUtils.newBuiltinImage("/assets/img/github.png"));
            github.setTitle(i18n("feedback.papi_community.github"));
            github.setSubtitle(i18n("feedback.papi_community.github.statement"));
            github.setExternalLink(Metadata.ISSUES_URL);

            papiCommunity.getContent().setAll(discord, github);
        }

        ComponentList hmclCommunity = new ComponentList();
        {
            IconedTwoLineListItem users = new IconedTwoLineListItem();
            users.setImage(FXUtils.newBuiltinImage("/assets/img/icon-HMCL.png"));
            users.setTitle(i18n("feedback.hmcl_community.qq_group"));
            users.setSubtitle(i18n("feedback.hmcl_community.qq_group.statement"));
            users.setExternalLink(Metadata.GROUPS_URL);

            IconedTwoLineListItem github = new IconedTwoLineListItem();
            github.setImage(FXUtils.newBuiltinImage("/assets/img/github.png"));
            github.setTitle(i18n("feedback.github"));
            github.setSubtitle(i18n("feedback.github.statement"));
            github.setExternalLink(Metadata.HMCL_GITHUB_URL + "/issues/new/choose");

            IconedTwoLineListItem discord = new IconedTwoLineListItem();
            discord.setImage(FXUtils.newBuiltinImage("/assets/img/discord.png"));
            discord.setTitle(i18n("feedback.discord"));
            discord.setSubtitle(i18n("feedback.discord.statement"));
            discord.setExternalLink("https://discord.gg/jVvC7HfM6U");

            hmclCommunity.getContent().setAll(users, github, discord);
        }

        content.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("feedback.papi_channel")),
                papiCommunity,
                ComponentList.createComponentListTitle(i18n("feedback.hmcl_channel")),
                hmclCommunity
        );

        this.setContent(content);
    }

}
