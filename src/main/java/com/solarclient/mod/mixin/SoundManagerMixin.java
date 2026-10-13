package com.solarclient.mod.mixin;

import com.solarclient.mod.client.Preload;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Faster launch: a game waiting hidden in the background makes no sound. */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void solarclient$silentWhileHidden(SoundInstance sound, CallbackInfoReturnable<SoundSystem.PlayResult> cir) {
        if (Preload.isHidden()) cir.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
    }
}
