package dev.rdh.argentum.test.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.argentum.test.LobbyBenchmark;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ParticleManager.class)
abstract class LobbyParticleMixin {
    @WrapMethod(method = {"render", "renderLit"})
    private void lobbyBenchmark$timeParticles(Entity camera, float tickDelta, Operation<Void> original) {
        long start = System.nanoTime();
        original.call(camera, tickDelta);
        LobbyBenchmark.recordParticles(System.nanoTime() - start);
    }

    @WrapWithCondition(method = {"render", "renderLit"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/particle/Particle;render(Lnet/minecraft/client/render/vertex/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private boolean lobbyBenchmark$skipGeometry(Particle particle, BufferBuilder buffer, Entity camera,
            float tickDelta, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        return !LobbyBenchmark.variant("noParticleGeometry");
    }
}
