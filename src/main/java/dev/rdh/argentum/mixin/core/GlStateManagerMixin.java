package dev.rdh.argentum.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.GlStateManager.Blend;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {
    @Shadow
	public static Blend BLEND;

    @Inject(method = "blendFunc", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBlendFunc(II)V"))
    private static void argentum$cacheBlendAlpha(int source, int destination, CallbackInfo ci) {
        BLEND.sfactorAlpha = source;
        BLEND.dfactorAlpha = destination;
    }
}
