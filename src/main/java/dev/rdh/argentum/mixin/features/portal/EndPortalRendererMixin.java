package dev.rdh.argentum.mixin.features.portal;

import net.minecraft.block.entity.EndPortalBlockEntity;
import net.minecraft.client.render.block.entity.EndPortalRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

@Mixin(value = EndPortalRenderer.class, priority = 100)
public abstract class EndPortalRendererMixin {
    @Inject(method = "render(Lnet/minecraft/block/entity/EndPortalBlockEntity;DDDFI)V", at = @At("HEAD"), cancellable = true)
    private void argentum$collectPortal(EndPortalBlockEntity entity, double x, double y, double z,
            float tickDelta, int stage, CallbackInfo ci) {
        ArgentumWorldRenderer renderer = ArgentumWorldRenderer.instanceNullable();
        if (renderer != null && renderer.getEndPortalRenderer().intercept(entity, x, y, z)) ci.cancel();
    }
}
