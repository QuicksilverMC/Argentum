package dev.rdh.argentum.mixin.features.blockentity;

import dev.rdh.argentum.impl.ext.BlockEntityRenderState;
import dev.rdh.argentum.impl.render.entity.instancing.InstanceRenderPass;

import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEntity.class)
public class BlockEntityMixin implements BlockEntityRenderState {
    @Unique
    private int argentum$light = -1;
    @Unique
    private int argentum$generation = -1;
    @Unique
    private InstanceRenderPass argentum$pass;

    @Override
    public int argentum$getCachedLight(int generation) {
        return this.argentum$generation == generation ? this.argentum$light : -1;
    }

    @Override
    public void argentum$cacheLight(int light, int generation) {
        this.argentum$light = light;
        this.argentum$generation = generation;
    }

    @Override
    public InstanceRenderPass argentum$getPass() {
        return this.argentum$pass;
    }

    @Override
    public void argentum$setPass(InstanceRenderPass pass) {
        this.argentum$pass = pass;
    }
}
