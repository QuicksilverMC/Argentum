package dev.rdh.cera.modules.colors;

import dev.rdh.cera.Cera;
import dev.rdh.cera.props.BlockMatcher;
import dev.rdh.cera.props.Props;
import dev.rdh.cera.modules.colors.CustomColormaps.Colormap.Format;
import dev.rdh.argentum.impl.world.biome.BiomeColorCache;
import dev.rdh.argentum.impl.world.biome.BiomeColorCache.BiomeColorSource;
import dev.rdh.argentum.impl.world.cloned.ChunkRenderContext;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import org.embeddedt.embeddium.api.util.ColorARGB;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;
import net.ornithemc.osl.resource.loader.api.resource.reload.ResourceReloadListener;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CustomColormaps implements ResourceReloadListener {
    private static final String[] DIRECTORIES = {"optifine/colormap/", "mcpatcher/colormap/"};

    private volatile State state = State.EMPTY;

    @Override
    public void resourcesReloaded(ResourceManager resources) {
        Map<Block, List<Colormap>> byBlock = new Object2ObjectOpenHashMap<>();
        for (String directory : DIRECTORIES) {
            resources.findResources("minecraft", directory, id -> id.identifier().endsWith(".properties"))
                    .values().forEach(resource -> loadCustom(resource, resources, byBlock));
        }
        loadPaletteBlocks(resources, "optifine/color.properties", byBlock);
        loadPaletteBlocks(resources, "mcpatcher/color.properties", byBlock);
        Map<Block, Colormap[]> blockColormaps = new Object2ObjectOpenHashMap<>();
        byBlock.forEach((block, list) -> blockColormaps.put(block, list.toArray(Colormap[]::new)));

        Colormap swampGrass = loadNamed(resources, "swampgrass.png", "swampgrasscolor.png");
        Colormap swampFoliage = loadNamed(resources, "swampfoliage.png", "swampfoliagecolor.png");
        this.state = new State(
                Map.copyOf(blockColormaps),
                loadNamed(resources, "water.png", "watercolorX.png"),
                loadNamed(resources, "pine.png", "pinecolor.png"),
                loadNamed(resources, "birch.png", "birchcolor.png"),
                swampGrass, swampFoliage,
                swampSource(swampGrass, true), swampSource(swampFoliage, false),
                loadNamed(resources, "redstone.png", "redstonecolor.png"),
                loadNamed(resources, "stem.png", "stemcolor.png"),
                loadNamed(resources, "pumpkinstem.png"),
                loadNamed(resources, "melonstem.png"),
                loadNamed(resources, "sky0.png", "skycolor0.png"),
                loadNamed(resources, "fog0.png", "fogcolor0.png"),
                loadNamed(resources, "underwater.png", "underwatercolor.png"),
                loadNamed(resources, "underlava.png", "underlavacolor.png")
        );

		StringBuilder sb = new StringBuilder("[CustomColormaps] loaded {} block maps");
        if (state.water != null) sb.append("/water");
        if (state.pine != null) sb.append("/pine");
        if (state.birch != null) sb.append("/birch");
        if (state.swampGrass != null) sb.append("/swampGrass");
        if (state.swampFoliage != null) sb.append("/swampFoliage");
        if (state.redstone != null) sb.append("/redstone");
        if (state.stem != null) sb.append("/stem");
        if (state.pumpkinStem != null) sb.append("/pumpkinStem");
        if (state.melonStem != null) sb.append("/melonStem");
        Cera.LOGGER.info(sb.toString(), state.blockColormaps.size());
    }

    public int getColor(BlockState state, ChunkRenderContext world, BlockPos pos) {
        if (!Cera.CONFIG.customColors) return -1;
        State s = this.state;
        Block block = state.getBlock();

        Colormap[] matched = s.blockColormaps.get(block);
        if (matched != null) {
            for (Colormap colormap : matched) {
                if (colormap.matches(state)) return colormap.resolve(world.getBiome(pos), pos);
            }
        }

        if (block == Blocks.REDSTONE_WIRE) {
            return s.redstone == null ? -1 : s.redstone.getColorByIndex(block.getMetadataFromState(state));
        }
        if (block == Blocks.PUMPKIN_STEM || block == Blocks.MELON_STEM) {
            Colormap stem;
            if (s.pumpkinStem != null && block == Blocks.PUMPKIN_STEM) {
                stem = s.pumpkinStem;
            } else if (s.melonStem != null && block == Blocks.MELON_STEM) {
                stem = s.melonStem;
            } else if (s.stem != null) {
                stem = s.stem;
            } else {
                return -1;
            }
            return stem.getColorByIndex(block.getMetadataFromState(state));
        }
        if (block == Blocks.LEAVES) {
            PlanksBlock.Variant variant = state.get(LeavesBlock.VARIANT);
            if (variant == PlanksBlock.Variant.SPRUCE && s.pine != null) return s.pine.resolve(world.getBiome(pos), pos);
            if (variant == PlanksBlock.Variant.BIRCH && s.birch != null) return s.birch.resolve(world.getBiome(pos), pos);
        }
        if ((block == Blocks.WATER || block == Blocks.FLOWING_WATER) && s.water != null) {
            return s.water.resolve(world.getBiome(pos), pos);
        }
        if (s.swampGrass != null || s.swampFoliage != null) {
            Biome biome = world.getBiome(pos);
            if (biome == Biome.SWAMPLAND) {
                if (s.swampGrass != null && isGrass(block)) return s.swampGrass.resolve(biome, pos);
                if (s.swampFoliage != null && isFoliage(block)) return s.swampFoliage.resolve(biome, pos);
            }
        }
        return -1;
    }

    public BiomeColorSource resolverFor(BlockState state) {
        if (!Cera.CONFIG.customColors) return null;
        State s = this.state;
        Block block = state.getBlock();

        Colormap[] matched = s.blockColormaps.get(block);
        if (matched != null) {
            for (Colormap colormap : matched) {
                if (colormap.matches(state)) return colormap;
            }
        }
        if (block == Blocks.LEAVES) {
            PlanksBlock.Variant variant = state.get(LeavesBlock.VARIANT);
            if (variant == PlanksBlock.Variant.SPRUCE && s.pine != null) return s.pine;
            if (variant == PlanksBlock.Variant.BIRCH && s.birch != null) return s.birch;
        }
        if ((block == Blocks.WATER || block == Blocks.FLOWING_WATER) && s.water != null) return s.water;
        if (isGrass(block)) return s.swampGrassSource;
        if (isFoliage(block)) return s.swampFoliageSource;
        return null;
    }

    public int redstoneColor(int level) {
        if (!Cera.CONFIG.customColors) return -1;
        Colormap redstone = this.state.redstone;
        return redstone != null ? redstone.getColorByIndex(level) : -1;
    }

    public int waterColor(Biome biome, BlockPos pos) {
        return sampleWorld(this.state.water, biome, pos);
    }

    public int skyColor(Biome biome, BlockPos pos) {
        return sampleWorld(this.state.sky, biome, pos);
    }

    public int fogColor(Biome biome, BlockPos pos) {
        return sampleWorld(this.state.fog, biome, pos);
    }

    public int underwaterColor(Biome biome, BlockPos pos) {
        return sampleWorld(this.state.underwater, biome, pos);
    }

    public int underlavaColor(Biome biome, BlockPos pos) {
        return sampleWorld(this.state.underlava, biome, pos);
    }

    private int sampleWorld(Colormap colormap, Biome biome, BlockPos pos) {
        if (!Cera.CONFIG.customColors) return -1;
        return colormap != null ? colormap.resolve(biome, pos) : -1;
    }

    public boolean hasBlockColormap(BlockState state) {
        if (!Cera.CONFIG.customColors) return false;
        Colormap[] matched = this.state.blockColormaps.get(state.getBlock());
        if (matched != null) {
            for (Colormap colormap : matched) {
                if (colormap.matches(state)) return true;
            }
        }
        return false;
    }

    private static boolean isGrass(Block block) {
        return block == Blocks.GRASS || block == Blocks.TALLGRASS || block == Blocks.REEDS;
    }

    private static boolean isFoliage(Block block) {
        return block == Blocks.LEAVES || block == Blocks.LEAVES2 || block == Blocks.VINE;
    }

    private static BiomeColorSource swampSource(Colormap swamp, boolean grass) {
        if (swamp == null) return null;
        return (biome, pos) -> {
			if(biome == Biome.SWAMPLAND) return swamp.resolve(biome, pos);
			if(grass) return biome.getGrassColor(pos);
	        return biome.getFoliageColor(pos);
		};
    }

    private static void loadCustom(Resource resource, ResourceManager resources, Map<Block, List<Colormap>> byBlock) {
        try {
            Props props = new Props(resource);
            Object2IntMap<Block> blocks = BlockMatcher.parseBlocks(props.get("blocks"));
            if (blocks.isEmpty()) return;

            Format format = parseFormat(props.get("format"));
            Colormap colormap;
            if (format == Format.FIXED) {
                colormap = new Colormap(format, null, 0, 0, 0, 0, props.getColor("color").orElse(0xFFFFFF), blocks);
            } else {
                Identifier source = props.get("source") != null
                        ? withPng(props.parseId(stripPng(props.get("source"))))
                        : withPng(new Identifier(props.id().namespace(), stripPng(props.id().identifier())));
                Image image = readImage(resources, source);
                if (image == null) {
                    Cera.LOGGER.warn("[CustomColors] Source not found for {}", props.id());
                    return;
                }
                colormap = new Colormap(format, image.pixels(), image.width(), image.height(),
                        props.getInt("yVariance", 0).orElse(0), props.getInt("yOffset", 0).orElse(0), 0, blocks);
            }
            for (Block block : blocks.keySet()) {
                byBlock.computeIfAbsent(block, _ -> new ArrayList<>()).add(colormap);
            }
        } catch (IOException e) {
            Cera.LOGGER.warn("[CustomColors] Failed to read {}", resource.location(), e);
        }
    }

    // OptiFine color.properties inline block colormaps: palette.block.<colormap path>=<block list>
    private static void loadPaletteBlocks(ResourceManager resources, String file, Map<Block, List<Colormap>> byBlock) {
        Resource resource = resources.getResource(new Identifier(file)).orElse(null);
        if (resource == null) return;
        String base = file.substring(0, file.lastIndexOf('/') + 1);
        try {
            Props props = new Props(resource);
            for (String key : props.properties().stringPropertyNames()) {
                if (!key.startsWith("palette.block.")) continue;
                Object2IntMap<Block> blocks = BlockMatcher.parseBlocks(props.get(key));
                if (blocks.isEmpty()) continue;
                String path = stripPng(key.substring("palette.block.".length()));
                path = path.startsWith("~/") || path.startsWith("/") ? path.replaceFirst("^~?/", "") : base + path;
                Image image = readImage(resources, withPng(new Identifier(path)));
                if (image == null) {
                    Cera.LOGGER.warn("[CustomColors] Palette not found: {}", key);
                    continue;
                }
                Colormap colormap = new Colormap(Format.VANILLA, image.pixels(), image.width(), image.height(), 0, 0, 0, blocks);
                for (Block block : blocks.keySet()) {
                    byBlock.computeIfAbsent(block, _ -> new ArrayList<>()).add(colormap);
                }
            }
        } catch (IOException e) {
            Cera.LOGGER.warn("[CustomColors] Failed to read {}", file, e);
        }
    }

    private static Colormap loadNamed(ResourceManager resources, String... names) {
        for (String directory : DIRECTORIES) {
            for (String name : names) {
                Image image = readImage(resources, new Identifier(directory + name));
                if (image != null) {
                    return new Colormap(Format.VANILLA, image.pixels(), image.width(), image.height(), 0, 0, 0, null);
                }
            }
        }
        return null;
    }

    private static Format parseFormat(String value) {
        if (value == null) return Format.VANILLA;
        return switch (value.trim()) {
            case "grid" -> Format.GRID;
            case "fixed" -> Format.FIXED;
            default -> Format.VANILLA;
        };
    }

    private static String stripPng(String path) {
        return path.endsWith(".png") ? path.substring(0, path.length() - 4) : path;
    }

    private static Identifier withPng(Identifier id) {
        return new Identifier(id.getNamespace(), id.getPath() + ".png");
    }

    private static Image readImage(ResourceManager resources, Identifier id) {
        Resource resource = resources.getResource(id).orElse(null);
        if (resource == null) return null;
        try (resource; InputStream in = resource.open()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) return null;
            int width = image.getWidth();
            int height = image.getHeight();
            return new Image(image.getRGB(0, 0, width, height, null, 0, width), width, height);
        } catch (IOException e) {
            return null;
        }
    }

    private record Image(int[] pixels, int width, int height) {
    }

    private record State(
            Map<Block, Colormap[]> blockColormaps,
            Colormap water,
            Colormap pine, Colormap birch,
            Colormap swampGrass, Colormap swampFoliage,
            BiomeColorSource swampGrassSource, BiomeColorSource swampFoliageSource,
            Colormap redstone,
            Colormap stem, Colormap pumpkinStem, Colormap melonStem,
            Colormap sky, Colormap fog, Colormap underwater, Colormap underlava
    ) {
        static final State EMPTY = new State(Map.of(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    record Colormap(
            Format format, int[] colors,
            int width, int height,
            int yVariance, int yOffset,
            int fixedColor,
            Object2IntMap<Block> blocks
    ) implements BiomeColorCache.BiomeColorSource {
        enum Format { VANILLA, GRID, FIXED }

        boolean matches(BlockState state) {
            return BlockMatcher.matches(this.blocks, state);
        }

        @Override
        public int resolve(Biome biome, BlockPos pos) {
            return switch (this.format) {
                case FIXED -> this.fixedColor;
                case VANILLA -> {
                    float temperature = Math.clamp(biome.getTemperature(pos), 0.0F, 1.0F);
                    float rainfall = Math.clamp(biome.getDownfall(), 0.0F, 1.0F) * temperature;
                    int cx = (int) ((1.0F - temperature) * (this.width - 1));
                    int cy = (int) ((1.0F - rainfall) * (this.height - 1));
                    yield sample(cx, cy);
                }
                case GRID -> {
                    int cy = pos.getY() - this.yOffset;
                    if (this.yVariance > 0) {
                        int hash = Cera.intHash(pos.getX() << (16 + pos.getZ())) & 0xFF;
                        cy += hash % (this.yVariance * 2 + 1) - this.yVariance;
                    }
                    yield sample(biome.id, cy);
                }
            };
        }

        int getColorByIndex(int index) {
            return ColorARGB.withAlpha(this.colors[Math.clamp(index, 0, this.colors.length - 1)], 0);
        }

        private int sample(int cx, int cy) {
            cx = Math.clamp(cx, 0, this.width - 1);
            cy = Math.clamp(cy, 0, this.height - 1);
            return ColorARGB.withAlpha(this.colors[cy * this.width + cx], 0);
        }

    }
}
