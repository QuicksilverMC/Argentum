package dev.rdh.argentum.impl.world.biome;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import dev.rdh.argentum.api.world.biome.BiomeColorSource;
import dev.rdh.argentum.impl.world.cloned.ChunkRenderContext;

public final class BiomeColorCache extends org.embeddedt.embeddium.impl.biome.BiomeColorCache<Biome, BiomeColorSource> {
    private final Source source;
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();

    public BiomeColorCache(int blendRadius) {
        this(new Source(), blendRadius);
    }

    private BiomeColorCache(Source source, int blendRadius) {
        super(source::getBiome, blendRadius);
        this.source = source;
    }

    public void update(ChunkRenderContext context) {
        this.source.context = context;
        super.update(context.origin());
    }

    @Override
    protected int resolveColor(BiomeColorSource resolver, Biome biome, int x, int y, int z) {
        this.cursor.set(x, y, z);
        return resolver.resolve(biome, this.cursor);
    }

    public enum ColorType implements BiomeColorSource {
        GRASS {
            @Override
            public int resolve(Biome biome, BlockPos pos) {
                return biome.getGrassColor(pos);
            }
        },
        FOLIAGE {
            @Override
            public int resolve(Biome biome, BlockPos pos) {
                return biome.getFoliageColor(pos);
            }
        },
        WATER {
            @Override
            public int resolve(Biome biome, BlockPos pos) {
                return biome.waterFogColor;
            }
        }
    }

    private static final class Source {
        private ChunkRenderContext context;

        private Biome getBiome(int x, int y, int z) {
            return this.context.getBiome(x, z);
        }
    }
}
