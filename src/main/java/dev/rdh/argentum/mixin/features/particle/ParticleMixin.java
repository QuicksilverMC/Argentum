package dev.rdh.argentum.mixin.features.particle;

import net.minecraft.client.entity.particle.Particle;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.ext.ParticleExtension;
import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

@Mixin(Particle.class)
public abstract class ParticleMixin extends Entity implements ParticleExtension {
    @Unique
    private boolean celeritas$visible = true;
    @Unique
    private long argentum$lightTick = Long.MIN_VALUE;
    @Unique
    private int argentum$light;

    private ParticleMixin(World world) {
        super(world);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void celeritas$updateVisibility(CallbackInfo ci) {
        ArgentumWorldRenderer renderer = ArgentumWorldRenderer.instanceNullable();
        this.celeritas$visible = renderer == null || renderer.isParticleVisible((Particle)(Object)this);
    }

    @Override
    public boolean argentum$isVisible() {
        return this.celeritas$visible;
    }

    @Override
    public int getLightLevel(float tickDelta) {
        if (!Argentum.CONFIG.particleLightCache) {
            return super.getLightLevel(tickDelta);
        }
        long tick = this.world.getTime();
        if (this.argentum$lightTick != tick) {
            this.argentum$lightTick = tick;
            this.argentum$light = super.getLightLevel(tickDelta);
        }
        return this.argentum$light;
    }
}
