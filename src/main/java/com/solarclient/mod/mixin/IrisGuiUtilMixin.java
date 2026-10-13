package com.solarclient.mod.mixin;

import com.solarclient.mod.client.gui.LiquidGlassButtonPainter;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris draws its list-row and shader-option buttons with its own texture via
 * {@code GuiUtil.drawButton}; swap in the SolarClient button body so they
 * match the rest of the game. Iris draws their text itself, on top.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gui.GuiUtil", remap = false)
public abstract class IrisGuiUtilMixin {

    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void solarclient$solarButtonBody(DrawContext context, int x, int y, int width, int height,
                                                    boolean hovered, boolean disabled, CallbackInfo ci) {
        LiquidGlassButtonPainter.paint(context, x, y, width, height, hovered && !disabled);
        ci.cancel();
    }
}
