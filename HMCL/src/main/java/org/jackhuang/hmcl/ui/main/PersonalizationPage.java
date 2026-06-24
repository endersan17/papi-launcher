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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.effects.JFXDepthManager;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.binding.When;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import org.jackhuang.hmcl.setting.EnumBackgroundImage;
import org.jackhuang.hmcl.setting.FontManager;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.setting.ConfigHolder.globalConfig;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class PersonalizationPage extends StackPane {

    private final HBox themeCardsContainer = new HBox(10);
    private Theme selectedTheme = Theme.getTheme();

    private static int snapOpacity(double val) {
        if (val <= 0) {
            return 0;
        } else if (Double.isNaN(val) || val >= 100.) {
            return 100;
        }

        int prevTick = (int) (val / 5);
        int prevTickValue = prevTick * 5;
        int nextTickValue = (prevTick + 1) * 5;

        return (val - prevTickValue) > (nextTickValue - val) ? nextTickValue : prevTickValue;
    }

    public PersonalizationPage() {
        VBox content = new VBox(4);
        content.setPadding(new Insets(4, 8, 8, 8));
        content.setFillWidth(true);
        ScrollPane scrollPane = new ScrollPane(content);
        FXUtils.smoothScrolling(scrollPane);
        scrollPane.setFitToWidth(true);
        getChildren().setAll(scrollPane);

        for (Theme theme : Theme.ALL_THEMES) {
            themeCardsContainer.getChildren().add(createThemeCard(theme));
        }

        ComponentList themeList = new ComponentList();
        {
            VBox themeBlock = new VBox();
            themeBlock.setSpacing(8);
            themeBlock.setPadding(new Insets(8, 0, 8, 0));

            Label themeTitle = new Label(i18n("settings.launcher.theme"));
            themeTitle.setStyle("-fx-font-size: 14px; -fx-text-fill: -papi-text; -fx-font-weight: 700;");
            themeTitle.setPadding(new Insets(0, 0, 4, 0));

            themeBlock.getChildren().addAll(themeTitle, themeCardsContainer);
            themeList.getContent().add(themeBlock);
        }
        {
            OptionToggleButton titleTransparentButton = new OptionToggleButton();
            themeList.getContent().add(titleTransparentButton);
            titleTransparentButton.selectedProperty().bindBidirectional(config().titleTransparentProperty());
            titleTransparentButton.setTitle(i18n("settings.launcher.title_transparent"));
        }
        {
            OptionToggleButton animationButton = new OptionToggleButton();
            themeList.getContent().add(animationButton);
            animationButton.selectedProperty().bindBidirectional(config().animationDisabledProperty());
            animationButton.setTitle(i18n("settings.launcher.turn_off_animations"));
        }
        {
            OptionToggleButton glassButton = new OptionToggleButton();
            themeList.getContent().add(glassButton);
            glassButton.selectedProperty().bindBidirectional(config().transparentEffectProperty());
            glassButton.setTitle(i18n("settings.launcher.glass_effect"));
            glassButton.setSubtitle(i18n("settings.launcher.glass_effect.subtitle"));
        }
        content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.appearance")), themeList);

        {
            ComponentList componentList = new ComponentList();

            MultiFileItem<EnumBackgroundImage> backgroundItem = new MultiFileItem<>();
            ComponentSublist backgroundSublist = new ComponentSublist();
            backgroundSublist.getContent().add(backgroundItem);
            backgroundSublist.setTitle(i18n("launcher.background"));
            backgroundSublist.setHasSubtitle(true);

            backgroundItem.loadChildren(Arrays.asList(
                    new MultiFileItem.Option<>(i18n("launcher.background.default"), EnumBackgroundImage.DEFAULT)
                            .setTooltip(i18n("launcher.background.default.tooltip")),
                    new MultiFileItem.Option<>(i18n("launcher.background.classic"), EnumBackgroundImage.CLASSIC),
                    new MultiFileItem.FileOption<>(i18n("settings.custom"), EnumBackgroundImage.CUSTOM)
                            .setChooserTitle(i18n("launcher.background.choose"))
                            .addExtensionFilter(FXUtils.getImageExtensionFilter())
                            .bindBidirectional(config().backgroundImageProperty()),
                    new MultiFileItem.StringOption<>(i18n("launcher.background.network"), EnumBackgroundImage.NETWORK)
                            .setValidators(new URLValidator(true))
                            .bindBidirectional(config().backgroundImageUrlProperty()),
                    new MultiFileItem.PaintOption<>(i18n("launcher.background.paint"), EnumBackgroundImage.PAINT)
                            .bindBidirectional(config().backgroundPaintProperty()),
                    new MultiFileItem.Option<>(i18n("launcher.background.per_theme"), EnumBackgroundImage.PER_THEME)
                            .setTooltip(i18n("launcher.background.per_theme.tooltip"))
            ));
            backgroundItem.selectedDataProperty().bindBidirectional(config().backgroundImageTypeProperty());
            backgroundSublist.subtitleProperty().bind(
                    new When(backgroundItem.selectedDataProperty().isEqualTo(EnumBackgroundImage.DEFAULT))
                            .then(i18n("launcher.background.default"))
                            .otherwise(config().backgroundImageProperty()));

            HBox opacityItem = new HBox(8);
            {
                opacityItem.setAlignment(Pos.CENTER);

                Label label = new Label(i18n("settings.launcher.background.settings.opacity"));

                JFXSlider slider = new JFXSlider(0, 100,
                        config().getBackgroundImageType() != EnumBackgroundImage.TRANSLUCENT
                                ? config().getBackgroundImageOpacity() : 50);
                slider.setShowTickMarks(true);
                slider.setMajorTickUnit(10);
                slider.setMinorTickCount(1);
                slider.setBlockIncrement(5);
                slider.setSnapToTicks(true);
                HBox.setHgrow(slider, Priority.ALWAYS);

                if (config().getBackgroundImageType() == EnumBackgroundImage.TRANSLUCENT) {
                    slider.setDisable(true);
                    config().backgroundImageTypeProperty().addListener(new ChangeListener<>() {
                        @Override
                        public void changed(ObservableValue<? extends EnumBackgroundImage> observable, EnumBackgroundImage oldValue, EnumBackgroundImage newValue) {
                            if (newValue != EnumBackgroundImage.TRANSLUCENT) {
                                config().backgroundImageTypeProperty().removeListener(this);
                                slider.setDisable(false);
                                slider.setValue(100);
                            }
                        }
                    });
                }

                Label textOpacity = new Label();

                StringBinding valueBinding = Bindings.createStringBinding(() -> ((int) slider.getValue()) + "%", slider.valueProperty());
                textOpacity.textProperty().bind(valueBinding);
                slider.setValueFactory(s -> valueBinding);

                slider.valueProperty().addListener((observable, oldValue, newValue) ->
                        config().setBackgroundImageOpacity(snapOpacity(newValue.doubleValue())));

                opacityItem.getChildren().setAll(label, slider, textOpacity);
            }

            componentList.getContent().setAll(backgroundItem, opacityItem);
            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("launcher.background")), componentList);
        }

        {
            ComponentList logPane = new ComponentSublist();
            logPane.setTitle(i18n("settings.launcher.log"));

            {
                VBox fontPane = new VBox();
                fontPane.setSpacing(5);

                {
                    BorderPane borderPane = new BorderPane();
                    fontPane.getChildren().add(borderPane);
                    {
                        Label left = new Label(i18n("settings.launcher.log.font"));
                        BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                        borderPane.setLeft(left);
                    }

                    {
                        HBox hBox = new HBox();
                        hBox.setSpacing(3);

                        FontComboBox cboLogFont = new FontComboBox();
                        cboLogFont.valueProperty().bindBidirectional(config().fontFamilyProperty());

                        JFXTextField txtLogFontSize = new JFXTextField();
                        FXUtils.setLimitWidth(txtLogFontSize, 50);
                        FXUtils.bind(txtLogFontSize, config().fontSizeProperty(), SafeStringConverter.fromFiniteDouble()
                                .restrict(it -> it > 0)
                                .fallbackTo(12.0)
                                .asPredicate(Validator.addTo(txtLogFontSize)));

                        hBox.getChildren().setAll(cboLogFont, txtLogFontSize);

                        borderPane.setRight(hBox);
                    }
                }

                Label lblLogFontDisplay = new Label("[23:33:33] [Client Thread/INFO] [WaterPower]: Loaded mod WaterPower.");
                lblLogFontDisplay.fontProperty().bind(Bindings.createObjectBinding(
                        () -> Font.font(Lang.requireNonNullElse(config().getFontFamily(), FXUtils.DEFAULT_MONOSPACE_FONT), config().getFontSize()),
                        config().fontFamilyProperty(), config().fontSizeProperty()));

                fontPane.getChildren().add(lblLogFontDisplay);

                logPane.getContent().add(fontPane);
            }

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.log")), logPane);
        }

        {
            ComponentSublist fontPane = new ComponentSublist();
            fontPane.setTitle(i18n("settings.launcher.font"));

            {
                VBox vbox = new VBox();
                vbox.setSpacing(5);

                {
                    BorderPane borderPane = new BorderPane();
                    vbox.getChildren().add(borderPane);
                    {
                        Label left = new Label(i18n("settings.launcher.font"));
                        BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                        borderPane.setLeft(left);
                    }

                    {
                        HBox hBox = new HBox();
                        hBox.setSpacing(8);

                        FontComboBox cboFont = new FontComboBox();
                        cboFont.setValue(config().getLauncherFontFamily());
                        FXUtils.onChange(cboFont.valueProperty(), FontManager::setFontFamily);

                        JFXButton clearButton = new JFXButton();
                        clearButton.getStyleClass().add("toggle-icon4");
                        clearButton.setGraphic(SVG.RESTORE.createIcon(Theme.blackFill(), -1));
                        clearButton.setOnAction(e -> cboFont.setValue(null));

                        hBox.getChildren().setAll(cboFont, clearButton);

                        borderPane.setRight(hBox);
                    }
                }

                vbox.getChildren().add(new Label("Hello Minecraft! Launcher"));

                fontPane.getContent().add(vbox);
            }

            {
                BorderPane fontAntiAliasingPane = new BorderPane();
                {
                    Label left = new Label(i18n("settings.launcher.font.anti_aliasing"));
                    BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                    fontAntiAliasingPane.setLeft(left);
                }

                {
                    @SuppressWarnings("unchecked")
                    JFXComboBox<Optional<FontSmoothingType>> cboAntiAliasing = new JFXComboBox<>(FXCollections.observableArrayList(
                            Optional.empty(),
                            Optional.of(FontSmoothingType.LCD),
                            Optional.of(FontSmoothingType.GRAY)
                    ));
                    String fontAntiAliasing = globalConfig().getFontAntiAliasing();
                    if ("lcd".equalsIgnoreCase(fontAntiAliasing)) {
                        cboAntiAliasing.setValue(Optional.of(FontSmoothingType.LCD));
                    } else if ("gray".equalsIgnoreCase(fontAntiAliasing)) {
                        cboAntiAliasing.setValue(Optional.of(FontSmoothingType.GRAY));
                    } else {
                        cboAntiAliasing.setValue(Optional.empty());
                    }
                    cboAntiAliasing.setConverter(FXUtils.stringConverter(value -> {
                        if (value.isPresent()) {
                            return i18n("settings.launcher.font.anti_aliasing." + value.get().name().toLowerCase(Locale.ROOT));
                        } else {
                            return i18n("settings.launcher.font.anti_aliasing.auto");
                        }
                    }));
                    FXUtils.onChange(cboAntiAliasing.valueProperty(), value ->
                            globalConfig().setFontAntiAliasing(value.map(it -> it.name().toLowerCase(Locale.ROOT))
                                    .orElse(null)));

                    fontAntiAliasingPane.setRight(cboAntiAliasing);
                }

                fontPane.getContent().add(fontAntiAliasingPane);
            }

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.font")), fontPane);
        }
    }

    private VBox createThemeCard(Theme theme) {
        VBox card = new VBox();
        card.setSpacing(8);
        card.setAlignment(Pos.CENTER);
        card.setCursor(Cursor.HAND);
        card.setUserData(theme);

        Rectangle preview = new Rectangle(60, 36);
        preview.setArcWidth(8);
        preview.setArcHeight(8);
        preview.setFill(Color.web(theme.getColor()));

        Rectangle previewBorder = new Rectangle(60, 36);
        previewBorder.setArcWidth(8);
        previewBorder.setArcHeight(8);
        previewBorder.setFill(Color.TRANSPARENT);
        previewBorder.setStroke(Color.web(theme.getColor()));
        previewBorder.setStrokeWidth(1);
        previewBorder.setOpacity(0.5);

        StackPane previewContainer = new StackPane();
        previewContainer.getChildren().addAll(preview, previewBorder);

        Label nameLabel = new Label(theme.getDisplayName());
        nameLabel.setStyle(
            "-fx-font-size: 11px;" +
            "-fx-font-weight: 600;" +
            "-fx-wrap-text: true;" +
            "-fx-text-alignment: center;"
        );
        nameLabel.setMaxWidth(90);
        nameLabel.setAlignment(Pos.CENTER);

        card.getProperties().put("label", nameLabel);
        card.getChildren().addAll(previewContainer, nameLabel);

        if (theme.equals(selectedTheme)) {
            applySelectedStyle(card, theme);
        } else {
            applyDefaultStyle(card);
        }

        card.setOnMouseEntered(e -> {
            if (!theme.equals(selectedTheme)) {
                card.setStyle(
                    "-fx-background-color: rgba(255, 255, 255, 0.05);" +
                    "-fx-border-color: rgba(255, 255, 255, 0.25);" +
                    "-fx-border-width: 2px;" +
                    "-fx-border-radius: 16px;" +
                    "-fx-background-radius: 16px;" +
                    "-fx-padding: 14px 12px 12px 12px;" +
                    "-fx-min-width: 110px;" +
                    "-fx-max-width: 110px;" +
                    "-fx-effect: dropshadow(one-pass, rgba(0, 0, 0, 0.4), 12, 0, 0, 6);"
                );
            }
        });

        card.setOnMouseExited(e -> {
            if (!theme.equals(selectedTheme)) {
                applyDefaultStyle(card);
            }
        });

        card.setOnMouseClicked(e -> selectTheme(theme));

        return card;
    }

    private void selectTheme(Theme theme) {
        Theme previousTheme = selectedTheme;
        selectedTheme = theme;

        for (javafx.scene.Node node : themeCardsContainer.getChildren()) {
            if (node instanceof VBox) {
                VBox card = (VBox) node;
                Theme cardTheme = (Theme) card.getUserData();
                if (cardTheme.equals(theme)) {
                    applySelectedStyle(card, cardTheme);
                } else {
                    applyDefaultStyle(card);
                }
            }
        }

        if (previousTheme != theme) {
            config().setTheme(theme);
        }
    }

    private void applySelectedStyle(VBox card, Theme theme) {
        int r = (int)(theme.getPaint().getRed() * 255);
        int g = (int)(theme.getPaint().getGreen() * 255);
        int b = (int)(theme.getPaint().getBlue() * 255);
        String color = theme.getColor();

        card.setStyle(
            "-fx-background-color: rgba(" + r + ", " + g + ", " + b + ", 0.15);" +
            "-fx-border-color: " + color + ";" +
            "-fx-border-width: 2px;" +
            "-fx-border-radius: 16px;" +
            "-fx-background-radius: 16px;" +
            "-fx-padding: 14px 12px 12px 12px;" +
            "-fx-min-width: 110px;" +
            "-fx-max-width: 110px;"
        );

        DropShadow shadow = new DropShadow();
        shadow.setRadius(10);
        shadow.setSpread(0.3);
        shadow.setColor(theme.getPaint());
        card.setEffect(shadow);

        Label label = (Label) card.getProperties().get("label");
        if (label != null) {
            label.setStyle(
                "-fx-font-size: 11px;" +
                "-fx-text-fill: " + color + ";" +
                "-fx-font-weight: 600;" +
                "-fx-wrap-text: true;" +
                "-fx-text-alignment: center;"
            );
        }
    }

    private void applyDefaultStyle(VBox card) {
        card.setStyle(
            "-fx-background-color: rgba(30, 30, 45, 0.6);" +
            "-fx-background-radius: 16px;" +
            "-fx-border-color: rgba(255, 255, 255, 0.10);" +
            "-fx-border-width: 2px;" +
            "-fx-border-radius: 16px;" +
            "-fx-padding: 14px 12px 12px 12px;" +
            "-fx-min-width: 110px;" +
            "-fx-max-width: 110px;" +
            "-fx-effect: dropshadow(one-pass, rgba(0, 0, 0, 0.3), 8, 0, 0, 4);"
        );
        card.setEffect(null);

        Label label = (Label) card.getProperties().get("label");
        if (label != null) {
            label.setStyle(
                "-fx-font-size: 11px;" +
                "-fx-text-fill: rgba(255, 255, 255, 0.85);" +
                "-fx-font-weight: 600;" +
                "-fx-wrap-text: true;" +
                "-fx-text-alignment: center;"
            );
        }
    }
}
