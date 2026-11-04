package dev.rdh.cera.mixin.shaders;

import dev.rdh.cera.modules.shaders.ShaderRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.layer.EnderDragonDeathLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonDeathLayer.class)
abstract class EnderDragonDeathLayerMixin {
    @Inject(method = "render(Lnet/minecraft/entity/living/mob/monster/boss/EnderDragonEntity;FFFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/Lighting;turnOff()V"), require = 1)
    private void cera$whiteBeams(CallbackInfo ci) {
        if (Minecraft.getInstance().cera$getShaderEngine().isInitializedImpl()) {
            ShaderRender.bindWhiteTexture();
            ShaderRender.fullBrightLightmap();
        }
    }
}
