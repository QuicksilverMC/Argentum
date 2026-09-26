package dev.rdh.argentum.mixin.features.present;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.lwjgl.sdl.SDL_DisplayMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import dev.rdh.argentum.impl.render.PresentationPacer;

import static org.lwjgl.sdl.SDLVideo.SDL_GetCurrentDisplayMode;
import static org.lwjgl.sdl.SDLVideo.SDL_GetDisplayForWindow;

@Mixin(targets = "org.lwjgl.opengl.SDLDisplay", remap = false)
public abstract class LegacySdlDisplayMixin {
    @Unique
    private final PresentationPacer argentum$pacer = new PresentationPacer();

    @Shadow
    public abstract long getHandle();

    @WrapOperation(method = {"update", "swapBuffers"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_GL_SwapWindow(J)Z"))
    private boolean argentum$decouplePresentation(long window, Operation<Boolean> original) {
        SDL_DisplayMode mode = SDL_GetCurrentDisplayMode(SDL_GetDisplayForWindow(this.getHandle()));
        if (mode != null && !this.argentum$pacer.shouldPresent(mode.refresh_rate())) {
            return true;
        }
        return original.call(window);
    }
}
