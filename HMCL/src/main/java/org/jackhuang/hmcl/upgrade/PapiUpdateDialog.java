package org.jackhuang.hmcl.upgrade;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class PapiUpdateDialog extends Dialog<ButtonType> {

    private static final ButtonType BTN_UPDATE = new ButtonType(i18n("papi.update.button.update"), ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType BTN_LATER = new ButtonType(i18n("papi.update.button.later"), ButtonBar.ButtonData.CANCEL_CLOSE);

    private final PapiRemoteVersion remoteVersion;
    private final ProgressBar progressBar;
    private final Label statusLabel;
    private final Node updateButton;
    private volatile boolean downloading;

    public PapiUpdateDialog(PapiRemoteVersion remoteVersion) {
        this.remoteVersion = remoteVersion;

        setTitle(i18n("papi.update.title"));
        getDialogPane().setMinWidth(480);

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        statusLabel = new Label();
        statusLabel.setVisible(false);

        Label infoLabel = new Label(i18n("papi.update.version.info", PapiUpdateChecker.CURRENT_VERSION, remoteVersion.getVersion()));

        VBox content = new VBox(12, infoLabel);

        if (remoteVersion.getBody() != null && !remoteVersion.getBody().isEmpty()) {
            TextArea changelog = new TextArea(remoteVersion.getBody());
            changelog.setEditable(false);
            changelog.setWrapText(true);
            changelog.setPrefHeight(200);
            changelog.setMaxHeight(300);
            content.getChildren().add(changelog);
        } else {
            Label noNotes = new Label(i18n("papi.update.no_notes"));
            noNotes.setStyle("-fx-text-fill: -papi-text-tertiary; -fx-font-style: italic;");
            content.getChildren().add(noNotes);
        }

        content.getChildren().addAll(progressBar, statusLabel);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(BTN_UPDATE, BTN_LATER);

        Node updateBtn = getDialogPane().lookupButton(BTN_UPDATE);
        if (updateBtn != null) {
            updateBtn.getStyleClass().add("dialog-accept");
            updateBtn.addEventFilter(ActionEvent.ACTION, e -> {
                if (downloading) return;
                e.consume();
                startDownload();
            });
        }
        updateButton = updateBtn;

        Node laterBtn = getDialogPane().lookupButton(BTN_LATER);
        if (laterBtn != null) {
            laterBtn.getStyleClass().add("dialog-cancel");
        }

        setResultConverter(btn -> btn == BTN_LATER ? btn : null);
    }

    private void startDownload() {
        if (downloading) return;
        downloading = true;

        if (updateButton != null) {
            updateButton.setDisable(true);
        }
        Node laterButton = getDialogPane().lookupButton(BTN_LATER);
        if (laterButton != null) laterButton.setDisable(true);

        progressBar.setVisible(true);
        progressBar.setProgress(0);
        statusLabel.setVisible(true);
        statusLabel.setText(i18n("papi.update.connecting"));

        Thread downloadThread = new Thread(() -> {
            Path tempFile = null;
            HttpURLConnection conn = null;

            try {
                if (getClass().getProtectionDomain().getCodeSource() == null
                        || getClass().getProtectionDomain().getCodeSource().getLocation() == null) {
                    Platform.runLater(() -> {
                        statusLabel.setText(i18n("papi.update.cannot_determine_jar"));
                        reenableButtons();
                    });
                    return;
                }

                URI jarUri = getClass().getProtectionDomain()
                        .getCodeSource().getLocation().toURI();
                Path currentLocation = Paths.get(jarUri);

                if (!Files.isRegularFile(currentLocation)) {
                    Platform.runLater(() -> {
                        statusLabel.setText(i18n("papi.update.jar_not_found"));
                        reenableButtons();
                    });
                    return;
                }

                Path targetDir = currentLocation.getParent();
                String jarName = "papi-launcher-" + remoteVersion.getVersion() + ".jar";
                Path targetFile = targetDir.resolve(jarName);
                tempFile = targetDir.resolve("." + jarName + ".tmp");

                conn = (HttpURLConnection) new URL(remoteVersion.getDownloadUrl()).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);

                long totalBytes = conn.getContentLengthLong();

                Platform.runLater(() -> {
                    if (totalBytes <= 0) {
                        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                    }
                    statusLabel.setText(i18n("papi.update.downloading"));
                });

                try (InputStream in = conn.getInputStream();
                     OutputStream out = Files.newOutputStream(tempFile)) {

                    byte[] buffer = new byte[8192];
                    int read;
                    long downloaded = 0;
                    long lastUiUpdate = 0;

                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        downloaded += read;

                        if (downloaded - lastUiUpdate > 65536) {
                            lastUiUpdate = downloaded;
                            final long d = downloaded;
                            final long t = totalBytes;
                            Platform.runLater(() -> {
                                if (t > 0) {
                                    progressBar.setProgress((double) d / t);
                                }
                                statusLabel.setText(i18n("papi.update.download_progress", formatSize(d), formatSize(t)));
                            });
                        }
                    }
                }

                conn.disconnect();
                conn = null;

                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                tempFile = null;

                Platform.runLater(() ->
                        statusLabel.setText(i18n("papi.update.downloaded_restarting")));

                Thread.sleep(2000);

                String javaBin = ProcessHandle.current().info().command()
                        .orElseGet(() -> System.getProperty("java.home")
                                + java.io.File.separator + "bin" + java.io.File.separator + "java");

                try {
                    new ProcessBuilder(javaBin, "-jar", targetFile.toAbsolutePath().toString())
                            .inheritIO()
                            .start();
                } catch (IOException e) {
                    Platform.runLater(() -> {
                        statusLabel.setText(i18n("papi.update.downloaded_manual_restart", targetFile.toAbsolutePath()));
                        reenableButtons();
                    });
                    return;
                }

                Platform.runLater(() -> {
                    Platform.exit();
                    System.exit(0);
                });

            } catch (Exception e) {
                LOG.warning("Download failed", e);
                Platform.runLater(() -> {
                    statusLabel.setStyle("-fx-text-fill: -papi-error;");
                    statusLabel.setText(i18n("papi.update.error", e.getMessage()));
                    reenableButtons();
                });
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                    }
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });

        downloadThread.setDaemon(false);
        downloadThread.setName("papi-download");
        downloadThread.start();
    }

    private void reenableButtons() {
        downloading = false;
        if (updateButton != null) {
            updateButton.setDisable(false);
        }
        Node laterButton = getDialogPane().lookupButton(BTN_LATER);
        if (laterButton != null) laterButton.setDisable(false);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String prefix = "KMGTPE".charAt(exp - 1) + "iB";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), prefix);
    }
}
