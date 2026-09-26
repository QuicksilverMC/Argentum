package dev.rdh.argentum.extras.mixin;

import dev.rdh.argentum.extras.ArgentumExtras;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

@Mixin(targets = "io.github.moehreag.legacylwjgl3.implementation.glfw.GLFWMouseImplementation", remap = false)
public abstract class LegacyGlfwMouseMixin {
    @Unique
    private boolean argentum$ctrlClicking;

    @ModifyArg(method = "createMouse", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFWMouseButtonCallback;create(Lorg/lwjgl/glfw/GLFWMouseButtonCallbackI;)Lorg/lwjgl/glfw/GLFWMouseButtonCallback;"))
    private GLFWMouseButtonCallbackI argentum$emulateRightClick(GLFWMouseButtonCallbackI callback) {
        if (!ArgentumExtras.CONFIG.macosRightClickEmulation || Platform.get() != Platform.MACOSX) {
            return callback;
        }
        return (window, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    this.argentum$ctrlClicking = (mods & GLFW_MOD_CONTROL) != 0;
                }
                if (this.argentum$ctrlClicking) {
                    button = GLFW_MOUSE_BUTTON_RIGHT;
                }
            }
            callback.invoke(window, button, action, mods);
        };
    }
}
