package com.solarclient.mod.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.ObjectTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * Launch-button label look: Segoe UI Black, black ink, drawn as one string
 * so the face's natural spacing stays clean (no per-glyph tracking gaps).
 */
public final class LaunchButtonFont {
    private LaunchButtonFont() {}

    public static final Identifier FONT_ID = Identifier.of("solarclient", "launch");
    public static final int COLOR = LiquidGlassButtonPainter.TEXT;

    public static Text styled(String label) {
        // Already Black (900) — do not also set bold (synthetic bold breaks TTF glyphs).
        return Text.literal(label == null ? "" : label)
                .setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(FONT_ID)));
    }

    public static void drawCentered(DrawContext ctx, TextRenderer tr, String label, int cx, int cy) {
        int width = measure(tr, label);
        draw(ctx, tr, label, cx - width / 2, cy);
    }

    public static void draw(DrawContext ctx, TextRenderer tr, String label, int x, int y) {
        if (label == null || label.isEmpty()) return;
        ctx.drawText(tr, styled(label), x, y, COLOR, false);
    }

    public static int measure(TextRenderer tr, String label) {
        if (label == null || label.isEmpty()) return 0;
        return tr.getWidth(styled(label));
    }

    public static void drawWidgetLabel(DrawContext ctx, int x, int y, int w, int h, Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || message == null) return;
        if (message.getString().isEmpty()) return;
        TextRenderer tr = client.textRenderer;

        // Keep the real Text: getString() turns player-head objects into
        // "[unknown player head]" and drops colours. Objects keep their own
        // glyph source, so only plain labels get the launch face.
        Text label = containsObject(message)
                ? message
                : message.copy().fillStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(FONT_ID)));

        int tw = tr.getWidth(label);
        int ty = y + (h - tr.fontHeight) / 2;
        int left = x + LABEL_PAD;
        int right = x + w - LABEL_PAD;
        int avail = right - left;
        if (tw <= avail) {
            ctx.drawText(tr, label, x + (w - tw) / 2, ty, COLOR, false);
            return;
        }

        // Too wide for the button: clip to it and scroll back and forth
        // (vanilla's scrolling-label timing) instead of spilling over neighbours.
        int overflow = tw - avail;
        double seconds = Util.getMeasuringTimeMs() / 1000.0;
        double period = Math.max(overflow * 0.5, 3.0);
        double phase = Math.sin(Math.PI / 2 * Math.cos(Math.PI * 2 * seconds / period)) / 2.0 + 0.5;
        int offset = (int) Math.round(phase * overflow);
        ctx.enableScissor(left, y, right, y + h);
        ctx.drawText(tr, label, left - offset, ty, COLOR, false);
        ctx.disableScissor();
    }

    private static final int LABEL_PAD = 4;

    private static boolean containsObject(Text text) {
        if (text.getContent() instanceof ObjectTextContent) return true;
        if (text.getContent() instanceof TranslatableTextContent tr) {
            for (Object arg : tr.getArgs()) {
                if (arg instanceof Text t && containsObject(t)) return true;
            }
        }
        for (Text sibling : text.getSiblings()) {
            if (containsObject(sibling)) return true;
        }
        return false;
    }
}
