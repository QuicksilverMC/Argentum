package dev.rdh.argentum.mixin.features.terrain;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.embeddedt.embeddium.impl.model.light.flat.FlatLightPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FlatLightPipeline.class, remap = false)
public class FlatLightPipelineMixin {
    @ModifyExpressionValue(method = "getOffsetLightmap", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/model/light/data/LightDataAccess;unpackLU(I)I"))
    private int argentum$useOffsetLuminance(int luminance) {
        return 0;
    }
}
