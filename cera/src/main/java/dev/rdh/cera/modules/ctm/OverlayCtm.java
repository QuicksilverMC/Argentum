package dev.rdh.cera.modules.ctm;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.GlassBlock;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

import static dev.rdh.cera.modules.ctm.CtmLookup.axis;

final class OverlayCtm {
    private static final Direction[][] SIDES = {
            {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH},
            {Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH},
            {Direction.EAST, Direction.WEST, Direction.DOWN, Direction.UP},
            {Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP},
            {Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP},
            {Direction.SOUTH, Direction.NORTH, Direction.DOWN, Direction.UP}
    };
    private static final Direction[] NORTH_AXIS = {
            Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN
    };
    private static final Direction[] EAST_AXIS = {
            Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN
    };

    private OverlayCtm() {
    }

    static List<CtmRule.Tile> select(CtmRule rule, WorldView world, BlockState state, BlockPos pos, Direction face, TextureAtlasSprite sprite, CtmRenderContext context) {
        Direction[] directions = sides(face, axis(state));
        boolean[] sides = new boolean[4];
        for (int i = 0; i < sides.length; i++) {
            sides[i] = isOverlayNeighbor(rule, world, state, context.offset(pos, directions[i]),
                    face, sprite, context);
        }

        if (sides[0] && sides[1] && sides[2] && sides[3]) return tile(rule, 8);
        if (sides[0] && sides[1] && sides[2]) return tile(rule, 5);
        if (sides[0] && sides[2] && sides[3]) return tile(rule, 6);
        if (sides[1] && sides[2] && sides[3]) return tile(rule, 12);
        if (sides[0] && sides[1] && sides[3]) return tile(rule, 13);

        boolean[] edges = new boolean[4];
        edges[0] = isOverlayNeighbor(rule, world, state,
                context.offset(pos, directions[1], directions[2]), face, sprite, context);
        edges[1] = isOverlayNeighbor(rule, world, state,
                context.offset(pos, directions[0], directions[2]), face, sprite, context);
        edges[2] = isOverlayNeighbor(rule, world, state,
                context.offset(pos, directions[1], directions[3]), face, sprite, context);
        edges[3] = isOverlayNeighbor(rule, world, state,
                context.offset(pos, directions[0], directions[3]), face, sprite, context);

        if (sides[1] && sides[2]) return tiles(rule, 3, edges[3] ? 16 : -1);
        if (sides[0] && sides[2]) return tiles(rule, 4, edges[2] ? 14 : -1);
        if (sides[1] && sides[3]) return tiles(rule, 10, edges[1] ? 2 : -1);
        if (sides[0] && sides[3]) return tiles(rule, 11, edges[0] ? 0 : -1);

        boolean[] matching = new boolean[4];
        for (int i = 0; i < matching.length; i++) {
            BlockPos neighbor = context.offset(pos, directions[i]);
            matching[i] = rule.matchesNeighbor(world, world.getBlockState(neighbor),
                    neighbor, face, sprite, context)
                    && exposed(world, neighbor, face, context);
        }

        List<CtmRule.Tile> selected = new ObjectArrayList<>(8);
        add(rule, selected, sides[0] ? 9 : -1);
        add(rule, selected, sides[1] ? 7 : -1);
        add(rule, selected, sides[2] ? 1 : -1);
        add(rule, selected, sides[3] ? 15 : -1);
        add(rule, selected, edges[0] && (matching[1] || matching[2]) && !sides[1] && !sides[2] ? 0 : -1);
        add(rule, selected, edges[1] && (matching[0] || matching[2]) && !sides[0] && !sides[2] ? 2 : -1);
        add(rule, selected, edges[2] && (matching[1] || matching[3]) && !sides[1] && !sides[3] ? 14 : -1);
        add(rule, selected, edges[3] && (matching[0] || matching[3]) && !sides[0] && !sides[3] ? 16 : -1);
        return selected;
    }

    private static boolean isOverlayNeighbor(CtmRule rule, WorldView world, BlockState state,
            BlockPos pos, Direction face, TextureAtlasSprite sprite, CtmRenderContext context) {
        BlockState neighbor = world.getBlockState(pos);
        Block block = neighbor.getBlock();
        return (block.isCube() || block instanceof GlassBlock || block instanceof StainedGlassBlock)
                && rule.matchesConnectBlock(neighbor)
                && rule.matchesConnectTile(world, neighbor, pos, face, context)
                && !rule.connectsOnce(world, state, pos, face, sprite, context)
                && exposed(world, pos, face, context);
    }

    private static boolean exposed(WorldView world, BlockPos pos, Direction face, CtmRenderContext context) {
        Block block = world.getBlockState(context.offset(pos, face)).getBlock();
        return !block.isOpaque() && (face != Direction.UP || block != Blocks.SNOW_LAYER);
    }

    private static Direction[] sides(Direction face, int axis) {
        if (face == Direction.NORTH && axis == 1) return NORTH_AXIS;
        if (face == Direction.EAST && axis == 2) return EAST_AXIS;
        return SIDES[face.ordinal()];
    }

    private static List<CtmRule.Tile> tile(CtmRule rule, int index) {
        return tiles(rule, index, -1);
    }

    private static List<CtmRule.Tile> tiles(CtmRule rule, int first, int second) {
        List<CtmRule.Tile> selected = new ObjectArrayList<>(2);
        add(rule, selected, first);
        add(rule, selected, second);
        return selected;
    }

    private static void add(CtmRule rule, List<CtmRule.Tile> selected, int index) {
        if (index >= 0 && rule.tiles()[index].action() != CtmRule.TileAction.SKIP) {
            selected.add(rule.tiles()[index]);
        }
    }
}
