package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.rdh.argentum.impl.render.blockentity.BlockEntityBaker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.client.Minecraft;

@Mixin(value = BlockEntityBaker.class, remap = false)
abstract class BlockEntityBakerMixin {
    @ModifyReturnValue(method = "light", at = @At("RETURN"))
    private static float cera$unshadedUnderShaders(float light) {
        return Minecraft.getInstance().cera$getShaderEngine().isInitializedImpl() ? 1.0F : light;
    }
}
