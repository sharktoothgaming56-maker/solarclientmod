package com.solarclient.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.solarclient.mod.client.render.OcclusionCuller;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Behind-walls culling for chests, signs, banners and other block entities.
 * Sodium builds its block-entity list through getRenderState and skips a
 * null result, so treating "hidden" like "too far away" covers both paths.
 */
@Mixin(BlockEntityRenderManager.class)
public abstract class BlockEntityRenderManagerMixin {
    @WrapOperation(
        method = "getRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;isInRenderDistance(Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/util/math/Vec3d;)Z"),
        require = 0
    )
    private <E extends BlockEntity> boolean solarclient$cullBehindWalls(BlockEntityRenderer<E, ?> renderer, E blockEntity, Vec3d cameraPos, Operation<Boolean> original) {
        if (!original.call(renderer, blockEntity, cameraPos)) return false;
        // Beacon beams, end gateways etc. draw far outside their block.
        if (renderer.rendersOutsideBoundingBox()) return true;
        return OcclusionCuller.isBlockEntityVisible(blockEntity);
    }
}
