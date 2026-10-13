package com.solarclient.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.solarclient.mod.client.render.OcclusionCuller;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds the behind-walls culling: once per frame for the camera, once per entity for the check. */
@Mixin(EntityRenderManager.class)
public abstract class EntityRenderManagerMixin {
    @Inject(method = "configure", at = @At("TAIL"))
    private void solarclient$beginCullFrame(Camera camera, Entity targeted, CallbackInfo ci) {
        OcclusionCuller.beginFrame(camera);
    }

    // Runs after vanilla (and Sodium) said yes; only ever turns a yes into a no.
    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"), require = 0)
    private boolean solarclient$cullBehindWalls(boolean visible, @Local(argsOnly = true) Entity entity) {
        return visible && OcclusionCuller.isEntityVisible(entity);
    }
}
