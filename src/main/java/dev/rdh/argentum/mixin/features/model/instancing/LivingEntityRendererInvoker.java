package dev.rdh.argentum.mixin.features.model.instancing;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {
    @Invoker("setupOverlayColor")
    boolean argentum$setupOverlayColor(LivingEntity entity, float tickDelta, boolean combineTextures);
}
