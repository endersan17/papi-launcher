package org.jackhuang.hmcl.container;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ResourcePackValidator {

    public enum Severity {
        OK, WARN, ERROR
    }

    public static final class ValidationResult {
        private final Severity severity;
        private final String messageKey;
        private final Object[] messageArgs;

        public ValidationResult(Severity severity, String messageKey, Object... messageArgs) {
            this.severity = severity;
            this.messageKey = messageKey;
            this.messageArgs = messageArgs;
        }

        public Severity getSeverity() { return severity; }
        public String getMessageKey() { return messageKey; }
        public Object[] getMessageArgs() { return messageArgs; }
        public boolean isOk() { return severity == Severity.OK; }
        public boolean isWarning() { return severity == Severity.WARN; }
        public boolean isError() { return severity == Severity.ERROR; }
    }

    private static final ValidationResult OK = new ValidationResult(Severity.OK, "");

    public static ValidationResult validate(Path zipFile) {
        return validate(zipFile, null);
    }

    public static ValidationResult validate(Path zipFile, String minecraftVersion) {
        int packFormat;
        try (ZipFile zf = new ZipFile(zipFile.toFile())) {
            ZipEntry metaEntry = zf.getEntry("pack.mcmeta");
            if (metaEntry == null) {
                return new ValidationResult(Severity.ERROR, "container.resource_pack.validation.no_meta");
            }

            try (InputStream is = zf.getInputStream(metaEntry);
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has("pack") || !root.get("pack").isJsonObject()) {
                    return new ValidationResult(Severity.ERROR, "container.resource_pack.validation.no_pack_section");
                }
                JsonObject pack = root.getAsJsonObject("pack");
                if (!pack.has("pack_format") || !pack.get("pack_format").isJsonPrimitive()) {
                    return new ValidationResult(Severity.ERROR, "container.resource_pack.validation.no_pack_format");
                }
                packFormat = pack.get("pack_format").getAsInt();
                if (packFormat <= 0) {
                    return new ValidationResult(Severity.ERROR, "container.resource_pack.validation.invalid_pack_format");
                }
            } catch (JsonParseException | IllegalStateException e) {
                return new ValidationResult(Severity.ERROR, "container.resource_pack.validation.invalid_json");
            }
        } catch (IOException e) {
            return new ValidationResult(Severity.ERROR, "container.resource_pack.validation.invalid_zip");
        }

        if (minecraftVersion != null && !minecraftVersion.isEmpty()) {
            int expected = getExpectedPackFormat(minecraftVersion);
            if (expected > 0 && packFormat != expected) {
                return new ValidationResult(Severity.WARN,
                        "container.resource_pack.validation.pack_format_mismatch",
                        zipFile.getFileName().toString(), minecraftVersion, packFormat, expected);
            }
        }

        return OK;
    }

    public static String sanitizeFileName(String fileName) {
        String name = fileName.replaceAll("[/\\\\:*?\"<>|\\0]", "_");
        name = name.replaceAll("\\s+", "_");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            name += ".zip";
        }
        return name;
    }

    static int getExpectedPackFormat(String gameVersion) {
        if (VersionNumber.compare(gameVersion, "1.6.1") < 0) return -1;
        if (VersionNumber.compare(gameVersion, "1.9") < 0) return 1;
        if (VersionNumber.compare(gameVersion, "1.11") < 0) return 2;
        if (VersionNumber.compare(gameVersion, "1.13") < 0) return 3;
        if (VersionNumber.compare(gameVersion, "1.15") < 0) return 4;
        if (VersionNumber.compare(gameVersion, "1.16.2") < 0) return 5;
        if (VersionNumber.compare(gameVersion, "1.17") < 0) return 6;
        if (VersionNumber.compare(gameVersion, "1.18") < 0) return 7;
        if (VersionNumber.compare(gameVersion, "1.19") < 0) return 8;
        if (VersionNumber.compare(gameVersion, "1.19.3") < 0) return 9;
        if (VersionNumber.compare(gameVersion, "1.19.4") < 0) return 12;
        if (VersionNumber.compare(gameVersion, "1.20") < 0) return 13;
        if (VersionNumber.compare(gameVersion, "1.20.2") < 0) return 15;
        if (VersionNumber.compare(gameVersion, "1.20.3") < 0) return 18;
        if (VersionNumber.compare(gameVersion, "1.20.5") < 0) return 22;
        if (VersionNumber.compare(gameVersion, "1.21") < 0) return 32;
        if (VersionNumber.compare(gameVersion, "1.21.2") < 0) return 34;
        if (VersionNumber.compare(gameVersion, "1.21.5") < 0) return 42;
        if (VersionNumber.compare(gameVersion, "1.21.8") < 0) return 46;
        return 53;
    }
}
