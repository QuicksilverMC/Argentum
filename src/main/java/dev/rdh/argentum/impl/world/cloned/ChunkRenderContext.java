package dev.rdh.argentum.impl.world.cloned;

import org.embeddedt.embeddium.impl.util.position.SectionPos;
import org.jetbrains.annotations.Nullable;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;
import net.minecraft.world.gen.WorldGeneratorType;
import dev.rdh.argentum.api.world.biome.BiomeColorSource;
import dev.rdh.argentum.impl.render.blockentity.BakedItemFrames;
import dev.rdh.argentum.impl.world.biome.BiomeColorCache;

import java.util.Arrays;
import java.util.List;

public final class ChunkRenderContext implements WorldView {
    private static final int SECTION_LENGTH = 3;
    private static final int SECTION_COUNT = SECTION_LENGTH * SECTION_LENGTH * SECTION_LENGTH;

    private final SectionPos origin;
    private final ClonedChunkSection[] sections;
    private final ClonedChunkSection originSection;
    private final WorldGeneratorType generatorType;
    private final boolean hasSky;
    private final List<BakedItemFrames.Snapshot> frames;
    private short[] lightCache;
    private BiomeColorCache biomeColorCache;

    private ChunkRenderContext(World world, SectionPos origin, ClonedChunkSection[] sections) {
        this.origin = origin;
        this.frames = BakedItemFrames.collect(world, origin);
        this.sections = sections;
        this.originSection = sections[getSectionIndex(1, 1, 1)];
        this.generatorType = world.getGeneratorType();
        this.hasSky = !world.dimension.hasNoSky();
    }

    public void resetCaches(short[] lightCache, BiomeColorCache biomeColorCache) {
        this.lightCache = lightCache;
        this.biomeColorCache = biomeColorCache;
        Arrays.fill(this.lightCache, (short)-1);
    }

    public static @Nullable ChunkRenderContext prepare(World world, SectionPos origin, ClonedChunkSectionCache cache) {
        if (isSectionEmpty(world, origin.x(), origin.y(), origin.z())) {
            return null;
        }

        ClonedChunkSection[] sections = new ClonedChunkSection[SECTION_COUNT];
        for (int x = 0; x < SECTION_LENGTH; x++) {
            for (int y = 0; y < SECTION_LENGTH; y++) {
                for (int z = 0; z < SECTION_LENGTH; z++) {
                    sections[getSectionIndex(x, y, z)] = cache.acquire(origin.x() + x - 1, origin.y() + y - 1, origin.z() + z - 1);
                }
            }
        }

        return new ChunkRenderContext(world, origin, sections);
    }

    public static boolean isSectionEmpty(World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkAt(x, z);
        if (chunk.isEmpty()) {
            return true;
        }

        WorldChunkSection[] sections = chunk.getSections();
        return y < 0 || y >= sections.length || sections[y] == null || sections[y].isEmpty();
    }

    public SectionPos origin() {
        return this.origin;
    }

    public List<BakedItemFrames.Snapshot> frames() {
        return this.frames;
    }

    /**
     * {@return a bitmask over x of the blocks in the origin section's row (y, z) that are not air}
     *
     * Coordinates are local to the origin section. The mask is conservative, so callers must still check the state
     * they read for air.
     */
    public int originNonAirRow(int y, int z) {
        return this.originSection.getNonAirRow(y, z);
    }

