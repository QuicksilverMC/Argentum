package dev.rdh.argentum.mixin.features.model.instancing;

import dev.rdh.argentum.api.render.instancing.InstanceDrawTarget;
import dev.rdh.argentum.api.render.terrain.TerrainDrawTarget;
import dev.rdh.argentum.impl.ext.MinecraftExtension;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin implements MinecraftExtension {
    @Unique
    private InstanceDrawTarget argentum$instanceDrawTarget;
    @Unique
    private TerrainDrawTarget argentum$terrainDrawTarget;

    @Override
    public InstanceDrawTarget argentum$getInstanceDrawTarget() {
        return this.argentum$instanceDrawTarget;
    }

    @Override
    public void argentum$setInstanceDrawTarget(InstanceDrawTarget target) {
        this.argentum$instanceDrawTarget = target;
    }

    @Override
    public TerrainDrawTarget argentum$getTerrainDrawTarget() {
        return this.argentum$terrainDrawTarget;
    }

    @Override
    public void argentum$setTerrainDrawTarget(TerrainDrawTarget target) {
        this.argentum$terrainDrawTarget = target;
    }
}
