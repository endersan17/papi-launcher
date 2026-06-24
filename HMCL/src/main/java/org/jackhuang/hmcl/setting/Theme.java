/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Premium theme system with glassmorphism and neon effects
 * Provides automatic contrast handling for perfect readability
 *
 * ROADMAP: El sistema de temas personalizados está diseñado y documentado
 * pero no implementado en esta versión. En una versión futura se
 * habilitará la carga de archivos papitheme-[nombre].css externos.
 */
@JsonAdapter(Theme.TypeAdapter.class)
public final class Theme {
    
    // PAPI THEMES - papitheme naming convention
    public static final Theme PAPI_GREEN = new Theme("green", "#00FF9D");
    public static final Theme PAPI_BLUE = new Theme("blue", "#00AFFF");
    public static final Theme PAPI_CYAN = new Theme("cyan", "#00FFEE");
    public static final Theme PAPI_PURPLE = new Theme("purple", "#C300FF");
    public static final Theme PAPI_RED = new Theme("red", "#FF00AA");
    public static final Theme PAPI_YELLOW = new Theme("yellow", "#AAFF00");

    // Legacy alias for backward compatibility
    public static final Theme VOID_GREEN = PAPI_GREEN;
    public static final Theme BLUE_LAUNCHER = PAPI_BLUE;
    public static final Theme CYAN_PULSE = PAPI_CYAN;
    public static final Theme ELECTRIC_PURPLE = PAPI_PURPLE;
    public static final Theme MAGENTA_GLOW = PAPI_RED;
    public static final Theme ACID_LIME = PAPI_YELLOW;

    // Legacy theme for migration
    public static final Theme BLUE = new Theme("blue", "#5C6BC0");

    public static final Color BACKGROUND_DARK = Color.web("#0A0A0A");
    public static final Color BACKGROUND_DARKER = Color.web("#050507");
    public static final Color GLASS_BACKGROUND = Color.rgb(0, 0, 0, 0.4);

    public static final Theme[] ALL_THEMES = new Theme[]{
            PAPI_GREEN,
            PAPI_BLUE,
            PAPI_CYAN,
            PAPI_PURPLE,
            PAPI_RED,
            PAPI_YELLOW
    };

    public static final Color[] SUGGESTED_COLORS = new Color[]{
            Color.web("#00FF9D"),
            Color.web("#00AFFF"),
            Color.web("#00FFEE"),
            Color.web("#C300FF"),
            Color.web("#FF00AA"),
            Color.web("#AAFF00")
    };

    public static Theme getTheme() {
        Theme theme = config().getTheme();
        // Default to VOID_GREEN for first-time users or if theme is null
        if (theme == null) {
            return VOID_GREEN;
        }
        return theme;
    }

    private final Color paint;
    private final String color;
    private final String name;

    Theme(String name, String color) {
        this.name = name;
        this.color = Objects.requireNonNull(color);
        this.paint = Color.web(color);
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public Color getPaint() {
        return paint;
    }

    public boolean isCustom() {
        return name.startsWith("#");
    }

    /**
     * Determines if theme is light or dark based on luminance
     * @return true if light theme, false if dark theme
     */
    public boolean isLight() {
        // Calculate relative luminance using WCAG formula
        double r = paint.getRed();
        double g = paint.getGreen();
        double b = paint.getBlue();
        
        // Apply sRGB gamma correction
        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        
        // Calculate relative luminance
        double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return luminance > 0.5; // Threshold for light/dark
    }

    /**
     * Get appropriate foreground color for perfect contrast on dark backgrounds.
     * The launcher exclusively uses dark surfaces (#0A0A0A, #1E1E2D, #050507),
     * so foreground is always WHITE regardless of accent color luminance.
     * @return White
     */
    public Color getForegroundColor() {
        return Color.WHITE;
    }

    /**
     * Get secondary foreground color (slightly muted)
     * @return 80% opacity white
     */
    public Color getSecondaryForegroundColor() {
        return Color.rgb(255, 255, 255, 0.8);
    }

    /**
     * Get tertiary foreground color (more muted)
     * @return 60% opacity white
     */
    public Color getTertiaryForegroundColor() {
        return Color.rgb(255, 255, 255, 0.6);
    }

    public static Theme custom(String color) {
        if (!color.startsWith("#"))
            throw new IllegalArgumentException();
        return new Theme(color, color);
    }

    public static Optional<Theme> getTheme(String name) {
        if (name == null)
            return Optional.empty();
        else if (name.startsWith("#"))
            try {
                Color.web(name);
                return Optional.of(custom(name));
            } catch (IllegalArgumentException ignore) {
            }
        else {
            switch (name.toLowerCase(Locale.ROOT)) {
                case "green":
                case "void_green":
                    return Optional.of(PAPI_GREEN);
                case "blue":
                case "blue_launcher":
                    return Optional.of(PAPI_BLUE);
                case "cyan":
                case "cyan_pulse":
                    return Optional.of(PAPI_CYAN);
                case "purple":
                case "electric_purple":
                    return Optional.of(PAPI_PURPLE);
                case "red":
                case "magenta_glow":
                    return Optional.of(PAPI_RED);
                case "yellow":
                case "acid_lime":
                    return Optional.of(PAPI_YELLOW);
            }
        }

        return Optional.empty();
    }

    public static String getColorDisplayName(Color c) {
        return c != null ? String.format("#%02X%02X%02X", 
                Math.round(c.getRed() * 255.0D), 
                Math.round(c.getGreen() * 255.0D), 
                Math.round(c.getBlue() * 255.0D)) : null;
    }

    private static ObjectBinding<Color> FOREGROUND_FILL;

    public static ObjectBinding<Color> foregroundFillBinding() {
        if (FOREGROUND_FILL == null)
            FOREGROUND_FILL = Bindings.createObjectBinding(
                    () -> Theme.getTheme().getForegroundColor(),
                    config().themeProperty()
            );

        return FOREGROUND_FILL;
    }

    public static ObjectBinding<Color> secondaryForegroundFillBinding() {
        return Bindings.createObjectBinding(
                () -> Theme.getTheme().getSecondaryForegroundColor(),
                config().themeProperty()
        );
    }

    public static ObjectBinding<Color> tertiaryForegroundFillBinding() {
        return Bindings.createObjectBinding(
                () -> Theme.getTheme().getTertiaryForegroundColor(),
                config().themeProperty()
        );
    }

    public static Color blackFill() {
        return Color.WHITE;
    }

    public static Color whiteFill() {
        return Color.WHITE;
    }

    public String getDisplayName() {
        switch (name) {
            case "green":
                return "Void Green";
            case "blue":
                return "Blue Launcher";
            case "cyan":
                return "Cyan Pulse";
            case "purple":
                return "Electric Purple";
            case "red":
                return "Magenta Glow";
            case "yellow":
                return "Acid Lime";
            default:
                return name;
        }
    }

    public static class TypeAdapter extends com.google.gson.TypeAdapter<Theme> {
        @Override
        public void write(JsonWriter out, Theme value) throws IOException {
            out.value(value.getName().toLowerCase(Locale.ROOT));
        }

        @Override
        public Theme read(JsonReader in) throws IOException {
            return getTheme(in.nextString()).orElse(Theme.VOID_GREEN);
        }
    }
}