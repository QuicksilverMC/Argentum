package dev.rdh.argentum.mixin.features.present;

import net.minecraft.client.Minecraft;

import org.lwjgl.sdl.SDL_DisplayMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.Argentum;
import pl.tomgirl.lenis.window.DisplaySdl;

import static org.lwjgl.sdl.SDLVideo.SDL_GetCurrentDisplayMode;
import static org.lwjgl.sdl.SDLVideo.SDL_GetDisplayForWindow;

@Mixin(value = DisplaySdl.class, remap = false)
public abstract class DisplaySdlMixin {
    @Unique
    private long argentum$nextPresentNanos = Long.MIN_VALUE;

    @Shadow
    public abstract long getHandle();

    @Inject(method = "swapBuffers", at = @At("HEAD"), cancellable = true)
    private void argentum$decouplePresentation(CallbackInfo ci) {
        if (!Argentum.CONFIG.decoupledPresentation || Minecraft.getInstance().options.vsync) {
            return;
        }

        SDL_DisplayMode mode = SDL_GetCurrentDisplayMode(SDL_GetDisplayForWindow(this.getHandle()));
        float refreshRate;
        if (mode == null || (refreshRate = mode.refresh_rate()) <= 0.0F) {
            return;
        }

        long now = System.nanoTime();
        if (now < this.argentum$nextPresentNanos) {
            ci.cancel();
            return;
        }
        this.argentum$nextPresentNanos = Math.max(this.argentum$nextPresentNanos + (long) (1.0e9 / refreshRate), now);
    }
}
