/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.setting;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import org.jackhuang.hmcl.ui.FXUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Premium Theme Manager with Glassmorphism and Neon Effects
 * Handles loading/updating theme CSS dynamically
 * @author Glavo (modified for PAPI LAUNCHER)
 */
public final class StyleSheets {
    private static final int FONT_STYLE_SHEET_INDEX = 0;
    private static final int ROOT_CSS_INDEX = 1;
    private static final int THEME_STYLE_SHEET_INDEX = 2;
    private static final int THEME_MANAGER_INDEX = 3;
    private static final int CUSTOM_CSS_INDEX = 4;
    private static final int FORCE_WHITE_INDEX = 5;

    private static final String FORCE_WHITE_CSS =
            ".root * { -fx-text-fill: #FFFFFF !important; }\n" +
            "Text { -fx-fill: #FFFFFF !important; }\n" +
            ".root SVGPath { -fx-fill: -papi-text !important; }";

    private static final ObservableList<String> stylesheets;
    private static final Map<String, String> STYLE_SHEET_URI_CACHE = new ConcurrentHashMap<>();
    private static String cachedThemeStyleSheet;
    private static String cachedThemeName;

    static {
        String forceWhiteUri = toStyleSheetUri(FORCE_WHITE_CSS, "");
        cachedThemeStyleSheet = getThemeStyleSheet();
        String[] array = new String[]{
                getFontStyleSheet(),
                "/assets/css/root.css",
                cachedThemeStyleSheet,
                getThemeManagerCss(),
                getCustomCssStyleSheet(),
                forceWhiteUri
        };
        stylesheets = FXCollections.observableList(Arrays.asList(array));

        FontManager.fontProperty().addListener(o -> stylesheets.set(FONT_STYLE_SHEET_INDEX, getFontStyleSheet()));
        config().themeProperty().addListener(o -> {
            String newUri = getThemeStyleSheet();
            cachedThemeStyleSheet = newUri;
            stylesheets.set(THEME_STYLE_SHEET_INDEX, newUri);
            updateOpacityCss();
        });
        config().customCssPathProperty().addListener(o -> stylesheets.set(CUSTOM_CSS_INDEX, getCustomCssStyleSheet()));
        config().transparentEffectProperty().addListener(o -> updateOpacityCss());
    }

    private static void updateOpacityCss() {
        ThemeManager.updateTransparencies();
        String css = ThemeManager.getOpacityCss();
        String uri;
        if (css == null || css.isEmpty()) {
            uri = "";
        } else {
            uri = toStyleSheetUri(css, "");
        }
        if (!uri.equals(ThemeManager.getLastAppliedUri())) {
            stylesheets.set(THEME_MANAGER_INDEX, uri);
            ThemeManager.setLastAppliedUri(uri);
        }
    }

    private static String toStyleSheetUri(String styleSheet, String fallback) {
        String cached = STYLE_SHEET_URI_CACHE.get(styleSheet);
        if (cached != null) return cached;

        String uri;
        if (FXUtils.JAVAFX_MAJOR_VERSION >= 17) {
            uri = "data:text/css;charset=UTF-8;base64," + Base64.getEncoder().encodeToString(styleSheet.getBytes(StandardCharsets.UTF_8));
        } else {
            try {
                Path temp = Files.createTempFile("hmcl", ".css");
                Files.writeString(temp, styleSheet, Charset.defaultCharset());
                temp.toFile().deleteOnExit();
                uri = temp.toUri().toString();
            } catch (IOException | NullPointerException e) {
                LOG.error("Unable to create stylesheet, fallback to " + fallback, e);
                return fallback;
            }
        }

        STYLE_SHEET_URI_CACHE.put(styleSheet, uri);
        return uri;
    }

