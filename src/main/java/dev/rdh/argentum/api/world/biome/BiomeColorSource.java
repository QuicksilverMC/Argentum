package dev.rdh.argentum.api.world.biome;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

public interface BiomeColorSource {
    int resolve(Biome biome, BlockPos pos);
}
