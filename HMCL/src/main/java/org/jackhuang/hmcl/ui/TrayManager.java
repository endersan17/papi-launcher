package org.jackhuang.hmcl.ui;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.Metadata;

import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;

public final class TrayManager {
    private static TrayIcon trayIcon;
    private static boolean initialized = false;

    private TrayManager() {}

    public static void initialize(Stage stage) {
        if (!SystemTray.isSupported()) return;

        SwingUtilities.invokeLater(() -> {
            if (initialized) return;

            try {
                URL imageUrl = TrayManager.class.getResource("/assets/img/icon-hide_show.png");
                if (imageUrl == null) return;

                Image image = Toolkit.getDefaultToolkit().getImage(imageUrl);

                PopupMenu popup = new PopupMenu();

                MenuItem showItem = new MenuItem(i18n("Show"));
                showItem.addActionListener(e -> Platform.runLater(() -> {
                    stage.show();
                    stage.toFront();
                }));

                MenuItem hideItem = new MenuItem(i18n("Hide"));
                hideItem.addActionListener(e -> Platform.runLater(stage::hide));

                MenuItem exitItem = new MenuItem(i18n("Exit"));
                exitItem.addActionListener(e -> Platform.runLater(Launcher::stopApplication));

                popup.add(showItem);
                popup.add(hideItem);
                popup.addSeparator();
                popup.add(exitItem);

                trayIcon = new TrayIcon(image, Metadata.FULL_TITLE, popup);
                trayIcon.setImageAutoSize(true);

                trayIcon.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getButton() == MouseEvent.BUTTON1) {
                            Platform.runLater(() -> {
                                stage.show();
                                stage.toFront();
                            });
                        }
                    }
                });

                SystemTray.getSystemTray().add(trayIcon);
                initialized = true;
            } catch (Exception ignored) {
            }
        });
    }

    public static void removeTrayIcon() {
        SwingUtilities.invokeLater(() -> {
            if (trayIcon != null && SystemTray.isSupported()) {
                SystemTray.getSystemTray().remove(trayIcon);
                trayIcon = null;
                initialized = false;
            }
        });
    }

    private static String i18n(String key) {
        try {
            return org.jackhuang.hmcl.util.i18n.I18n.i18n("tray." + key.toLowerCase());
        } catch (Exception e) {
            return key;
        }
    }
}
