package org.jackhuang.hmcl.upgrade;

import javafx.application.Platform;

import static org.jackhuang.hmcl.util.Lang.thread;

public final class PapiUpdateService {

    private PapiUpdateService() {}

    public static void checkAndPrompt() {
        thread(() -> {
            PapiRemoteVersion remote = PapiUpdateChecker.checkForUpdate();
            if (remote != null) {
                Platform.runLater(() -> showUpdateDialog(remote));
            }
        }, "papi-update-checker", true);
    }

    private static void showUpdateDialog(PapiRemoteVersion remote) {
        PapiUpdateDialog dialog = new PapiUpdateDialog(remote);
        dialog.showAndWait();
    }
}
