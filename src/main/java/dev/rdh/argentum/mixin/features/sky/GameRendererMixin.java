package dev.rdh.argentum.mixin.features.sky;

import dev.rdh.argentum.impl.render.terrain.fog.ArgentumFogService;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Unique
    private static final float VANILLA_MAX_SKY_FOG_END = 16 * 16.0F;

    @Inject(method = "render(IFJ)V", at = { @At("HEAD"), @At("RETURN") })
    private void argentum$invalidateSkyTexture(CallbackInfo ci) {
        ArgentumFogService.INSTANCE.invalidateSkyTexture();
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderSky(FI)V"))
    private void argentum$keepSkyEdgesFogged(CallbackInfo ci) {
        if (GlStateManager.FOG.end > VANILLA_MAX_SKY_FOG_END) GlStateManager.fogEnd(VANILLA_MAX_SKY_FOG_END);
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderSky(FI)V", shift = At.Shift.AFTER))
    private void argentum$captureSkyTexture(CallbackInfo ci) {
        ArgentumFogService.INSTANCE.captureSkyTexture();
    }
}
