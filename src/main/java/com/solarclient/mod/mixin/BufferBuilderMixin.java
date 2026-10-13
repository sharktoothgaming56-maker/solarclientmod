package com.solarclient.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.render.BufferBuilder;
import org.lwjgl.system.MemoryUtil;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Stops the "Missing elements in vertex: mc_midTexCoord, at_tangent" crash.
 *
 * With a shader pack on, Iris adds its own attributes to entity vertex
 * formats and fills them in for vanilla's render paths. Mods that build
 * vertices their own way (Essential's cosmetics) never write them, and
 * vanilla throws as soon as the next vertex starts. When the ONLY unwritten
 * attributes are shader ones they are zeroed instead; anything else missing
 * still throws exactly like vanilla.
 *
 * Hooked right at the currentMask check rather than HEAD, so it runs after
 * Iris has had its chance to fill the attributes itself. endVertex runs for
 * every vertex the game draws, so this is a value hook (no CallbackInfo
 * allocation) that returns immediately in the normal mask == 0 case.
 */
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin {
    @Shadow private long vertexPointer;
    @Shadow private int currentMask;
    @Shadow @Final private VertexFormat vertexFormat;
    @Shadow @Final private int[] offsetsByElementId;

    // require = 0: if another mod reshapes endVertex this simply doesn't apply,
    // instead of stopping the game from starting.
    @ModifyExpressionValue(
        method = "endVertex",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/BufferBuilder;currentMask:I", opcode = Opcodes.GETFIELD, ordinal = 0),
        require = 0
    )
    private int solarclient$fillShaderAttributes(int missing) {
        if (missing == 0) return 0;
        List<VertexFormatElement> elements = VertexFormatElement.elementsFromMask(missing).toList();
        for (VertexFormatElement e : elements) {
            if (!isShaderAttribute(this.vertexFormat.getElementName(e))) return missing;
            if (e.id() >= this.offsetsByElementId.length || this.offsetsByElementId[e.id()] < 0) return missing;
        }
        for (VertexFormatElement e : elements) {
            MemoryUtil.memSet(this.vertexPointer + this.offsetsByElementId[e.id()], 0, e.byteSize());
        }
        this.currentMask = 0;
        return 0;
    }

    private static boolean isShaderAttribute(String name) {
        return name != null && (name.startsWith("mc_") || name.startsWith("at_") || name.startsWith("iris_"));
    }
}
