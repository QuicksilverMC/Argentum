package dev.rdh.argentum.mixin.features.sky;

import dev.rdh.argentum.impl.render.terrain.fog.ArgentumFogService;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "renderSky(FI)V", at = @At("RETURN"))
    private void argentum$captureSkyTexture(CallbackInfo ci) {
        ArgentumFogService.INSTANCE.captureSkyTexture();
    }
}
