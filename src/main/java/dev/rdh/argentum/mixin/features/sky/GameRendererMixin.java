package dev.rdh.argentum.mixin.features.sky;

import dev.rdh.argentum.impl.render.terrain.fog.ArgentumFogService;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render(IFJ)V", at = { @At("HEAD"), @At("RETURN") })
    private void argentum$invalidateSkyTexture(CallbackInfo ci) {
        ArgentumFogService.INSTANCE.invalidateSkyTexture();
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderSky(FI)V", shift = At.Shift.AFTER))
    private void argentum$captureSkyTexture(CallbackInfo ci) {
        ArgentumFogService.INSTANCE.captureSkyTexture();
    }
}
