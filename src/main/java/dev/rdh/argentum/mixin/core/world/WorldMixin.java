package dev.rdh.argentum.mixin.core.world;

import dev.rdh.argentum.impl.ext.WorldExtension;
import dev.rdh.argentum.impl.render.BlockEntityLight;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

@Mixin(World.class)
public class WorldMixin implements ChunkTrackerHolder, WorldExtension {
    private final ChunkTracker celeritas$tracker = new ChunkTracker();
    private final BlockEntityLight argentum$blockEntityLight = new BlockEntityLight();
    @Unique
    private int argentum$entityListVersion;

    @Override
    public ChunkTracker sodium$getTracker() {
        return celeritas$tracker;
    }

    @Override
    public BlockEntityLight argentum$getBlockEntityLight() {
        return argentum$blockEntityLight;
    }

    @Override
    public int argentum$getEntityListVersion() {
        return this.argentum$entityListVersion;
    }

    @Inject(method = {"notifyEntityAdded", "notifyEntityRemoved"}, at = @At("HEAD"))
    private void argentum$countEntityListChange(Entity entity, CallbackInfo ci) {
        this.argentum$entityListVersion++;
    }
}
