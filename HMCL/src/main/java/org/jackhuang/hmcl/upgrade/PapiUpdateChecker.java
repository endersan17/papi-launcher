package org.jackhuang.hmcl.upgrade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.IOException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class PapiUpdateChecker {

    private PapiUpdateChecker() {}

    public static final String CURRENT_VERSION = Metadata.VERSION;

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/endersan17/papi-launcher/releases";

    public static PapiRemoteVersion checkForUpdate() {
        try {
            String response = NetworkUtils.doGet(GITHUB_API_URL);
            JsonArray releases = JsonUtils.fromNonNullJson(response, JsonArray.class);

            if (releases.size() == 0) {
                LOG.info("No releases found on GitHub");
                return null;
            }

            JsonObject latest = releases.get(0).getAsJsonObject();
            String tag = latest.get("tag_name").getAsString();
            String remoteVersion = tag.startsWith("v") ? tag.substring(1) : tag;

            String body = latest.has("body") && !latest.get("body").isJsonNull()
                    ? latest.get("body").getAsString()
                    : null;

            JsonArray assets = latest.getAsJsonArray("assets");
            String downloadUrl = null;

            for (JsonElement element : assets) {
                JsonObject asset = element.getAsJsonObject();
                String name = asset.get("name").getAsString();
                if (name.startsWith("papi-launcher-") && name.endsWith(".jar")) {
                    downloadUrl = asset.get("browser_download_url").getAsString();
                    break;
                }
            }

            if (downloadUrl == null) {
                LOG.warning("No matching papi-launcher-*.jar asset in latest release");
                return null;
            }

            if (VersionNumber.asVersion(remoteVersion).compareTo(VersionNumber.asVersion(CURRENT_VERSION)) <= 0) {
                LOG.info("Already up to date: " + CURRENT_VERSION);
                return null;
            }

            LOG.info("Update available: " + remoteVersion);
            return new PapiRemoteVersion(remoteVersion, downloadUrl, body);

        } catch (IOException | RuntimeException e) {
            LOG.warning("Failed to check for update", e);
            return null;
        }
    }
}
