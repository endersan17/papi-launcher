package org.jackhuang.hmcl.container;

import com.google.gson.annotations.SerializedName;

public final class LaunchProfile {
    @SerializedName("name")
    private String name;
    @SerializedName("description")
    private String description;
    @SerializedName("maxMemory")
    private Integer maxMemory;
    @SerializedName("minMemory")
    private Integer minMemory;
    @SerializedName("jvmArgs")
    private String jvmArgs;
    @SerializedName("gameArgs")
    private String gameArgs;
    @SerializedName("width")
    private Integer width;
    @SerializedName("height")
    private Integer height;
    @SerializedName("fullscreen")
    private Boolean fullscreen;
    @SerializedName("serverIp")
    private String serverIp;
    @SerializedName("javaDir")
    private String javaDir;
    @SerializedName("wrapper")
    private String wrapper;
    @SerializedName("forceJavaCompat")
    private Boolean forceJavaCompat;
    @SerializedName("experimentalRendering")
    private Boolean experimentalRendering;

    public LaunchProfile() {
        this("");
    }

    public LaunchProfile(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getMaxMemory() { return maxMemory; }
    public void setMaxMemory(Integer maxMemory) { this.maxMemory = maxMemory; }

    public Integer getMinMemory() { return minMemory; }
    public void setMinMemory(Integer minMemory) { this.minMemory = minMemory; }

    public String getJvmArgs() { return jvmArgs; }
    public void setJvmArgs(String jvmArgs) { this.jvmArgs = jvmArgs; }

    public String getGameArgs() { return gameArgs; }
    public void setGameArgs(String gameArgs) { this.gameArgs = gameArgs; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    public Boolean getFullscreen() { return fullscreen; }
    public void setFullscreen(Boolean fullscreen) { this.fullscreen = fullscreen; }

    public String getServerIp() { return serverIp; }
    public void setServerIp(String serverIp) { this.serverIp = serverIp; }

    public String getJavaDir() { return javaDir; }
    public void setJavaDir(String javaDir) { this.javaDir = javaDir; }

    public String getWrapper() { return wrapper; }
    public void setWrapper(String wrapper) { this.wrapper = wrapper; }

    public Boolean getForceJavaCompat() { return forceJavaCompat; }
    public void setForceJavaCompat(Boolean forceJavaCompat) { this.forceJavaCompat = forceJavaCompat; }

    public Boolean getExperimentalRendering() { return experimentalRendering; }
    public void setExperimentalRendering(Boolean experimentalRendering) { this.experimentalRendering = experimentalRendering; }

    public LaunchProfile copy() {
        LaunchProfile p = new LaunchProfile(name);
        p.description = description;
        p.maxMemory = maxMemory;
        p.minMemory = minMemory;
        p.jvmArgs = jvmArgs;
        p.gameArgs = gameArgs;
        p.width = width;
        p.height = height;
        p.fullscreen = fullscreen;
        p.serverIp = serverIp;
        p.javaDir = javaDir;
        p.wrapper = wrapper;
        p.forceJavaCompat = forceJavaCompat;
        p.experimentalRendering = experimentalRendering;
        return p;
    }
}
