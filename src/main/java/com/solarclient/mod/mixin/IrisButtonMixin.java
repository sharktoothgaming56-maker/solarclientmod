package com.solarclient.mod.mixin;

import com.solarclient.mod.client.gui.LaunchButtonFont;
import com.solarclient.mod.client.gui.LiquidGlassButtonPainter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris's own buttons (Done / Apply / Cancel / Open Folder / Settings on the
 * shader screen) override {@code drawIcon} with Iris's widget texture, so
 * they skipped {@link PressableWidgetMixin}'s body — yet still hit its
 * cancelled {@code drawLabel}, which left them blank. Paint them exactly
 * like every other button instead.
 *
 * Iris is optional: {@link Pseudo} plus {@code require = 0} make this a no-op
 * without it. Names are intermediary ({@code method_75752} = drawIcon) because
 * the target class is not part of Minecraft and cannot be remapped.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gui.element.screen.IrisButton", remap = false)
public abstract class IrisButtonMixin {

    @Inject(method = "method_75752", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void solarclient$paintLikeEveryButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        PressableWidget self = (PressableWidget) (Object) this;
        // Iris fades these out when you hide the GUI to preview a pack.
        if (self.getAlpha() < 0.05f) { ci.cancel(); return; }
        boolean hover = self.isHovered() || self.isSelected() || self.isFocused();
        LiquidGlassButtonPainter.paint(context, self.getX(), self.getY(), self.getWidth(), self.getHeight(), hover);
        LaunchButtonFont.drawWidgetLabel(context, self.getX(), self.getY(), self.getWidth(), self.getHeight(), self.getMessage());
        ci.cancel();
    }
}
