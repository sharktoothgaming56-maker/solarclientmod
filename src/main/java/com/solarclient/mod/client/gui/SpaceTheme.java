package com.solarclient.mod.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import com.solarclient.mod.client.config.SolarConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole space look, shared by every SolarClient screen:
 *  - deterministic twinkling starfield (positions seeded per-index so the
 *    sky doesn't reshuffle every frame; only brightness animates)
 *  - two soft glowing planets (layered translucent discs)
 *  - a shooting star every few seconds (time-derived, no state needed)
 *  - Shared liquid-glass button surface (SolarButton + every vanilla PressableWidget)
 *
 * Everything here uses only ctx.fill / fillGradient / text drawing —
 * the APIs already proven working in this project — deliberately
 * avoiding ButtonWidget rendering overrides and the reworked
 * mouseClicked(Click) API that broke earlier. Click handling uses raw
 * GLFW polling, the same technique HudEditorScreen already runs on.
 */
public final class SpaceTheme {
    private SpaceTheme() {}

    /** Fully opaque variant (title screen). */
    public static void paintOpaque(DrawContext ctx, int w, int h) {
        ctx.fillGradient(0, 0, w, h, 0xFF2a1440, 0xFF08050F);
        paintSky(ctx, w, h, 1f);
    }

    /** Translucent variant (in-game menus — world stays faintly visible). */
    public static void paintOverlay(DrawContext ctx, int w, int h) {
        ctx.fillGradient(0, 0, w, h, 0xE0241033, 0xF008050F);
        paintSky(ctx, w, h, 0.8f);
    }

    /** Dim variant (HUD editor — needs to stay out of the way). */
    public static void paintDim(DrawContext ctx, int w, int h) {
        ctx.fillGradient(0, 0, w, h, 0xB01a0e28, 0xC008050F);
        paintSky(ctx, w, h, 0.4f);
    }

    private static void paintSky(DrawContext ctx, int w, int h, float intensity) {
        long time = System.currentTimeMillis();

        // Planets: a big warm one top-right, a small purple one mid-left.
        drawGlowDisc(ctx, (int) (w * 0.86), (int) (h * 0.12), 34, 255, 190, 120, 0.35f * intensity);
        drawGlowDisc(ctx, (int) (w * 0.10), (int) (h * 0.68), 13, 157, 107, 255, 0.45f * intensity);

        // Stars — ~1 per 4000 px², deterministic positions, twinkling alpha.
        int count = Math.max(30, (w * h) / 4000);
        for (int i = 0; i < count; i++) {
            long hash = (i * 2654435761L) ^ 0x9E3779B97F4A7C15L;
            int x = (int) (Math.abs(hash) % w);
            int y = (int) (Math.abs(hash >> 20) % h);
            int size = (Math.abs((int) (hash >> 40)) % 5 == 0) ? 2 : 1;
            float twinkle = (float) (0.55 + 0.45 * Math.sin(time / 400.0 + i * 1.7));
            int alpha = (int) (200 * twinkle * intensity);
            if (alpha <= 8) continue;
            ctx.fill(x, y, x + size, y + size, (alpha << 24) | 0xE6E1F7);
        }

        // Shooting star: one every ~3.5s, streaking for the first 0.7s of
        // its cycle. Derived purely from the clock, so no state to manage.
        long period = 3500;
        long cycle = time / period;
        float progress = (time % period) / 700f;
        if (progress < 1f) {
            long seed = cycle * 6364136223846793005L + 1442695040888963407L;
            int startX = (int) (Math.abs(seed) % (w * 3 / 4));
            int startY = (int) (Math.abs(seed >> 24) % (h / 3));
            float px = startX + progress * (w * 0.35f);
            float py = startY + progress * (h * 0.22f);
            for (int t = 0; t < 10; t++) {
                int alpha = (int) (220 * (1f - progress) * (1f - t / 10f) * intensity);
                if (alpha <= 8) break;
                int sx = (int) (px - t * 3.2f);
                int sy = (int) (py - t * 2.0f);
                ctx.fill(sx, sy, sx + 2, sy + 2, (alpha << 24) | 0xFFFFFF);
            }
        }
    }

    /** A soft glowing disc: concentric translucent circles, baked into one texture. */
    public static void drawGlowDisc(DrawContext ctx, int cx, int cy, int radius, int r, int g, int b, float coreAlpha) {
        GuiBakes.glowDisc(ctx, cx, cy, radius, r, g, b, coreAlpha);
    }

    /** Filled circle via horizontal strips — no circle primitive needed. */
    public static void fillDisc(DrawContext ctx, int cx, int cy, int radius, int argb) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
            ctx.fill(cx - half, cy + dy, cx + half, cy + dy + 1, argb);
        }
    }


    /**
     * Shared liquid-glass surface used by every SolarButton AND every vanilla
     * PressableWidget (via PressableWidgetMixin). Launcher Launch-button look.
     */
    public static void paintSharedGlassButton(DrawContext ctx, int x, int y, int w, int h) {
        paintSharedGlassButton(ctx, x, y, w, h, false);
    }

    public static void paintSharedGlassButton(DrawContext ctx, int x, int y, int w, int h, boolean hover) {
        LiquidGlassButtonPainter.paint(ctx, x, y, w, h, hover);
    }

    // =================================================================
    // SolarButton — launcher liquid-glass UI control (not MC textures)
    // =================================================================
    public static class SolarButton {
        public int x, y, width, height;
        private String label;
        private Runnable action;
        public boolean bold;

        private static int cornerRadius(int w, int h) {
            return LiquidGlassButtonPainter.radius(w, h);
        }

        public SolarButton(int x, int y, int width, int height, String label, Runnable action) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.label = label; this.action = action;
        }

        public void setLabel(String label) { this.label = label; }
        public void setAction(Runnable action) { this.action = action; }

        public boolean isHovered(int mouseX, int mouseY) {
            if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
            int r = cornerRadius(width, height);
            int lx = mouseX - x;
            int ly = mouseY - y;
            if (lx < r && ly < r) return cornerHit(lx, ly, r);
            if (lx >= width - r && ly < r) return cornerHit(width - 1 - lx, ly, r);
            if (lx < r && ly >= height - r) return cornerHit(lx, height - 1 - ly, r);
            if (lx >= width - r && ly >= height - r) return cornerHit(width - 1 - lx, height - 1 - ly, r);
            return true;
        }

        private static boolean cornerHit(int lx, int ly, int r) {
            float dx = (lx + 0.5f) - r;
            float dy = (ly + 0.5f) - r;
            return dx * dx + dy * dy <= (float) r * r;
        }

        public void render(DrawContext ctx, MinecraftClient client, int mouseX, int mouseY) {
            boolean hover = isHovered(mouseX, mouseY);
            int drawY = hover ? y - 2 : y;
            LiquidGlassButtonPainter.paint(ctx, x, drawY, width, height, hover);
            LaunchButtonFont.drawWidgetLabel(ctx, x, drawY, width, height, Text.literal(label));
        }

        public void click() { if (action != null) action.run(); }
    }

    // =================================================================
    // SpaceScreen — base for all SolarClient screens: paints the theme,
    // owns a SolarButton list, and does GLFW edge-detected clicking.
    // =================================================================
    public abstract static class SpaceScreen extends Screen {
        protected final List<SolarButton> solarButtons = new ArrayList<>();
        // Starts true so a click held over from the previous screen can't
        // instantly trigger a button sitting under the cursor.
        private boolean wasMouseDown = true;
        protected boolean justPressed = false; // exposed for subclasses with extra custom hit areas

        protected SpaceScreen(Text title) { super(title); }

        // ----- Responsive layout helpers ---------------------------------
        // Every SolarClient menu sizes its buttons off these instead of a
        // hardcoded 200px, so buttons grow/shrink and stay centred when the
        // window is resized. init() re-runs on resize, so reading these
        // there keeps the layout correct at any window size.

        /** Full-width button width — scales with the window. */
        protected int panelWidth() {
            int w = Math.max(160, Math.min(280, (int) (this.width * 0.32f)));
            return w & ~1;
        }

        /** Shared responsive button height for Solar menus. */
        protected int buttonHeight() {
            return Math.max(20, Math.min(26, this.height / 30));
        }

        /** Row pitch (button + gap), scales with window. */
        protected int rowStride() {
            return buttonHeight() + Math.max(5, this.height / 80);
        }

        /** Left x for a centred full-width button (identical left/right inset). */
        protected int panelX() {
            return (this.width - panelWidth()) / 2;
        }

        /** Half-width (for two-column rows), accounting for a 4px gutter. */
        protected int halfWidth() {
            return (panelWidth() - 4) / 2;
        }

        /** x of the left column in a two-column row. */
        protected int leftColX() {
            return panelX();
        }

        /** x of the right column in a two-column row. */
        protected int rightColX() {
            return panelX() + halfWidth() + 4;
        }

        /** true = opaque space background (title screen); false = translucent overlay. */
        protected boolean opaqueBackground() { return false; }

        /**
         * Whether the bottom-right SOLARCLIENT wordmark is drawn on this
         * screen. Defaults on; the title screen and the Solar Menu turn it
         * off because their artwork already carries the branding.
         */
        protected boolean showWordmark() { return true; }

        /**
         * Optional Essential-style icon rail down the far right edge.
         * Screens opt in by assigning this in init(); null means no rail.
         * Lives on the base class so the click plumbing is written once
         * and every screen that wants one behaves identically.
         */
        protected SolarSideRail sideRail;

        protected abstract void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta);

        /**
         * render() paints its own full-screen background, so vanilla's
         * panorama, full-resolution blur and darkening pass underneath it were
         * pure GPU cost hidden behind a 90%-opaque overlay.
         */
        @Override
        public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            if (opaqueBackground()) SpaceTheme.paintOpaque(ctx, this.width, this.height);
            else SpaceTheme.paintOverlay(ctx, this.width, this.height);

            boolean mouseDown = GLFW.glfwGetMouseButton(this.client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            justPressed = mouseDown && !wasMouseDown;
            wasMouseDown = mouseDown;

            // Iterate a SNAPSHOT, not the live list. A button's click can
            // call clearAndInit() (e.g. opening a different conversation),
            // which clears and refills solarButtons — mutating it mid
            // for-each would throw ConcurrentModificationException and crash
            // the game. Render over the snapshot, then fire at most one
            // click and stop, since the list may no longer be valid after.
            List<SolarButton> snapshot = new ArrayList<>(solarButtons);
            for (SolarButton b : snapshot) {
                b.render(ctx, this.client, mouseX, mouseY);
            }
            if (justPressed) {
                for (SolarButton b : snapshot) {
                    if (b.isHovered(mouseX, mouseY)) { b.click(); break; }
                }
            }

            renderContent(ctx, mouseX, mouseY, delta);

            // Rail draws after content so its tooltip floats over
            // everything, and is click-tested BEFORE the centre stack
            // would ever see the same press.
            if (sideRail != null) {
                sideRail.layout(this.width, this.height);
                sideRail.render(ctx, mouseX, mouseY);
                if (justPressed) sideRail.mouseClicked(mouseX, mouseY, 0);
            }

            // Solar Client brand wordmark, bottom-right (screens can opt out).
            if (showWordmark()) {
            int markW = Math.min(160, Math.max(96, this.width / 7));
            int markH = SolarLogos.brandHeightForWidth(markW);
            int mx = this.width - markW - 8;
            int my = this.height - markH - 6;
            SolarLogos.drawBrand(ctx, mx, my, markW);
            }

            super.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPause() { return false; }
    }
}
