package org.jackhuang.hmcl.container;

import com.google.gson.annotations.SerializedName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class ContainerContentEntry {
    public enum Type {
        @SerializedName("mod") MOD,
        @SerializedName("world") WORLD,
        @SerializedName("resourcepack") RESOURCE_PACK,
        @SerializedName("shaderpack") SHADER_PACK
    }

    private final Type type;
    private final String sourcePath;
    private final String fileName;
    private final Instant addedAt;
    @SerializedName("enabled")
    private boolean enabled = true;

    public ContainerContentEntry(Type type, String sourcePath, String fileName, Instant addedAt) {
        this.type = type;
        this.sourcePath = sourcePath;
        this.fileName = fileName;
        this.addedAt = addedAt;
    }

    public Type getType() { return type; }
    public String getSourcePath() { return sourcePath; }
    public String getFileName() { return fileName; }
    public Instant getAddedAt() { return addedAt; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isBroken() {
        return !Files.exists(Path.of(sourcePath));
    }
}
