package com.solarclient.mod.mixin;

import com.solarclient.mod.client.Preload;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Faster launch: create the game window hidden and windowed (see {@link Preload}). */
@Mixin(Window.class)
public abstract class WindowMixin {
    @Shadow private boolean fullscreen;
    @Shadow private boolean currentFullscreen;

    @ModifyArg(method = "<init>",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"),
            index = 3)
    private long solarclient$createHidden(long monitor) {
        if (!Preload.isHidden()) return monitor;
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_TRUE);
        return 0L;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void solarclient$deferFullscreen(CallbackInfo ci) {
        if (!Preload.isHidden() || !this.fullscreen) return;
        Preload.fullscreenOnReveal = true;
        this.fullscreen = false;
        this.currentFullscreen = false;
    }
}
