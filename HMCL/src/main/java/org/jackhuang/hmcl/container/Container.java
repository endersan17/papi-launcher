package org.jackhuang.hmcl.container;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.UUID;

public final class Container {
    @SerializedName("id")
    private final UUID id;
    @SerializedName("name")
    private final String name;
    @SerializedName("linkedVersionId")
    private final String linkedVersionId;
    @SerializedName("containerPath")
    private final String containerPath;
    @SerializedName("createdAt")
    private final Instant createdAt;
    @SerializedName("description")
    private final String description;

    public Container(UUID id, String name, String linkedVersionId, String containerPath, Instant createdAt, String description) {
        this.id = id;
        this.name = name;
        this.linkedVersionId = linkedVersionId;
        this.containerPath = containerPath;
        this.createdAt = createdAt;
        this.description = description;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getLinkedVersionId() { return linkedVersionId; }
    public String getContainerPath() { return containerPath; }
    public Instant getCreatedAt() { return createdAt; }
    public String getDescription() { return description; }
}
