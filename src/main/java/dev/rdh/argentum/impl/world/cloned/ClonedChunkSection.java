package dev.rdh.argentum.impl.world.cloned;

import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.embeddedt.embeddium.impl.util.position.SectionPos;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkNibbleStorage;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;

import java.util.Arrays;

final class ClonedChunkSection {
    private static final char AIR_STATE_ID = (char)Block.STATE_REGISTRY.getId(Blocks.AIR.defaultState());

    private final SectionPos position;
    private final char[] blockStates;
    private final short[] nonAirRows;
    private final byte[] blockLight;
    private final byte[] skyLight;
    private final byte[] emptySectionSkyLight;
    private final boolean hasLightData;
    private final Short2ObjectMap<BlockEntity> blockEntities = new Short2ObjectOpenHashMap<>();
    private final byte[] biomes;
    private final boolean hasSky;

    ClonedChunkSection(World world, int sectionX, int sectionY, int sectionZ) {
        this.position = new SectionPos(sectionX, sectionY, sectionZ);

        WorldChunk chunk = world.getChunkAt(sectionX, sectionZ);
        WorldChunkSection source = getSection(chunk, sectionY);
        this.hasSky = !world.dimension.hasNoSky();

        if (source == null || source.isEmpty()) {
            this.blockStates = null;
            this.nonAirRows = null;
        } else {
            this.blockStates = Arrays.copyOf(source.getBlockStates(), source.getBlockStates().length);
            this.nonAirRows = computeNonAirRows(this.blockStates);
        }

        if (source == null) {
            this.hasLightData = false;
            this.blockLight = null;
            this.skyLight = null;
            this.emptySectionSkyLight = createEmptySectionSkyLight(chunk, sectionY, this.hasSky);
        } else {
            this.hasLightData = true;
            this.blockLight = copy(source.getBlockLightStorage());
            this.skyLight = this.hasSky && source.getSkyLightStorage() != null ? copy(source.getSkyLightStorage()) : null;
            this.emptySectionSkyLight = null;
        }

        this.biomes = Arrays.copyOf(chunk.getBiomes(), 256);
        copyBlockEntities(chunk, sectionY);
    }

    private static WorldChunkSection getSection(WorldChunk chunk, int sectionY) {
        WorldChunkSection[] sections = chunk.getSections();
        return sectionY >= 0 && sectionY < sections.length ? sections[sectionY] : null;
    }

    private static short[] computeNonAirRows(char[] blockStates) {
        short[] rows = new short[256];

        for (int i = 0; i < blockStates.length; i++) {
            if (blockStates[i] != AIR_STATE_ID) {
                // i >> 4 is the row and i & 15 is x
                rows[i >> 4] |= (short)(1 << (i & 15));
            }
        }

        return rows;
    }

    private static byte[] copy(ChunkNibbleStorage source) {
        return Arrays.copyOf(source.getData(), source.getData().length);
    }

    private static byte[] createEmptySectionSkyLight(WorldChunk chunk, int sectionY, boolean hasSky) {
        if (!hasSky || sectionY < 0 || sectionY >= 16) {
            return null;
        }

        byte[] minLitY = new byte[256];
        int minY = sectionY << 4;
        int[] heightMap = chunk.getHeightMap();

        for (int i = 0; i < 256; i++) {
            minLitY[i] = (byte) Math.clamp(heightMap[i] - minY, 0, 17);
        }

        return minLitY;
    }

    private void copyBlockEntities(WorldChunk chunk, int sectionY) {
        if (this.blockStates == null) {
            return;
        }

        int chunkBaseX = this.position.x() << 4;
        int chunkBaseZ = this.position.z() << 4;

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int row = this.nonAirRows[y << 4 | z] & 0xFFFF; row != 0; row &= row - 1) {
                    int x = Integer.numberOfTrailingZeros(row);
					if (!getBlockState(x, y, z).getBlock().hasBlockEntity()) {
                        continue;
                    }

                    // we can't reuse the same BlockPos in the loop, since `chunk.getBlockEntity` and `be.setPos` both don't clone it
                    BlockPos pos = new BlockPos(chunkBaseX + x, sectionY << 4 | y, chunkBaseZ + z);

                    BlockEntity be = chunk.getBlockEntity(pos, WorldChunk.BlockEntityCreationType.IMMEDIATE);

                    if (be != null) {
                        be.setPos(pos);
                        this.blockEntities.put(packLocal(x, y, z), be);
                    }
                }
            }
        }
    }

    BlockState getBlockState(int x, int y, int z) {
        if (this.blockStates == null) {
            return Blocks.AIR.defaultState();
        }
        BlockState state = Block.STATE_REGISTRY.get(this.blockStates[y << 8 | z << 4 | x]);
        return state == null ? Blocks.AIR.defaultState() : state;
    }

    int getNonAirRow(int y, int z) {
        return this.nonAirRows == null ? 0 : this.nonAirRows[y << 4 | z] & 0xFFFF;
    }

    BlockEntity getBlockEntity(int x, int y, int z) {
        return this.blockEntities.get(packLocal(x, y, z));
    }

    int getLight(LightType type, int x, int y, int z) {
        if (this.hasLightData) {
            byte[] light = type == LightType.SKY ? this.skyLight : this.blockLight;
            if (light == null) {
                return 0;
            }
            int index = y << 8 | z << 4 | x;
            return light[index >> 1] >> ((index & 1) << 2) & 15;
        }

        if (type != LightType.SKY || !this.hasSky) {
            return 0;
        }
        if (this.position.y() < 0 || this.position.y() >= 16) {
            return LightType.SKY.defaultValue;
        }
        if (this.emptySectionSkyLight == null) {
            return 0;
        }
        return y >= this.emptySectionSkyLight[z << 4 | x] ? LightType.SKY.defaultValue : 0;
    }

    Biome getBiome(int x, int z) {
        return Biome.byId(this.biomes[z << 4 | x] & 255, Biome.DEFAULT);
    }

    SectionPos getPosition() {
        return this.position;
    }

    private static short packLocal(int x, int y, int z) {
        return (short)((x & 15) << 8 | (z & 15) << 4 | y & 15);
    }
}
