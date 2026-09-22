package dev.rdh.argentum.impl.render.entity;


import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import dev.rdh.argentum.mixin.core.world.ClientChunkCacheAccessor;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.TypeInstanceMultiMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.List;
import java.util.function.Consumer;

public class EntityGatherer {
    private final List<Entity> entityList;

    private ClientWorld gatheredWorld;
    private long gatheredTick = Long.MIN_VALUE;
    private int gatheredChunkX;
    private int gatheredChunkZ;
    private int gatheredRadius = -1;

    public EntityGatherer() {
        this.entityList = new ObjectArrayList<>();
    }

    public List<Entity> getLoadedEntityList(ClientWorld world, int centerChunkX, int centerChunkZ, int radius) {
        // The world's entity set only changes while it ticks - packets are handled on the main thread inside the
        // tick - so between two frames of the same tick this sweep would produce the same list. Entities are held
        // by reference, so they still move at frame rate.
        if (world == this.gatheredWorld && world.getTime() == this.gatheredTick && radius == this.gatheredRadius
                && centerChunkX == this.gatheredChunkX && centerChunkZ == this.gatheredChunkZ) {
            return this.entityList;
        }

        this.entityList.clear();
        this.gatheredWorld = world;
        this.gatheredTick = world.getTime();
        this.gatheredChunkX = centerChunkX;
        this.gatheredChunkZ = centerChunkZ;
        this.gatheredRadius = radius;

        Consumer<Entity> addEntity = this.entityList::add;
        // Iterate directly over chunk entity lists where possible - mods may create multipart entities that are not
        // added to the main loadedEntityList.
        if (world.getChunkSource() instanceof ClientChunkCacheAccessor provider) {
            var chunksByPos = provider.getChunksByPos();
            int diameter = radius * 2 + 1;

            if (diameter * diameter < chunksByPos.size()) {
                for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
                    for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                        WorldChunk chunk = chunksByPos.get(ChunkPos.toLong(chunkX, chunkZ));
                        if (chunk != null) {
                            collect(chunk, addEntity);
                        }
                    }
                }
            } else {
                for (WorldChunk chunk : provider.getAllChunks()) {
                    collect(chunk, addEntity);
                }
            }
        } else {
            // Best we can do is the loaded entity list - this will miss some multipart entities
            world.entities.forEach(addEntity);
        }
        return this.entityList;
    }

    private static void collect(WorldChunk chunk, Consumer<Entity> addEntity) {
        if (!chunk.argentum$hasEntities()) {
            return;
        }

        for (TypeInstanceMultiMap<Entity> map : chunk.getEntities()) {
            map.forEach(addEntity);
        }
    }
}
