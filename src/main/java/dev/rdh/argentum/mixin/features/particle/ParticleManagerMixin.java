package dev.rdh.argentum.mixin.features.particle;

import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.render.particle.ParticleCapture;
import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    @WrapWithCondition(method = {"render", "renderLit"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/particle/Particle;render(Lnet/minecraft/client/render/vertex/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V"), require = 2)
    private boolean celeritas$renderVisibleParticle(Particle particle, BufferBuilder buffer, Entity camera,
            float tickDelta, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        if (!particle.argentum$isVisible()) return false;
        ArgentumWorldRenderer renderer = ArgentumWorldRenderer.instanceNullable();
        return renderer == null || renderer.isParticleInFrustum(particle);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void argentum$beginCapture(Entity camera, float tickDelta, CallbackInfo ci) {
        ParticleCapture capture = argentum$capture();
        if (capture != null) capture.begin(Tesselator.getInstance().getBuffer());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void argentum$endCapture(Entity camera, float tickDelta, CallbackInfo ci) {
        ParticleCapture capture = argentum$capture();
        if (capture != null) capture.end();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/particle/Particle;render(Lnet/minecraft/client/render/vertex/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private void argentum$captureParticle(Particle particle, BufferBuilder buffer, Entity camera, float tickDelta,
            float dx, float dy, float dz, float forwards, float sideways, Operation<Void> original) {
        ParticleCapture capture = argentum$capture();
        if (capture == null || !capture.render(particle, buffer, camera, tickDelta, dx, dy, dz, forwards, sideways)) {
            original.call(particle, buffer, camera, tickDelta, dx, dy, dz, forwards, sideways);
        }
    }

    private static ParticleCapture argentum$capture() {
        ArgentumWorldRenderer renderer = ArgentumWorldRenderer.instanceNullable();
        return renderer == null ? null : renderer.getParticleCapture();
    }
}
