package org.jackhuang.hmcl.discord;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscordRPCService {
    private static final DiscordRPCService INSTANCE = new DiscordRPCService();
    private static final String APPLICATION_ID = "1518370604963860612";

    private final DiscordRPC lib = DiscordRPC.INSTANCE;
    private final ExecutorService rpcExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PapiLauncher-RPC-Worker");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private Thread callbackThread;
    private long startTimestamp;

    private DiscordRPCService() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "PapiLauncher-RPC-Shutdown-Hook"));
    }

    public static DiscordRPCService getInstance() {
        return INSTANCE;
    }

    public synchronized void init() {
        if (initialized.get()) return;
        initialized.set(true);
        startTimestamp = System.currentTimeMillis() / 1000;

        rpcExecutor.submit(() -> {
            try {
                DiscordEventHandlers handlers = new DiscordEventHandlers();
                lib.Discord_Initialize(APPLICATION_ID, handlers, true, null);

                callbackThread = new Thread(() -> {
                    while (!Thread.currentThread().isInterrupted() && initialized.get()) {
                        lib.Discord_RunCallbacks();
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }, "PapiLauncher-RPC-Callback");
                callbackThread.setDaemon(true);
                callbackThread.start();

                updatePresenceAsync("En el men\u00fa principal", "Listo para jugar", false);
            } catch (Exception e) {
                System.err.println("[PapiLauncher] Error al inicializar Discord RPC: " + e.getMessage());
                initialized.set(false);
            }
        });
    }

    public synchronized void shutdown() {
        if (!initialized.get()) return;
        initialized.set(false);

        if (callbackThread != null) {
            callbackThread.interrupt();
            callbackThread = null;
        }

        rpcExecutor.submit(() -> {
            try {
                lib.Discord_Shutdown();
            } catch (Exception e) {
                System.err.println("[PapiLauncher] Error al cerrar Discord RPC: " + e.getMessage());
            }
        });
    }

    public void updatePresenceAsync(String line1, String line2, boolean isPlaying) {
        if (!initialized.get()) return;

        rpcExecutor.submit(() -> {
            try {
                DiscordRichPresence presence = new DiscordRichPresence();
                presence.details = line1;
                presence.state = line2;
                presence.startTimestamp = this.startTimestamp;
                presence.largeImageKey = "papi_logo";
                presence.largeImageText = "Papi Launcher";

                if (isPlaying) {
                    presence.smallImageKey = "minecraft";
                    presence.smallImageText = "Minecraft";
                }

                lib.Discord_UpdatePresence(presence);
            } catch (Exception e) {
                System.err.println("[PapiLauncher] Error al actualizar presencia: " + e.getMessage());
            }
        });
    }
}
