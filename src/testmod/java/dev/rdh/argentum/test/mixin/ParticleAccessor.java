package dev.rdh.argentum.test.mixin;

import net.minecraft.client.entity.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("lifetime")
    void lobbyBenchmark$setLifetime(int lifetime);

    @Accessor("gravity")
    void lobbyBenchmark$setGravity(float gravity);
}
