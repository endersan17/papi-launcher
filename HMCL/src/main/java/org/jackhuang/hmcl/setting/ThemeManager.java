package org.jackhuang.hmcl.setting;

import java.util.Base64;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;

public final class ThemeManager {

    private static String cachedOpacityCss = "";
    static String lastAppliedUri = null;

    private ThemeManager() {}

    static String getLastAppliedUri() {
        return lastAppliedUri;
    }

    static void setLastAppliedUri(String uri) {
        lastAppliedUri = uri;
    }

    public static String getOpacityCss() {
        return cachedOpacityCss;
    }

    public static void updateTransparencies() {
        boolean transparent = config() != null && config().isTransparentEffect();
        if (transparent || config() == null) {
            cachedOpacityCss = "";
        } else {
            cachedOpacityCss =
                ".root {" +
                "    -papi-surface: rgba(15, 15, 18, 1);" +
                "    -papi-overlay: rgba(10, 10, 15, 1);" +
                "    -papi-dialog-bg: rgba(10, 10, 10, 1);" +
                "    -papi-surface-alt: rgba(10, 10, 10, 1);" +
                "}" +
                ".gray-background {" +
                "    -fx-background-color: rgba(5, 5, 5, 1.0);" +
                "}" +
                ".scroll-pane.gray-background {" +
                "    -fx-background-color: rgba(5, 5, 5, 1.0);" +
                "}";
        }
    }
}
