package dev.rdh.argentum.mixin.core.world;

import dev.rdh.argentum.impl.ext.WorldExtension;
import dev.rdh.argentum.impl.render.BlockEntityLight;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.World;

@Mixin(World.class)
public class WorldMixin implements ChunkTrackerHolder, WorldExtension {
    private final ChunkTracker celeritas$tracker = new ChunkTracker();
    private final BlockEntityLight argentum$blockEntityLight = new BlockEntityLight();

    @Override
    public ChunkTracker sodium$getTracker() {
        return celeritas$tracker;
    }

    @Override
    public BlockEntityLight argentum$getBlockEntityLight() {
        return argentum$blockEntityLight;
    }
}
