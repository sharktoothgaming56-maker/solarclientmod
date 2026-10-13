package com.solarclient.mod.client.hud;

import com.solarclient.mod.client.config.SolarConfig;
import com.solarclient.mod.client.gui.SolarLogos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;

/**
 * The Solar Client brand wordmark, PINNED to the bottom-left. It is not
 * part of the HUD editor and cannot be moved or resized — the only control
 * is the "SolarClient Logo" ON/OFF toggle in the Solar Menu, per the
 * brief. It still hides itself while chat/commands are open, while F3 is
 * up, while TAB (player list) is held, and on SolarClient's own menus
 * (which draw their own wordmark).
 */
public class LogoHud {
    public static final String ID = "logo";

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!SolarConfig.get().isHudEnabled(ID)) return;
        if (client.currentScreen instanceof ChatScreen) return;
        if (client.currentScreen == null && client.getDebugHud().shouldShowDebugHud()) return;
        if (client.currentScreen instanceof com.solarclient.mod.client.gui.SpaceTheme.SpaceScreen) return;
        if (HudStyle.isPlayerListOpen()) return;

        float scale = SolarConfig.get().getLogoScale();
        // Config default is 0.5 (text-era); brand PNG needs a larger base width.
        int markW = Math.max(56, Math.round(120 * scale));
        int markH = SolarLogos.brandHeightForWidth(markW);
        int height = client.getWindow().getScaledHeight();
        SolarLogos.drawBrand(ctx, 4, height - markH - 4, markW);
    }
}
