package dev.rdh.cera.mixin.shaders;

import dev.rdh.argentum.impl.render.environment.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Minecraft;

@Mixin(value = CloudRenderer.class, remap = false)
abstract class CloudRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cera$vanillaCloudsUnderShaders(CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().cera$getShaderEngine().isInitializedImpl()) {
            cir.setReturnValue(false);
        }
    }
}
