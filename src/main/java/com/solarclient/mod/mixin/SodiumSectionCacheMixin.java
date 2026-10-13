package com.solarclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium sweeps its whole cache of copied chunk sections every frame just to
 * drop entries older than five seconds; profiling put that at ~4% of the
 * render thread. Sweeping four times a second instead only means an unused
 * copy can linger a quarter second longer. Cache entries for changed blocks
 * are invalidated separately, so nothing ever renders from a stale copy.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache", remap = false)
public abstract class SodiumSectionCacheMixin {
    @Unique private static final long SWEEP_INTERVAL_NS = 250_000_000L;
    @Unique private long solarclient$lastSweep;

    @Inject(method = "cleanup", at = @At("HEAD"), cancellable = true, require = 0)
    private void solarclient$sweepLessOften(CallbackInfo ci) {
        long now = System.nanoTime();
        if (now - this.solarclient$lastSweep < SWEEP_INTERVAL_NS) {
            ci.cancel();
            return;
        }
        this.solarclient$lastSweep = now;
    }
}
