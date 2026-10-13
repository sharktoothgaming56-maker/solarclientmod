package com.solarclient.mod.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.TitleScreen;

/** Plays the intro video outside of a resource reload (see {@link IntroPlayback#replay}). */
public class IntroOverlay extends Overlay {
    private final MinecraftClient client;

    public IntroOverlay(MinecraftClient client) {
        this.client = client;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int w = this.client.getWindow().getScaledWidth();
        int h = this.client.getWindow().getScaledHeight();
        IntroPlayback.renderVideoFrame(context, w, h);
        if (IntroPlayback.isVideoFinished()) {
            this.client.setOverlay(null);
            // Opened as vanilla's so the usual swap carries other mods' buttons across.
            this.client.setScreen(new TitleScreen());
        }
    }
}
