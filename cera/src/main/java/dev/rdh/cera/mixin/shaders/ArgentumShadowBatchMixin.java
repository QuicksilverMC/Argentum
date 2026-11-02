package dev.rdh.cera.mixin.shaders;

import dev.rdh.argentum.impl.render.entity.EntityShadowBatch;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityShadowBatch.class, remap = false)
abstract class ArgentumShadowBatchMixin {
    @Inject(method = "record", at = @At("HEAD"), cancellable = true)
    private void cera$vanillaShadowsUnderShaders(CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().cera$getShaderEngine().isInitializedImpl()) {
            cir.setReturnValue(false);
        }
    }
}
