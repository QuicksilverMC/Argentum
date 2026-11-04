package dev.rdh.cera.mixin.shaders;

import dev.rdh.cera.modules.shaders.ShaderRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.LightningBoltRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBoltRenderer.class)
abstract class LightningBoltRendererMixin {
    @Inject(method = "render(Lnet/minecraft/entity/global/LightningBoltEntity;DDDFF)V", at = @At("HEAD"), require = 1)
    private void cera$whiteBolt(CallbackInfo ci) {
        if (Minecraft.getInstance().cera$getShaderEngine().isInitializedImpl()) {
            ShaderRender.bindWhiteTexture();
            ShaderRender.fullBrightLightmap();
        }
    }
}
