package com.solarclient.mod.mixin;

import com.solarclient.mod.client.Preload;
import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Faster launch: menu music waits until the game is actually shown. */
@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void solarclient$noMusicWhileHidden(CallbackInfo ci) {
        if (Preload.isHidden()) ci.cancel();
    }
}
