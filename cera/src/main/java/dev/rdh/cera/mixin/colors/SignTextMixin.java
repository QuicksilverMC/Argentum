package dev.rdh.cera.mixin.colors;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.rdh.argentum.impl.render.blockentity.SignText;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SignText.class, remap = false)
public abstract class SignTextMixin {
    @ModifyReturnValue(method = "baseColor", at = @At("RETURN"))
    private static int cera$signTextColor(int color) {
        return Minecraft.getInstance().cera$getCustomColors().getSignTextColor(color);
    }
}
