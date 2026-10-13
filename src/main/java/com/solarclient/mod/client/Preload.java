package com.solarclient.mod.client;

import com.solarclient.mod.client.gui.IntroPlayback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Faster launch (launcher beta). A game run with it stays open for good:
 * closing or quitting only hides it (back on the title screen, silent, idling),
 * and the launcher shows it again on the next Launch, intro and all.
 *
 * -Dsolarclient.preload=true   start hidden (launcher warms the game up)
 * -Dsolarclient.fastlaunch=true start visible, but hide instead of quitting
 *
 * stdin commands from the launcher: "solarclient:show", "solarclient:hide",
 * "solarclient:keepalive on|off".
 */
public final class Preload {
    public static final boolean SESSION = Boolean.getBoolean("solarclient.preload");
    private static final boolean MANAGED = SESSION || Boolean.getBoolean("solarclient.fastlaunch");

    private static volatile boolean hidden = SESSION;
    private static volatile boolean keepAlive = MANAGED;
    private static boolean quitting;
    /** Fullscreen is applied on reveal; GLFW ignores the hidden hint for fullscreen windows. */
    public static boolean fullscreenOnReveal;
    private static boolean readyAnnounced;
    /** Ticks left before the window is hidden, so a fullscreen exit lands first. */
    private static int hideCountdown = -1;

    private Preload() {}

    public static boolean isHidden() {
        return hidden;
    }

    public static void start() {
        if (!MANAGED) return;
        Thread t = new Thread(Preload::readCommands, "SolarClient preload");
        t.setDaemon(true);
        t.start();
    }

    private static void readCommands() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                String cmd = line.trim();
                MinecraftClient client = MinecraftClient.getInstance();
                if (client == null) continue;
                switch (cmd) {
                    case "solarclient:show" -> client.execute(() -> reveal(client));
                    case "solarclient:hide" -> client.execute(() -> hide(client));
                    case "solarclient:keepalive on" -> keepAlive = true;
                    case "solarclient:keepalive off" -> keepAlive = false;
                    default -> { }
                }
            }
        } catch (IOException ignored) {
            // treated the same as the launcher going away
        }
        // stdin only closes when the launcher exits. Nobody can show a hidden
        // game after that, and a visible one should quit normally from now on.
        keepAlive = false;
        if (hidden) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) client.execute(() -> quit(client));
            else System.exit(0);
        }
    }

    private static void quit(MinecraftClient client) {
        quitting = true;
        client.scheduleStop();
    }

    /** From the scheduleStop hook: true when the stop was turned into a hide. */
    public static boolean interceptStop(MinecraftClient client) {
        if (!keepAlive || quitting) return false;
        GLFW.glfwSetWindowShouldClose(client.getWindow().getHandle(), false);
        hide(client);
        return true;
    }

    /** Called every client tick. */
    public static void tick(MinecraftClient client) {
        if (hideCountdown >= 0 && hideCountdown-- == 0) {
            GLFW.glfwHideWindow(client.getWindow().getHandle());
            SolarClientModClient.LOGGER.info("SOLARCLIENT_HIDDEN");
        }
        if (!hidden || readyAnnounced || !SESSION) return;
        // Usually the title screen, but first-run screens count too.
        if (client.getOverlay() != null || client.currentScreen == null) return;
        readyAnnounced = true;
        SolarClientModClient.LOGGER.info("SOLARCLIENT_PRELOAD_READY");
    }

    private static void hide(MinecraftClient client) {
        if (hidden) return;
        hidden = true;
        if (client.world != null) {
            boolean singleplayer = client.isInSingleplayer();
            client.world.disconnect(Text.translatable("menu.returnToMenu"));
            if (singleplayer) client.disconnectWithSavingScreen();
            else client.disconnectWithProgressScreen();
        }
        client.setScreen(new TitleScreen());
        client.getSoundManager().stopAll();
        client.getMusicTracker().stop();
        Window window = client.getWindow();
        fullscreenOnReveal = window.isFullscreen();
        if (fullscreenOnReveal) {
            window.toggleFullscreen();
            hideCountdown = 3;
        } else {
            hideCountdown = 0;
        }
    }

    private static void reveal(MinecraftClient client) {
        if (!hidden) return;
        hidden = false;
        hideCountdown = -1;
        Window window = client.getWindow();
        long handle = window.getHandle();
        GLFW.glfwShowWindow(handle);
        // Windows may refuse focus to a process that is not in the foreground;
        // a momentary always-on-top still puts the window in front of the launcher.
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE);
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FLOATING, GLFW.GLFW_FALSE);
        GLFW.glfwFocusWindow(handle);
        if (fullscreenOnReveal && !window.isFullscreen()) {
            window.toggleFullscreen();
        }
        com.solarclient.mod.client.social.SolarLink.get().start();
        IntroPlayback.replay(client);
        SolarClientModClient.LOGGER.info("Faster launch: game shown");
    }
}
