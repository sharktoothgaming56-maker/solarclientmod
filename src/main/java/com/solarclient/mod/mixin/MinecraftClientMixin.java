package com.solarclient.mod.mixin;

import com.solarclient.mod.client.Preload;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Faster launch: closing the window or pressing Quit hides the game instead (see {@link Preload}). */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "scheduleStop", at = @At("HEAD"), cancellable = true)
    private void solarclient$hideInsteadOfQuit(CallbackInfo ci) {
        if (Preload.interceptStop((MinecraftClient) (Object) this)) ci.cancel();
    }
}
