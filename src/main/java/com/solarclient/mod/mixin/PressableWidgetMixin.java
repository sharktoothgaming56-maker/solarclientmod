package com.solarclient.mod.mixin;

import com.solarclient.mod.client.gui.LaunchButtonFont;
import com.solarclient.mod.client.gui.LiquidGlassButtonPainter;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replace vanilla widgets.png with frosted textured Launch-style buttons,
 * and redraw labels in black Segoe-weight text (bundled launch font).
 */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {

    @Inject(method = "drawButton(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
    private void solarclient$liquidGlassButton(DrawContext context, CallbackInfo ci) {
        PressableWidget self = (PressableWidget) (Object) this;
        boolean hover = self.isHovered() || self.isSelected();
        if (((ClickableWidget) (Object) this).isFocused()) hover = true;
        LiquidGlassButtonPainter.paint(
                context,
                self.getX(),
                self.getY(),
                self.getWidth(),
                self.getHeight(),
                hover);
        // Draw label here so we control color/font; drawLabel is cancelled below.
        LaunchButtonFont.drawWidgetLabel(
                context,
                self.getX(),
                self.getY(),
                self.getWidth(),
                self.getHeight(),
                self.getMessage());
        ci.cancel();
    }

    @Inject(method = "drawLabel(Lnet/minecraft/client/font/DrawnTextConsumer;)V", at = @At("HEAD"), cancellable = true)
    private void solarclient$skipVanillaLabel(DrawnTextConsumer drawer, CallbackInfo ci) {
        ci.cancel();
    }
}
