package dev.rdh.cera.modules;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DirtBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.model.block.BlockElementFace;
import net.minecraft.client.render.model.block.BlockElementTexture;
import net.minecraft.client.render.model.block.FaceBakery;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.client.resource.model.ModelRotation;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.lwjgl.util.vector.Vector3f;

import dev.rdh.cera.Cera;
import dev.rdh.cera.props.Props;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class BetterGrass {
    private static final Identifier CONFIG = new Identifier("optifine/bettergrass.properties");
    private Pending pending;
    private volatile State state = State.EMPTY;

    public void reload(ResourceManager resources, TextureAtlas atlas, Map<String, TextureAtlasSprite> sourcedSprites) {
        Props properties = load(resources);

        pending = new Pending(
                enabled(properties, "grass"),
                enabled(properties, "mycelium"),
                enabled(properties, "podzol"),
                enabled(properties, "grass.snow"),
                enabled(properties, "mycelium.snow"),
                enabled(properties, "podzol.snow"),
                enabled(properties, "grass.multilayer", false),
                register(properties, "texture.grass", "blocks/grass_top", resources, atlas, sourcedSprites),
                register(properties, "texture.grass_side", "blocks/grass_side", resources, atlas, sourcedSprites),
                register(properties, "texture.mycelium", "blocks/mycelium_top", resources, atlas, sourcedSprites),
                register(properties, "texture.podzol", "blocks/dirt_podzol_top", resources, atlas, sourcedSprites),
                register(properties, "texture.snow", "blocks/snow", resources, atlas, sourcedSprites)
        );
    }

    public void bake() {
        Pending pending = this.pending;
        if (pending == null) return;

        Map<Direction, List<BakedQuad>> grass = cube(pending.grass, 0);
        if (pending.grassMultilayer) {
            Map<Direction, List<BakedQuad>> grassSide = cube(pending.grassSide, -1);
            EnumMap<Direction, List<BakedQuad>> layered = new EnumMap<>(Direction.class);
            for (Direction direction : Direction.values()) {
                layered.put(direction, List.of(grassSide.get(direction).getFirst(), grass.get(direction).getFirst()));
            }
            grass = Map.copyOf(layered);
        }

        state = new State(
                pending.grassEnabled,
                pending.myceliumEnabled,
                pending.podzolEnabled,
                pending.grassSnowEnabled,
                pending.myceliumSnowEnabled,
                pending.podzolSnowEnabled,
                grass,
                cube(pending.mycelium, -1),
                cube(pending.podzol, 0),
                cube(pending.snow, -1)
        );
    }

    public List<BakedQuad> getFaceQuads(WorldView world, BlockState blockState, BlockPos pos, Direction face, List<BakedQuad> original) {
        Mode mode = Cera.CONFIG.betterGrass;
        if (mode == Mode.OFF || face == null || !face.getAxis().isHorizontal()) return original;

        State state = this.state;
        Block block = blockState.getBlock();
        if (block != Blocks.GRASS && block != Blocks.MYCELIUM
                && (block != Blocks.DIRT || blockState.get(DirtBlock.VARIANT) != DirtBlock.Variant.PODZOL)) return original;
        Block above = world.getBlockState(pos.up()).getBlock();
        boolean snowy = above == Blocks.SNOW || above == Blocks.SNOW_LAYER;

        if (block == Blocks.GRASS) {
            if (snowy) {
                return state.grassSnowEnabled && connects(mode, world, pos, face, Blocks.SNOW_LAYER)
                        ? state.snow.get(face) : original;
            }
            return state.grassEnabled && connects(mode, world, pos.down(), face, Blocks.GRASS)
                    ? state.grass.get(face) : original;
        }

        if (block == Blocks.MYCELIUM) {
            if (snowy) {
                return state.myceliumSnowEnabled && connects(mode, world, pos, face, Blocks.SNOW_LAYER)
                        ? state.snow.get(face) : original;
            }
            return state.myceliumEnabled && connects(mode, world, pos.down(), face, Blocks.MYCELIUM)
                    ? state.mycelium.get(face) : original;
        }

        if (block == Blocks.DIRT && blockState.get(DirtBlock.VARIANT) == DirtBlock.Variant.PODZOL) {
            if (snowy) {
                return state.podzolSnowEnabled && connects(mode, world, pos, face, Blocks.SNOW_LAYER)
                        ? state.snow.get(face) : original;
            }
            BlockState neighbor = world.getBlockState(pos.down().offset(face));
            return state.podzolEnabled && (mode == Mode.FAST
                    || neighbor.getBlock() == Blocks.DIRT
                    && neighbor.get(DirtBlock.VARIANT) == DirtBlock.Variant.PODZOL)
                    ? state.podzol.get(face) : original;
        }

        return original;
    }

    private static boolean connects(Mode mode, WorldView world, BlockPos pos, Direction face, Block block) {
        return mode == Mode.FAST || world.getBlockState(pos.offset(face)).getBlock() == block;
    }

    private static Props load(ResourceManager resources) {
        try {
            Resource resource = resources.getResource(CONFIG).orElse(null);
            return resource == null ? new Props(CONFIG, new Properties()) : new Props(resource);
        } catch (IOException e) {
            Cera.LOGGER.warn("[BetterGrass] Failed to load properties", e);
            return new Props(CONFIG, new Properties());
        }
    }

    private static boolean enabled(Props properties, String key) {
        return enabled(properties, key, true);
    }

    private static boolean enabled(Props properties, String key, boolean fallback) {
        var result = properties.getBoolean(key, fallback);
        if (!result.isSuccess()) Cera.LOGGER.warn("[BetterGrass] {}", result.error());
        return result.orElse(false);
    }

    private static TextureAtlasSprite register(Props properties, String key, String fallback,
            ResourceManager resources, TextureAtlas atlas, Map<String, TextureAtlasSprite> sourcedSprites) {
        Identifier id = properties.parseId(properties.get(key, fallback));
        Identifier resource = new Identifier(id.getNamespace(), "textures/" + id.getPath() + ".png");
        if (!resources.hasResource(resource)) {
            Cera.LOGGER.warn("[BetterGrass] texture not found: {}", resource);
            id = new Identifier(fallback);
        }
        TextureAtlasSprite sprite = sourcedSprites.get(id.toString());
        return sprite == null ? atlas.registerSprite(id) : sprite;
    }

    private static Map<Direction, List<BakedQuad>> cube(TextureAtlasSprite sprite, int tintIndex) {
        FaceBakery bakery = new FaceBakery();
        EnumMap<Direction, List<BakedQuad>> faces = new EnumMap<>(Direction.class);
        Vector3f from = new Vector3f(0.0F, 0.0F, 0.0F);
        Vector3f to = new Vector3f(16.0F, 16.0F, 16.0F);
        for (Direction direction : Direction.values()) {
            BlockElementTexture texture = new BlockElementTexture(new float[]{0.0F, 0.0F, 16.0F, 16.0F}, 0);
            BlockElementFace face = new BlockElementFace(direction, tintIndex, "", texture);
            faces.put(direction, List.of(bakery.bakeQuad(from, to, face, sprite, direction, ModelRotation.X0_Y0, null, false, true)));
        }
        return Map.copyOf(faces);
    }

    private record Pending(
            boolean grassEnabled,
            boolean myceliumEnabled,
            boolean podzolEnabled,
            boolean grassSnowEnabled,
            boolean myceliumSnowEnabled,
            boolean podzolSnowEnabled,
            boolean grassMultilayer,
            TextureAtlasSprite grass,
            TextureAtlasSprite grassSide,
            TextureAtlasSprite mycelium,
            TextureAtlasSprite podzol,
            TextureAtlasSprite snow
    ) {
    }

    private record State(
            boolean grassEnabled,
            boolean myceliumEnabled,
            boolean podzolEnabled,
            boolean grassSnowEnabled,
            boolean myceliumSnowEnabled,
            boolean podzolSnowEnabled,
            Map<Direction, List<BakedQuad>> grass,
            Map<Direction, List<BakedQuad>> mycelium,
            Map<Direction, List<BakedQuad>> podzol,
            Map<Direction, List<BakedQuad>> snow
    ) {
        private static final State EMPTY = new State(false, false, false, false, false, false, Map.of(), Map.of(), Map.of(), Map.of());
    }

    public enum Mode {
        OFF,
        FAST,
        FANCY;

        public String key() {
            return "value.better_grass." + this.name().toLowerCase();
        }
    }
}
