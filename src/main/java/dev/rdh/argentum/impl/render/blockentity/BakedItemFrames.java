package dev.rdh.argentum.impl.render.blockentity;

import dev.rdh.argentum.impl.ext.ItemFrameEntityExtension;
import org.embeddedt.embeddium.impl.model.quad.BakedQuadView;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.util.position.SectionPos;

import net.minecraft.block.material.MapColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.ModelIdentifier;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.client.resource.model.ModelManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.Identifier;
import net.minecraft.util.TypeInstanceMultiMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.map.MapDecoration;
import net.minecraft.world.map.SavedMapData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BakedItemFrames {
    public static final long NONE = 0L;
    private static final long VALID = 1L << 62;
    private static final long ITEM = 1L << 61;
    private static final int MAP_SIZE = 128;
    private static final String MAP_PREFIX = "map_";
    private static final ModelIdentifier NORMAL_FRAME = new ModelIdentifier("item_frame", "normal");
    private static final ModelIdentifier MAP_FRAME = new ModelIdentifier("item_frame", "map");
    private static final Direction[] DIRECTIONS = Direction.values();

    private BakedItemFrames() {
    }

    public record Snapshot(ItemFrameEntity entity, long state, BlockPos hanging, float yaw, int rotation, BakedModel model,
            SlotSheet.Entry map, BakedModel item, int[] tints) {
    }

    public record Baked(ItemFrameEntity entity, long state) {
    }

    public static boolean frameBaked(ItemFrameEntity frame) {
        long baked = ((ItemFrameEntityExtension)frame).argentum$getBakedFrame();
        return baked != NONE && (baked & ~ITEM) == key(frame);
    }

    public static boolean itemBaked(ItemFrameEntity frame) {
        return frameBaked(frame) && (((ItemFrameEntityExtension)frame).argentum$getBakedFrame() & ITEM) != 0;
    }

    public static boolean fullyBaked(Entity entity) {
        if (!(entity instanceof ItemFrameEntity frame) || !frameBaked(frame)) return false;
        ItemStack stack = frame.getDisplayItem();
        return stack == null || !stack.hasCustomHoverName() && itemBaked(frame);
    }

    private static long key(ItemFrameEntity frame) {
        if (frame.dir == null) return NONE;
        long key = VALID | frame.dir.getIdHorizontal() | (long)(frame.rotation() & 7) << 2;
        ItemStack stack = frame.getDisplayItem();
        if (stack == null) return key;
        return key | 1L << 5 | (long)(stack.hasCustomHoverName() ? 1 : 0) << 6
                | (long)(Item.getId(stack.getItem()) & 0xFFFF) << 7 | (long)(stack.getMetadata() & 0xFFFF) << 23;
    }

    public static List<Snapshot> collect(World world, SectionPos section) {
        if (!BakedBlockEntities.enabled()) return List.of();
        List<Snapshot> frames = null;
        for (int i = 0; i < 5; i++) {
            int chunkX = section.x() + (i == 1 ? 1 : i == 2 ? -1 : 0);
            int chunkZ = section.z() + (i == 3 ? 1 : i == 4 ? -1 : 0);
            WorldChunk chunk = world.getChunkAt(chunkX, chunkZ);
            if (chunk.isEmpty()) continue;
            TypeInstanceMultiMap<Entity>[] entities = chunk.getEntities();
            if (section.y() < 0 || section.y() >= entities.length) continue;
            for (ItemFrameEntity frame : entities[section.y()].find(ItemFrameEntity.class)) {
                if (frame.dir == null || frame.removed) continue;
                BlockPos wall = frame.getBlockPos().offset(frame.dir.getOpposite());
                if (wall.getX() >> 4 != section.x() || wall.getY() >> 4 != section.y() || wall.getZ() >> 4 != section.z()) continue;
                if (frames == null) frames = new ArrayList<>();
                frames.add(snapshot(world, frame));
            }
        }
        return frames == null ? List.of() : frames;
    }

    private static Snapshot snapshot(World world, ItemFrameEntity frame) {
        Minecraft minecraft = Minecraft.getInstance();
        ModelManager models = minecraft.getBlockRenderDispatcher().getModelShaper().getManager();
        ItemStack stack = frame.getDisplayItem();
        boolean isMap = stack != null && stack.getItem() == Items.FILLED_MAP;
        SlotSheet.Entry map = null;
        BakedModel item = null;
        int[] tints = null;
        if (isMap) {
            SavedMapData data = Items.FILLED_MAP.getSavedMapData(stack, world);
            SlotSheet maps = minecraft.getBlocksAtlas().argentum$getMaps();
            if (data != null && maps != null && bakeable(data)) map = maps.upload(mapKey(data));
        } else if (stack != null) {
            BakedModel model = minecraft.getItemRenderer().getModelShaper().getModel(stack);
            if (!model.isCustomRenderer() && !stack.hasEnchantmentGlint() && stack.getItem() != Items.COMPASS) {
                tints = tints(stack, model);
            }
            if (tints != null) item = model;
        }
        return new Snapshot(frame, key(frame) | (map != null || item != null ? ITEM : 0), frame.getBlockPos(), frame.yaw,
                frame.rotation(), models.getModel(isMap ? MAP_FRAME : NORMAL_FRAME), map, item, tints);
    }

    private static int[] tints(ItemStack stack, BakedModel model) {
        int[] tints = new int[0];
        for (int i = 0; i <= DIRECTIONS.length; i++) {
            for (BakedQuad quad : i < DIRECTIONS.length ? model.getQuads(DIRECTIONS[i]) : model.getQuads()) {
                if (BakedQuadView.of(quad).celeritas$getSprite() instanceof TextureAtlasSprite sprite
                        && sprite.embeddium$getTransparencyLevel() == SpriteTransparencyLevel.TRANSLUCENT) {
                    return null;
                }
                if (!quad.hasTint()) continue;
                int index = quad.getTintIndex();
                if (index >= tints.length) tints = Arrays.copyOf(tints, index + 1);
                tints[index] = stack.getItem().getDisplayColor(stack, index);
            }
        }
        return tints;
    }

    private static boolean bakeable(SavedMapData data) {
        for (byte color : data.colors) {
            if ((color & 0xFF) / 4 == 0) return false;
        }
        for (MapDecoration decoration : data.decorations.values()) {
            if (decoration.getType() == 1) return false;
        }
        return true;
    }

    private static Identifier mapKey(SavedMapData data) {
        return new Identifier("argentum", data.id);
    }

    static int[] mapPixels(Identifier key) {
        World world = Minecraft.getInstance().world;
        SavedMapData data = world == null ? null : (SavedMapData)world.loadSavedData(SavedMapData.class, key.getPath());
        if (data == null) return null;
        int[] pixels = new int[MAP_SIZE * MAP_SIZE];
        for (int i = 0; i < pixels.length; i++) {
            int color = data.colors[i] & 0xFF;
            pixels[i] = color / 4 == 0 ? (i + i / MAP_SIZE & 1) * 8 + 16 << 24 : MapColor.BY_ID[color / 4].getColor(color & 3);
        }
        return pixels;
    }

    public static void onMapUpdated(SavedMapData data) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!BakedBlockEntities.enabled() || minecraft.world == null || !data.id.startsWith(MAP_PREFIX)) return;
        SlotSheet maps = minecraft.getBlocksAtlas().argentum$getMaps();
        if (maps != null) maps.refresh(mapKey(data));

        int number;
        try {
            number = Integer.parseInt(data.id.substring(MAP_PREFIX.length()));
        } catch (NumberFormatException exception) {
            return;
        }
        boolean bakeable = bakeable(data);
        for (Entity entity : minecraft.world.getEntities()) {
            if (entity instanceof ItemFrameEntity frame && frame.getDisplayItem() != null
                    && frame.getDisplayItem().getItem() == Items.FILLED_MAP && frame.getDisplayItem().getMetadata() == number
                    && itemBaked(frame) != bakeable) {
                rebuild(frame);
            }
        }
    }

    public static void forget(World world) {
        if (world == null) return;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof ItemFrameEntity frame) ((ItemFrameEntityExtension)frame).argentum$setBakedFrame(NONE);
        }
    }

    public static void rebuild(ItemFrameEntity frame) {
        if (frame.dir != null) BakedBlockEntities.rebuild(frame.world, frame.getBlockPos().offset(frame.dir.getOpposite()));
    }
}
