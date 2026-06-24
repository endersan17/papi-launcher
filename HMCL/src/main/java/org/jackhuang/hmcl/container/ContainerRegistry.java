package org.jackhuang.hmcl.container;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class ContainerRegistry {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    @SerializedName("schema_version")
    private int schemaVersion;
    @SerializedName("containers")
    private List<Container> containers;

    public ContainerRegistry() {
    }

    public ContainerRegistry(int schemaVersion, List<Container> containers) {
        this.schemaVersion = schemaVersion;
        this.containers = containers;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public List<Container> getContainers() {
        return containers;
    }
}
