package dev.rdh.argentum.mixin.features.entity.shadow;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Shadow
    @Final
    protected EntityRenderDispatcher dispatcher;

    @Shadow
    protected float shadowSize;

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private void celeritas$batchShadow(Entity entity, double dx, double dy, double dz, float opacity, float tickDelta, CallbackInfo ci) {
        ArgentumWorldRenderer renderer = ArgentumWorldRenderer.instanceNullable();
        if (renderer != null && renderer.getEntityInstancing().isShadowPass()) {
            ci.cancel();
            return;
        }
        if (renderer != null && renderer.getEntityShadowBatch().record(this.dispatcher.world, entity,
                dx, dy, dz, opacity, tickDelta, this.shadowSize)) {
            ci.cancel();
        }
    }
}
