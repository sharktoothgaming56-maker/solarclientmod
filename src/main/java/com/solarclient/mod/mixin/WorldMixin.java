package com.solarclient.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.solarclient.mod.client.render.OcclusionCuller;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tells the behind-walls culling a block changed, so a wall that just opened up is re-checked. */
@Mixin(World.class)
public abstract class WorldMixin {
    @ModifyReturnValue(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("RETURN"),
        require = 0
    )
    private boolean solarclient$wallChanged(boolean changed) {
        if (changed && ((World) (Object) this).isClient()) OcclusionCuller.blockChanged();
        return changed;
    }
}
