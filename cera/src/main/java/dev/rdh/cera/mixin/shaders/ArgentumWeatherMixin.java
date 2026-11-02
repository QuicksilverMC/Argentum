package dev.rdh.cera.mixin.shaders;

import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Minecraft;

@Mixin(value = ArgentumWorldRenderer.class, remap = false)
abstract class ArgentumWeatherMixin {
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void cera$vanillaWeatherUnderShaders(CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().cera$getShaderEngine().isInitializedImpl()) {
            cir.setReturnValue(false);
        }
    }
}