    /** {@return the state at the given coordinates, which must be local to the origin section} */
    public BlockState getOriginBlockState(int x, int y, int z) {
        return this.originSection.getBlockState(x, y, z);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState getBlockState(int x, int y, int z) {
        ClonedChunkSection section = this.getSection(x >> 4, y >> 4, z >> 4);
        return section == null ? Blocks.AIR.defaultState() : section.getBlockState(x & 15, y & 15, z & 15);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        ClonedChunkSection section = this.getSection(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        return section == null ? null : section.getBlockEntity(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    @Override
    public int getLightColor(BlockPos pos, int ambientLight) {
        return this.getLightColor(pos.getX(), pos.getY(), pos.getZ(), ambientLight);
    }

    public int getLightColor(int x, int y, int z, int ambientLight) {
        int cached = this.getCachedLight(x, y, z);
        int sky = cached >> 4;
        int block = Math.max(cached & 15, ambientLight);
        return sky << 20 | block << 4;
    }

    private int getCachedLight(int x, int y, int z) {
        int index = this.getCacheIndex(x, y, z);
        if (index < 0) {
            return this.getBrightness(LightType.SKY, x, y, z) << 4 | this.getBrightness(LightType.BLOCK, x, y, z);
        }

        int light = this.lightCache[index];
        if (light < 0) {
            light = this.getBrightness(LightType.SKY, x, y, z) << 4 | this.getBrightness(LightType.BLOCK, x, y, z);
            this.lightCache[index] = (short)light;
        }
        return light;
    }

    private int getBrightness(LightType type, int x, int y, int z) {
        if (type == LightType.SKY && !this.hasSky) {
            return 0;
        }

        if (y < 0 || y >= 256) {
            return type.defaultValue;
        }

        if (!this.getBlockState(x, y, z).getBlock().usesNeighborLight()) {
            return this.getLight(type, x, y, z);
        }

        int light = this.getLight(type, x - 1, y, z);
        light = Math.max(light, this.getLight(type, x + 1, y, z));
        light = Math.max(light, this.getLight(type, x, y - 1, z));
        light = Math.max(light, this.getLight(type, x, y + 1, z));
        light = Math.max(light, this.getLight(type, x, y, z - 1));
        return Math.max(light, this.getLight(type, x, y, z + 1));
    }

    private int getLight(LightType type, int x, int y, int z) {
        ClonedChunkSection section = this.getSection(x >> 4, y >> 4, z >> 4);
        return section == null ? type.defaultValue : section.getLight(type, x & 15, y & 15, z & 15);
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return this.getBiome(pos.getX(), pos.getZ());
    }

    public Biome getBiome(int x, int z) {
        ClonedChunkSection section = this.getSection(x >> 4, this.origin.y(), z >> 4);
        return section == null ? Biome.DEFAULT : section.getBiome(x & 15, z & 15);
    }

    public int getBiomeColor(BlockPos pos, BiomeColorSource type) {
        return this.biomeColorCache.getColor(type, pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public boolean isAir(BlockPos pos) {
        return this.getBlockState(pos).getBlock().getMaterial() == Material.AIR;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public int getDirectSignal(BlockPos pos, Direction direction) {
        BlockState state = this.getBlockState(pos);
        return state.getBlock().getDirectSignal(this, pos, state, direction);
    }

    @Override
    public WorldGeneratorType getGeneratorType() {
        return this.generatorType;
    }

    private @Nullable ClonedChunkSection getSection(int x, int y, int z) {
        int localX = x - this.origin.x() + 1;
        int localY = y - this.origin.y() + 1;
        int localZ = z - this.origin.z() + 1;
        if ((localX | localY | localZ) < 0 || localX >= SECTION_LENGTH || localY >= SECTION_LENGTH || localZ >= SECTION_LENGTH) {
            return null;
        }
        return this.sections[getSectionIndex(localX, localY, localZ)];
    }

    private int getCacheIndex(int x, int y, int z) {
        int localX = x - this.origin.minX() + 2;
        int localY = y - this.origin.minY() + 2;
        int localZ = z - this.origin.minZ() + 2;
        return (localX | localY | localZ) < 0 || localX >= 20 || localY >= 20 || localZ >= 20
                ? -1 : (localY * 20 + localZ) * 20 + localX;
    }

    private static int getSectionIndex(int x, int y, int z) {
        return (x * SECTION_LENGTH + y) * SECTION_LENGTH + z;
    }
}
