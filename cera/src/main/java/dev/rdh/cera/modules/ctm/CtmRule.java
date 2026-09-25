package dev.rdh.cera.modules.ctm;

import dev.rdh.cera.Cera;
import dev.rdh.cera.props.BlockMatcher;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.AbstractLogBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.QuartzBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Nameable;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;

import org.embeddedt.embeddium.impl.model.quad.BakedQuadView;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import static dev.rdh.cera.modules.ctm.CtmLookup.axis;
import static dev.rdh.cera.modules.ctm.CtmLookup.directions;
import static dev.rdh.cera.modules.ctm.CtmLookup.horizontalDirections;
import static dev.rdh.cera.modules.ctm.CtmLookup.logicalFace;
import static dev.rdh.cera.modules.ctm.CtmLookup.tileIndex;
import static dev.rdh.cera.modules.ctm.CtmLookup.verticalDirections;

record CtmRule(
        String path,
        int weight,
        Map<Block, Integer> matchBlocks,
        Set<String> matchTiles,
        int metadata,
        int faces,
        IntPredicate heights,
        Set<String> biomes,
        Predicate<String> name,
        boolean innerSeams,
        Connect connect,
        Tile[] tiles,
        Action action,
        int metadataMax,
        Object2IntMap<Block> connectBlocks,
        Set<String> connectTiles
) {
    private static final String[] BIOME_NAMES = new String[256];

    boolean matches(WorldView world, BlockState state, BlockPos pos, Direction face, TextureAtlasSprite sprite) {
        int checkedMetadata = checkedMetadata(state);
        if (!matchBlocks.isEmpty()) {
            Integer blockMetadata = matchBlocks.get(state.getBlock());
            if (blockMetadata == null || (blockMetadata & 1 << checkedMetadata) == 0) return false;
        }
        if (!matchTiles.isEmpty() && !matchTiles.contains(sprite.getName())) return false;
        if ((metadata & 1 << checkedMetadata) == 0) return false;
        if ((faces & 1 << logicalFace(face, axis(state)).ordinal()) == 0) return false;
        if (!heights.test(pos.getY())) return false;
        if (!biomes.isEmpty()
                && !biomes.contains(biomeName(world.getBiome(pos)))) return false;
        if (name == null) return true;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity instanceof Nameable named && named.hasCustomName()
                && name.test(named.getName());
    }

    private static String biomeName(Biome biome) {
        String name = BIOME_NAMES[biome.id];
        if (name == null) BIOME_NAMES[biome.id] = name = CtmRuleLoader.normalizeBiome(biome.name);
        return name;
    }

    List<BakedQuad> compact(WorldView world, BlockState state, BlockPos pos, BakedQuad quad,
            QuadGeometry geometry, TextureAtlasSprite sprite, CtmRenderContext context,
            Compact action) {
        int connections = connections(world, state, pos, geometry, sprite, context);
        List<BakedQuad> cached = context.compact(this, quad, connections);
        if (cached != null) return cached;
        int override = action.overrides[tileIndex(connections)];
        if (override >= 0) {
            Tile tile = tiles[override];
            if (tile.action == TileAction.SKIP) return null;
            List<BakedQuad> result = tile.action == TileAction.DEFAULT || tile.sprite == sprite
                    ? List.of(quad)
                    : List.of(context.remap(quad, sprite, tile.sprite, quad.getTintIndex()));
            context.putCompact(this, quad, connections, result);
            return result;
        }

        TextureAtlasSprite[] compactSprites = new TextureAtlasSprite[5];
        for (int i = 0; i < compactSprites.length; i++) {
            if (tiles[i].action == TileAction.SKIP) continue;
            compactSprites[i] = tiles[i].action == TileAction.DEFAULT ? sprite : tiles[i].sprite;
        }
        List<BakedQuad> result = CompactCtm.transform(
                quad, geometry, sprite, compactSprites, connections);
        if (result != null) context.putCompact(this, quad, connections, result);
        return result;
    }

    private Tile top(WorldView world, BlockState state, BlockPos pos, Direction face, TextureAtlasSprite sprite, CtmRenderContext context) {
        int axis = axis(state);
        if (logicalFace(face, axis).getAxis() == Direction.Axis.Y) return null;
        Direction top = switch (axis) {
            case 0 -> Direction.UP;
            case 1 -> Direction.SOUTH;
            case 2 -> Direction.EAST;
            default -> throw new IllegalStateException();
        };
        return connects(world, state, pos, context.offset(pos, top), face, sprite, context) ? tiles[0] : null;
    }

    private Tile combined(WorldView world, BlockState state, BlockPos pos, Direction face, TextureAtlasSprite sprite, boolean horizontalFirst, CtmRenderContext context) {
        int axis = axis(state);
        int horizontal = lineIndex(world, state, pos, face, sprite,
                horizontalDirections(face, axis), context);
        int vertical = lineIndex(world, state, pos, face, sprite,
                verticalDirections(face, axis), context);
        int primary = horizontalFirst ? horizontal : vertical;
        int secondary = horizontalFirst ? vertical : horizontal;
        return tiles[primary == 3 && secondary != 3 ? secondary + 4 : primary];
    }

    private Tile line(WorldView world, BlockState state, BlockPos pos, Direction face, TextureAtlasSprite sprite, Direction[] directions, CtmRenderContext context) {
        return tiles[lineIndex(world, state, pos, face, sprite, directions, context)];
    }

    private int lineIndex(WorldView world, BlockState state, BlockPos pos, Direction face, TextureAtlasSprite sprite, Direction[] directions, CtmRenderContext context) {
        return lineIndex(
                connects(world, state, pos, context.offset(pos, directions[0]), face, sprite, context),
                connects(world, state, pos, context.offset(pos, directions[1]), face, sprite, context)
        );
    }

    private Tile random(WorldView world, BlockState state, BlockPos pos, Direction face,
            int[] weights, int randomLoops, int symmetry, boolean linked) {
        if (linked) {
            BlockPos below = pos.offset(Direction.DOWN);
            while (below.getY() >= 0 && world.getBlockState(below).getBlock() == state.getBlock()) {
                pos = below;
                below = below.offset(Direction.DOWN);
            }
        }
        int hash = Cera.random(pos, face.ordinal() / symmetry * symmetry) & Integer.MAX_VALUE;
        for (int i = 0; i < randomLoops; i++) hash = Cera.intHash(hash);
        if (weights == null) return tiles[hash % tiles.length];
        int total = weights[weights.length - 1];
        if (total <= 0) return tiles[0];
        int weighted = hash % total;
        for (int i = 0; i < weights.length; i++) {
            if (weighted < weights[i]) return tiles[i];
        }
        return tiles[0];
    }

    private Tile repeat(BlockPos pos, Direction face, int width, int height, int symmetry) {
        int side = face.ordinal() / symmetry * symmetry;
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int nx = switch (side) {
            case 0, 1, 3 -> x;
            case 2 -> -x - 1;
            case 4 -> z;
            default -> -z - 1;
        };
        int ny = switch (side) {
            case 0 -> -z - 1;
            case 1 -> z;
            default -> -y;
        };
        return tiles[Math.floorMod(ny, height) * width + Math.floorMod(nx, width)];
    }

    private int connections(WorldView world, BlockState state, BlockPos pos, QuadGeometry geometry,
            TextureAtlasSprite sprite, CtmRenderContext context) {
        Direction face = geometry.face;
        int axis = axis(state);
        Direction[] directions = directions(face, axis);
        boolean pane = PaneCulling.supports(state.getBlock());
        if (pane && face.getAxis() != Direction.Axis.Y
                && geometry.mirrored(directions[0])) {
            directions = directions(face.getOpposite(), axis);
        }
        int connections = 0;
        for (int i = 0; i < 4; i++) {
            Direction direction = directions[i];
            if (connects(world, state, pos, context.offset(pos, direction), face, sprite, context)
                    && (!pane || PaneCulling.covers(
                            world, state, pos, direction, geometry, context))) {
                connections |= 1 << (i * 2);
            }
        }
        if (Cera.CONFIG.connectedTextures == ConnectedTextures.Mode.FANCY) {
            for (int i = 0; i < 4; i++) {
                int next = (i + 1) & 3;
                if ((connections & 1 << (i * 2)) != 0 && (connections & 1 << (next * 2)) != 0) {
                    BlockPos corner = context.offset(pos, directions[i], directions[next]);
                    if ((!pane || PaneCulling.coversCorner(world, state, corner,
                            directions[i], directions[next], geometry))
                            && connects(world, state, pos, corner, face, sprite, context)) {
                        connections |= 1 << (i * 2 + 1);
                    }
                }
            }
        }
        return connections;
    }

    static void validate() {
        int[] vertices = new int[28];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 7;
            float x = vertex < 2 ? 0 : 1;
            vertices[offset] = Float.floatToRawIntBits(x);
            vertices[offset + 4] = Float.floatToRawIntBits(x);
        }
        BakedQuad quad = new BakedQuad(vertices, -1, Direction.SOUTH);
        if (QuadGeometry.of(quad, null).mirrored(Direction.WEST)) {
            throw new IllegalStateException("Canonical pane UVs detected as mirrored");
        }
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 7;
            float u = Float.intBitsToFloat(vertices[offset + 4]);
            vertices[offset + 4] = Float.floatToRawIntBits(1 - u);
        }
        if (!QuadGeometry.of(new BakedQuad(vertices, -1, Direction.SOUTH), null)
                .mirrored(Direction.WEST)) {
            throw new IllegalStateException("Mirrored pane UVs not detected");
        }
    }

    private boolean connects(WorldView world, BlockState state, BlockPos ignoredPos, BlockPos otherPos,
							 Direction face, TextureAtlasSprite sprite, CtmRenderContext context) {
        if (!connectsOnce(world, state, otherPos, face, sprite, context)) return false;
        return !innerSeams || !connectsOnce(world, state, context.offset(otherPos, face), face, sprite, context);
    }

    boolean connectsOnce(WorldView world, BlockState state, BlockPos otherPos,
            Direction face, TextureAtlasSprite sprite, CtmRenderContext context) {
        BlockState other = world.getBlockState(otherPos);
        if (state == other) return true;
        return switch (connect) {
            case BLOCK -> state.getBlock() == other.getBlock()
                    && state.getBlock().getMetadataFromState(state)
                    == other.getBlock().getMetadataFromState(other);
            case MATERIAL -> state.getBlock().getMaterial() == other.getBlock().getMaterial();
            case TILE -> context.neighborSprite(world, other, otherPos, face) == sprite;
            case STATE -> false;
        };
    }

    boolean matchesConnectBlock(BlockState state) {
        return connectBlocks.isEmpty() || BlockMatcher.matches(connectBlocks, state);
    }

    boolean matchesConnectTile(WorldView world, BlockState state, BlockPos pos, Direction face, CtmRenderContext context) {
        return connectTiles.isEmpty() || connectTiles.contains(spriteName(world, state, pos, face, context));
    }

    boolean matchesNeighbor(WorldView world, BlockState state, BlockPos pos, Direction face, TextureAtlasSprite sprite, CtmRenderContext context) {
        if (state.getBlock() == Blocks.AIR) return false;
        if (!matchBlocks.isEmpty()) {
            Integer mask = matchBlocks.get(state.getBlock());
			if (mask == null || (mask & 1 << checkedMetadata(state)) == 0) return false;
        }
        return matchTiles.isEmpty() || spriteName(world, state, pos, face, context).equals(sprite.getName());
    }

    private int checkedMetadata(BlockState state) {
        int value = state.getBlock().getMetadataFromState(state);
        if (state.getBlock() instanceof AbstractLogBlock && metadataMax <= 3) return value & 3;
        if (state.getBlock() instanceof QuartzBlock && metadataMax <= 2 && value > 2) return 2;
        return value;
    }

    static BakedQuad remap(BakedQuad quad, TextureAtlasSprite from, TextureAtlasSprite to,
            int tintIndex) {
        int[] vertices = quad.getVertices().clone();
        float fromWidth = from.getUMax() - from.getUMin();
        float fromHeight = from.getVMax() - from.getVMin();
        float toWidth = to.getUMax() - to.getUMin();
        float toHeight = to.getVMax() - to.getVMin();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride + 4;
            float u = Float.intBitsToFloat(vertices[offset]);
            float v = Float.intBitsToFloat(vertices[offset + 1]);
            vertices[offset] = Float.floatToRawIntBits(
                    to.getUMin() + (u - from.getUMin()) / fromWidth * toWidth);
            vertices[offset + 1] = Float.floatToRawIntBits(
                    to.getVMin() + (v - from.getVMin()) / fromHeight * toHeight);
        }
        return new BakedQuad(vertices, tintIndex, quad.getFace());
    }

    static TextureAtlasSprite sprite(BakedQuad quad) {
        return (TextureAtlasSprite)BakedQuadView.of(quad).celeritas$getSprite();
    }

    private static int lineIndex(boolean first, boolean second) {
        if (first && second) return 1;
        else if (first) return 2;
        else if (second) return 0;
        else return 3;
    }

    static Replacement ctm() {
        return new Replacement((rule, world, state, pos, geometry, sprite, context) ->
                rule.tiles[tileIndex(rule.connections(
                        world, state, pos, geometry, sprite, context))], true);
    }

    static Compact compact(int[] overrides) {
        return new Compact(overrides);
    }

    static Replacement horizontal() {
        return new Replacement((rule, world, state, pos, geometry, sprite, context) ->
                rule.line(world, state, pos, geometry.face, sprite,
                        horizontalDirections(geometry.face, axis(state)), context), false);
    }

    static Replacement vertical() {
        return new Replacement((rule, world, state, pos, geometry, sprite, context) ->
                rule.line(world, state, pos, geometry.face, sprite,
                        verticalDirections(geometry.face, axis(state)), context), false);
    }

    static Replacement top() {
        return new Replacement((rule, world, state, pos, geometry, sprite, context) ->
                rule.top(world, state, pos, geometry.face, sprite, context), false);
    }

    static Replacement random(int[] weights, int randomLoops, int symmetry, boolean linked) {
        return new Replacement((rule, world, state, pos, geometry, _, _) ->
                rule.random(world, state, pos, geometry.face,
                        weights, randomLoops, symmetry, linked), false);
    }

    static Replacement repeat(int width, int height, int symmetry) {
        return new Replacement((rule, _, _, pos, geometry, _, _) ->
                rule.repeat(pos, geometry.face, width, height, symmetry), false);
    }

    static Replacement fixed() {
        return new Replacement((rule, _, _, _, _, _, _) ->
                rule.tiles[0], false);
    }

    static Replacement combined(boolean horizontalFirst) {
        return new Replacement((rule, world, state, pos, geometry, sprite, context) ->
                rule.combined(world, state, pos, geometry.face, sprite,
                        horizontalFirst, context), false);
    }

    static Decoration overlay(int tintIndex, BlockState tintState, BlockLayer layer) {
        return new Decoration((rule, world, state, pos, geometry, sprite, context) ->
                OverlayCtm.select(rule, world, state, pos, geometry.face, sprite, context),
                tintIndex, tintState, layer, false);
    }

    static Decoration overlay(Replacement action, int tintIndex, BlockState tintState,
            BlockLayer layer) {
        return new Decoration((rule, world, state, pos, geometry, sprite, context) -> {
            Tile tile = action.selector.select(rule, world, state, pos, geometry, sprite, context);
            return tile == null || tile.action == TileAction.SKIP ? List.of() : List.of(tile);
        }, tintIndex, tintState, layer, action.paneGeometry);
    }

    private static String spriteName(WorldView world, BlockState state, BlockPos pos, Direction face,
            CtmRenderContext context) {
        TextureAtlasSprite sprite = context.neighborSprite(world, state, pos, face);
        return sprite == null ? "" : sprite.getName();
    }

    record Tile(TextureAtlasSprite sprite, TileAction action) {
    }

    sealed interface Action permits Replacement, Compact, Decoration {
        boolean paneGeometry();
    }

    record Replacement(Selector selector, boolean paneGeometry) implements Action {
    }

    record Compact(int[] overrides) implements Action {
        @Override
        public boolean paneGeometry() {
            return true;
        }
    }

    record Decoration(OverlaySelector selector, int tintIndex, BlockState tintState,
            BlockLayer layer, boolean paneGeometry) implements Action {
    }

    @FunctionalInterface
    interface Selector {
        Tile select(CtmRule rule, WorldView world, BlockState state, BlockPos pos,
                QuadGeometry geometry, TextureAtlasSprite sprite, CtmRenderContext context);
    }

    @FunctionalInterface
    interface OverlaySelector {
        List<Tile> select(CtmRule rule, WorldView world, BlockState state, BlockPos pos,
                QuadGeometry geometry, TextureAtlasSprite sprite, CtmRenderContext context);
    }

    enum TileAction {
        REPLACE,
        DEFAULT,
        SKIP
    }

    enum Connect {
        BLOCK,
        TILE,
        MATERIAL,
        STATE
    }

    enum Method {
        CTM,
        CTM_COMPACT,
        HORIZONTAL,
        VERTICAL,
        TOP,
        RANDOM,
        REPEAT,
        FIXED,
        HORIZONTAL_VERTICAL,
        VERTICAL_HORIZONTAL,
        OVERLAY,
        OVERLAY_FIXED,
        OVERLAY_RANDOM,
        OVERLAY_REPEAT,
        OVERLAY_CTM;

        boolean overlay() {
            return switch (this) {
                case OVERLAY, OVERLAY_FIXED, OVERLAY_RANDOM, OVERLAY_REPEAT, OVERLAY_CTM -> true;
                default -> false;
            };
        }

        static Method parse(String value) {
            return switch (value.trim()) {
                case "ctm", "glass" -> CTM;
                case "ctm_compact" -> CTM_COMPACT;
                case "horizontal", "bookshelf" -> HORIZONTAL;
                case "vertical" -> VERTICAL;
                case "top" -> TOP;
                case "random" -> RANDOM;
                case "repeat" -> REPEAT;
                case "fixed" -> FIXED;
                case "horizontal+vertical", "h+v" -> HORIZONTAL_VERTICAL;
                case "vertical+horizontal", "v+h" -> VERTICAL_HORIZONTAL;
                case "overlay" -> OVERLAY;
                case "overlay_fixed" -> OVERLAY_FIXED;
                case "overlay_random" -> OVERLAY_RANDOM;
                case "overlay_repeat" -> OVERLAY_REPEAT;
                case "overlay_ctm" -> OVERLAY_CTM;
                default -> null;
            };
        }
    }
}
