package com.solarclient.mod.mixin;

import com.solarclient.mod.client.gui.LiquidGlassButtonPainter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Icon buttons skip {@code drawButton}; paint the same frosted textured
 * surface underneath so they still match menu buttons. No {@code @Shadow}.
 */
@Mixin(TexturedButtonWidget.class)
public abstract class TexturedButtonWidgetMixin {

    @Inject(method = "drawIcon(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"))
    private void solarclient$glassUnderIcon(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        TexturedButtonWidget self = (TexturedButtonWidget) (Object) this;
        boolean hover = self.isHovered() || self.isSelected();
        LiquidGlassButtonPainter.paint(
                context,
                self.getX(),
                self.getY(),
                self.getWidth(),
                self.getHeight(),
                hover);
    }
}