    private static String getFontStyleSheet() {
        final String defaultCss = "/assets/css/font.css";
        final FontManager.FontReference font = FontManager.getFont();

        if (font == null || "System".equals(font.getFamily()))
            return defaultCss;

        String fontFamily = font.getFamily();
        String style = font.getStyle();
        String weight = null;
        String posture = null;

        if (style != null) {
            style = style.toLowerCase(Locale.ROOT);

            if (style.contains("thin"))
                weight = "100";
            else if (style.contains("extralight") || style.contains("extra light") || style.contains("ultralight") | style.contains("ultra light"))
                weight = "200";
            else if (style.contains("medium"))
                weight = "500";
            else if (style.contains("semibold") || style.contains("semi bold") || style.contains("demibold") || style.contains("demi bold"))
                weight = "600";
            else if (style.contains("extrabold") || style.contains("extra bold") || style.contains("ultrabold") || style.contains("ultra bold"))
                weight = "800";
            else if (style.contains("black") || style.contains("heavy"))
                weight = "900";
            else if (style.contains("light"))
                weight = "lighter";
            else if (style.contains("bold"))
                weight = "bold";

            posture = style.contains("italic") || style.contains("oblique") ? "italic" : null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(".root {");
        builder.append("-fx-font-family:\"").append(fontFamily).append("\";");

        if (weight != null)
            builder.append("-fx-font-weight:").append(weight).append(";");

        if (posture != null)
            builder.append("-fx-font-style:").append(posture).append(";");

        builder.append('}');

        return toStyleSheetUri(builder.toString(), fontFamily);
    }

    private static String getThemeManagerCss() {
        try {
            updateOpacityCss();
            String css = ThemeManager.getOpacityCss();
            if (css == null || css.isEmpty()) {
                return "";
            }
            return toStyleSheetUri(css, "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Get theme CSS file based on current theme
     * Returns the appropriate theme file for glassmorphism + neon styling
     */
    private static String getThemeStyleSheet() {
        final String[] themeNames = {
                "green", "blue", "cyan", "purple", "red", "yellow"
        };
        final String defaultCss = "/assets/css/papitheme-green.css";

        Theme theme = Theme.getTheme();
        if (theme == null)
            return defaultCss;

        String themeName = theme.getName().toLowerCase(Locale.ROOT);
        if (themeName.equals(cachedThemeName) && cachedThemeStyleSheet != null)
            return cachedThemeStyleSheet;

        for (String name : themeNames) {
            if (themeName.equals(name)) {
                String cssPath = "/assets/css/papitheme-" + name + ".css";
                if (StyleSheets.class.getResource(cssPath) != null) {
                    cachedThemeName = themeName;
                    cachedThemeStyleSheet = cssPath;
                    return cssPath;
                }
            }
        }
        // Legacy theme name support
        String path;
        switch (themeName) {
            case "void_green":
                path = "/assets/css/papitheme-green.css"; break;
            case "blue_launcher":
                path = "/assets/css/papitheme-blue.css"; break;
            case "cyan_pulse":
                path = "/assets/css/papitheme-cyan.css"; break;
            case "electric_purple":
                path = "/assets/css/papitheme-purple.css"; break;
            case "magenta_glow":
                path = "/assets/css/papitheme-red.css"; break;
            case "acid_lime":
                path = "/assets/css/papitheme-yellow.css"; break;
            default:
                path = defaultCss; break;
        }
        cachedThemeName = themeName;
        cachedThemeStyleSheet = path;
        return path;
    }

    public static void init(Scene scene) {
        ObservableList<String> target = scene.getStylesheets();
        stylesheets.addListener((ListChangeListener<String>) c -> {
            Platform.runLater(() -> {
                List<String> validSheets = new ArrayList<>();
                for (String s : stylesheets) {
                    if (s != null && !s.trim().isEmpty()) {
                        validSheets.add(s);
                    }
                }
                if (!target.equals(validSheets)) {
                    target.setAll(validSheets);
                }
            });
        });
        List<String> initialSheets = new ArrayList<>();
        for (String s : stylesheets) {
            if (s != null && !s.trim().isEmpty()) {
                initialSheets.add(s);
            }
        }
        if (!initialSheets.isEmpty()) {
            target.addAll(initialSheets);
        }
    }

    private static String getCustomCssStyleSheet() {
        String customCssPath = config().getCustomCssPath();
        if (customCssPath == null || customCssPath.isEmpty()) {
            return "";
        }

        Path cssFile = Path.of(customCssPath);
        if (!Files.exists(cssFile)) {
            LOG.warning("Custom CSS file not found: " + customCssPath);
            return "";
        }

        try {
            String cssContent = Files.readString(cssFile, StandardCharsets.UTF_8);
            return toStyleSheetUri(cssContent, "");
        } catch (IOException e) {
            LOG.error("Failed to read custom CSS file: " + customCssPath, e);
            return "";
        }
    }

    private StyleSheets() {
    }
}