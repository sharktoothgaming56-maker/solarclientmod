package com.solarclient.mod.client.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * Square menu buttons — frosted textured fill, thick black stroke.
 * Same stroke + label style as before; only the body texture changed.
 */
public final class LiquidGlassButtonPainter {
    private LiquidGlassButtonPainter() {}

    /** Frosted translucent charcoal glass. */
    public static final int FILL = 0x99181818;
    public static final int FILL_HOVER = 0xB02A2A2A;
    public static final int STROKE = 0xFF000000;
    public static final int TEXT = 0xFFFFFFFF;
    /** Border thickness in pixels. */
    public static final int STROKE_PX = 2;

    /** Sharp square corners. */
    public static int radius(int w, int h) {
        return 0;
    }

    public static void paint(DrawContext ctx, int x, int y, int w, int h, boolean hover) {
        if (w < 4 || h < 4) return;

        int s = STROKE_PX;

        // Soft drop shadow
        ctx.fill(x + 1, y + 2, x + w + 1, y + h + 2, 0x33000000);

        // Frosted body
        ctx.fill(x, y, x + w, y + h, hover ? FILL_HOVER : FILL);

        // Soft vertical gradient (lighter top → darker bottom)
        int mid = Math.max(1, h / 2);
        ctx.fill(x + s, y + s, x + w - s, y + s + mid, hover ? 0x28FFFFFF : 0x18FFFFFF);
        ctx.fill(x + s, y + s + mid, x + w - s, y + h - s, 0x22000000);

        GuiBakes.grain(ctx, x + s, y + s, Math.max(0, w - s * 2), Math.max(0, h - s * 2));

        // Thick black stroke (top / bottom / left / right)
        ctx.fill(x, y, x + w, y + s, STROKE);
        ctx.fill(x, y + h - s, x + w, y + h, STROKE);
        ctx.fill(x, y, x + s, y + h, STROKE);
        ctx.fill(x + w - s, y, x + w, y + h, STROKE);
    }

}
