package dev.rdh.argentum.impl.render;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import net.minecraft.util.math.BlockPos;

public final class BlockEntityLight {
    private final Long2IntOpenHashMap generations = new Long2IntOpenHashMap();

    public BlockEntityLight() {
        this.generations.defaultReturnValue(0);
    }

    public int generation(BlockPos pos) {
        return this.generations.get(key(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
    }

    public void invalidate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        // grown by a block: smooth light samples neighbors, so a change across a section border matters
        int lowX = minX - 1 >> 4, highX = maxX + 1 >> 4;
        int lowY = minY - 1 >> 4, highY = maxY + 1 >> 4;
        int lowZ = minZ - 1 >> 4, highZ = maxZ + 1 >> 4;
        for (int x = lowX; x <= highX; x++) {
            for (int y = lowY; y <= highY; y++) {
                for (int z = lowZ; z <= highZ; z++) {
                    this.generations.addTo(key(x, y, z), 1);
                }
            }
        }
    }

    private static long key(int sectionX, int sectionY, int sectionZ) {
        return (long)(sectionX & 0x3FFFFF) << 42 | (long)(sectionY & 0xFF) << 34 | (long)(sectionZ & 0x3FFFFF) << 12;
    }
}
