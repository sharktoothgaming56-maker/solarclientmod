package com.solarclient.mod.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Pre-rendered textures for menu effects that used to be built from
 * thousands of 1px ctx.fill() calls every frame.
 *
 * Since 1.21.6 every fill() allocates a GUI render-state object that the
 * game then sorts, batches and uploads. The button grain alone was ~750
 * fills per button, so a menu with 15 buttons pushed 10,000+ of them a
 * frame — enough to pin a CPU core and flood the garbage collector, which
 * is what made opening the Solar Menu stall the game and the whole PC.
 * Each effect is now drawn once into a texture and blitted as one quad,
 * pixel-for-pixel identical to the old output.
 *
 * Everything here runs on the render thread (called from render code).
 */
public final class GuiBakes {
    private GuiBakes() {}

    // ----- button grain ------------------------------------------------
    private static final Identifier GRAIN = Identifier.of("solarclient", "gui_bake/glass_grain");
    private static final int GRAIN_W = 512, GRAIN_H = 128;
    private static boolean grainReady;

    /** Draws the frosted-glass grain over (x, y, w, h). */
    public static void grain(DrawContext ctx, int x, int y, int w, int h) {
        if (w < 1 || h < 1) return;
        if (!grainReady) {
            register(GRAIN, bakeGrain());
            grainReady = true;
        }
        for (int ty = 0; ty < h; ty += GRAIN_H) {
            for (int tx = 0; tx < w; tx += GRAIN_W) {
                int cw = Math.min(GRAIN_W, w - tx), ch = Math.min(GRAIN_H, h - ty);
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GRAIN, x + tx, y + ty, 0f, 0f,
                        cw, ch, cw, ch, GRAIN_W, GRAIN_H, 0xFFFFFFFF);
            }
        }
    }

    // Same hash walk the per-pixel version used, so the pattern is unchanged.
    private static NativeImage bakeGrain() {
        NativeImage img = new NativeImage(GRAIN_W, GRAIN_H, true);
        for (int gy = 0; gy < GRAIN_H; gy++) {
            int row = gy * 1103515245 + 12345;
            for (int gx = 0; gx < GRAIN_W; ) {
                int n = (row + gx * 1664525) >>> 16;
                int step = 1 + (n & 3);
                if ((n & 7) <= 2) {
                    int a = 8 + (n & 24);
                    int rgb = ((n & 1) == 0) ? 0xFFFFFF : 0x000000;
                    img.setColorArgb(gx, gy, (a << 24) | rgb);
                }
                gx += step;
            }
        }
        return img;
    }

    // ----- glowing planets ---------------------------------------------
    private static final Map<Long, Identifier> GLOWS = new HashMap<>();

    /**
     * A soft glowing disc: four concentric translucent circles of one colour,
     * composited into a single texture (alpha = 1 - Π(1 - layer alpha), which
     * is exactly what stacking the layers with normal blending produced).
     */
    public static void glowDisc(DrawContext ctx, int cx, int cy, int radius, int r, int g, int b, float coreAlpha) {
        int outer = radius + 3 * (radius / 3 + 2);
        int quantAlpha = Math.round(coreAlpha * 1000);
        long key = ((long) radius << 48) ^ ((long) ((r << 16) | (g << 8) | b) << 16) ^ quantAlpha;
        Identifier id = GLOWS.get(key);
        if (id == null) {
            id = Identifier.of("solarclient", "gui_bake/glow_" + Long.toHexString(key));
            register(id, bakeGlow(radius, outer, (r << 16) | (g << 8) | b, coreAlpha));
            GLOWS.put(key, id);
        }
        int size = outer * 2 + 1;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, cx - outer, cy - outer, 0f, 0f,
                size, size, size, size, size, size, 0xFFFFFFFF);
    }

    private static NativeImage bakeGlow(int radius, int outer, int rgb, float coreAlpha) {
        int size = outer * 2 + 1;
        NativeImage img = new NativeImage(size, size, true);
        for (int py = 0; py < size; py++) {
            int dy = py - outer;
            for (int px = 0; px < size; px++) {
                int dx = px - outer;
                double keep = 1.0;
                for (int layer = 3; layer >= 0; layer--) {
                    int lr = radius + layer * (radius / 3 + 2);
                    int alpha = (int) (255 * coreAlpha / (1 + layer * 2));
                    if (alpha <= 4 || Math.abs(dy) > lr) continue;
                    // fillDisc drew each row from cx-half (inclusive) to cx+half (exclusive).
                    int half = (int) Math.round(Math.sqrt((double) lr * lr - (double) dy * dy));
                    if (dx >= -half && dx < half) keep *= 1.0 - alpha / 255.0;
                }
                int a = (int) Math.round((1.0 - keep) * 255);
                if (a > 0) img.setColorArgb(px, py, (a << 24) | rgb);
            }
        }
        return img;
    }

    private static void register(Identifier id, NativeImage img) {
        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(id, new NativeImageBackedTexture(id::toString, img));
    }
}
