package com.solarclient.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.solarclient.mod.client.Preload;
import com.solarclient.mod.client.gui.SpaceTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Caps the frame rate at 60 while a SolarClient menu is open over a world,
 * the same cap vanilla already applies to its own title-screen menus.
 *
 * Behind a Solar menu the world is still rendered every frame, at whatever
 * uncapped rate the GPU can manage, only to be covered by a 90%-opaque
 * overlay. That turned opening a menu into a full-load GPU and CPU spike.
 */
@Mixin(InactivityFpsLimiter.class)
public abstract class InactivityFpsLimiterMixin {
    private static final int MENU_FPS = 60;
    private static final int HIDDEN_FPS = 10;

    @ModifyReturnValue(method = "update", at = @At("RETURN"), require = 0)
    private int solarclient$capBehindMenus(int limit) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Loading still runs at the normal rate so a preload finishes as fast as a real launch.
        if (Preload.isHidden() && client.getOverlay() == null) {
            return Math.min(limit, HIDDEN_FPS);
        }
        if (client.world != null && client.currentScreen instanceof SpaceTheme.SpaceScreen) {
            return Math.min(limit, MENU_FPS);
        }
        return limit;
    }
}
