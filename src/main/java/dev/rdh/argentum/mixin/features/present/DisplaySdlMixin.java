package dev.rdh.argentum.mixin.features.present;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.lwjgl.sdl.SDL_DisplayMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import dev.rdh.argentum.impl.render.PresentationPacer;
import pl.tomgirl.pylon.window.DisplaySdl;

import static org.lwjgl.sdl.SDLVideo.SDL_GetCurrentDisplayMode;
import static org.lwjgl.sdl.SDLVideo.SDL_GetDisplayForWindow;

@Mixin(value = DisplaySdl.class, remap = false)
public abstract class DisplaySdlMixin {
    @Unique
    private final PresentationPacer argentum$pacer = new PresentationPacer();

    @Shadow
    public abstract long getHandle();

    @WrapMethod(method = "swapBuffers")
    private void argentum$decouplePresentation(Operation<Void> original) {
        int display = SDL_GetDisplayForWindow(this.getHandle());
        SDL_DisplayMode mode = SDL_GetCurrentDisplayMode(display);
        if (mode == null) {
            original.call();
            return;
        }
        this.argentum$pacer.present(display, mode.refresh_rate(), () -> {
            original.call();
            return true;
        });
    }
}
