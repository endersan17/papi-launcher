package org.jackhuang.hmcl.upgrade;

public final class PapiRemoteVersion {

    private final String version;
    private final String downloadUrl;
    private final String body;

    public PapiRemoteVersion(String version, String downloadUrl) {
        this(version, downloadUrl, null);
    }

    public PapiRemoteVersion(String version, String downloadUrl, String body) {
        this.version = version;
        this.downloadUrl = downloadUrl;
        this.body = body;
    }

    public String getVersion() {
        return version;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getBody() {
        return body;
    }
}
