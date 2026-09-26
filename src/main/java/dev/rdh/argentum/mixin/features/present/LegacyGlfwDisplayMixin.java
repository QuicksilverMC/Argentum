package dev.rdh.argentum.mixin.features.present;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.lwjgl.glfw.GLFWVidMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import dev.rdh.argentum.impl.render.PresentationPacer;

import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;

@Mixin(targets = "org.lwjgl.opengl.GLFWDisplay", remap = false)
public abstract class LegacyGlfwDisplayMixin {
    @Unique
    private final PresentationPacer argentum$pacer = new PresentationPacer();

    @Shadow
    private long getPrimaryMonitor() {
        return 0;
    }

    @WrapOperation(method = {"update", "swapBuffers"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V"))
    private void argentum$decouplePresentation(long window, Operation<Void> original) {
        long monitor = this.getPrimaryMonitor();
        GLFWVidMode mode = monitor != 0 ? glfwGetVideoMode(monitor) : null;
        if (mode == null || this.argentum$pacer.shouldPresent(mode.refreshRate())) {
            original.call(window);
        }
    }
}
