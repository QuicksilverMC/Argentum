package dev.rdh.argentum.impl.render.entity.instancing;

import dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer;

import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.model.quad.BakedQuadView;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.Map;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

final class BakedItemGeometryCache {
    private static final int[] NO_TINTS = new int[0];

    // keyed by the colour of every tint index the model uses: a generated item model gives each layer its own tint
    // index, so a potion's bottle and its liquid overlay do not share one
    private final Reference2ReferenceOpenHashMap<BakedModel, Object2ReferenceOpenHashMap<IntArrayList, InstanceGeometry>> itemGeometries = new Reference2ReferenceOpenHashMap<>();
    private final Reference2ReferenceOpenHashMap<BakedModel, Int2ObjectMap<InstanceGeometry>> fixedGeometries = new Reference2ReferenceOpenHashMap<>();
    private final Reference2ReferenceOpenHashMap<BakedModel, Int2ObjectMap<InstanceGeometry>> blockGeometries = new Reference2ReferenceOpenHashMap<>();

    private final Reference2ReferenceOpenHashMap<BakedModel, Boolean> layeredItems = new Reference2ReferenceOpenHashMap<>();

    private final IntArrayList tintKey = new IntArrayList();

    /** {@return whether the model has layers past the first} Those are the ones a mod may want to draw separately. */
    boolean isLayered(BakedModel model) {
        return this.layeredItems.computeIfAbsent(model, ignored -> {
            int highest = -1;
            for (Direction direction : Direction.values()) {
                highest = highestTintIndex(model.getQuads(direction), highest);
            }
            return highestTintIndex(model.getQuads(), highest) > 0;
        });
    }

    InstanceGeometry getItem(BakedModel model, ItemStack item) {
        if (model.isCustomRenderer()) {
            return null;
        }
        var byTint = this.itemGeometries.computeIfAbsent(model, ignored -> new Object2ReferenceOpenHashMap<>());
        IntArrayList key = this.tintKey;
        fillTints(key, model, item);
        InstanceGeometry geometry = byTint.get(key);
        if (geometry == null) {
            IntArrayList stored = new IntArrayList(key);
            geometry = new BakedItemGeometry(model, stored.toIntArray());
            byTint.put(stored, geometry);
        }
        return geometry;
    }

    boolean supportsItem(BakedModel model, ItemStack item) {
        return this.getItem(model, item) != null;
    }

    InstanceGeometry getFixed(BakedModel model, int color) {
        if (model.isCustomRenderer()) {
            return null;
        }
        return this.fixedGeometries.computeIfAbsent(model, ignored -> new Int2ObjectOpenHashMap<>())
                .computeIfAbsent(color, ignored -> new BakedItemGeometry(model, color, true));
    }

    InstanceGeometry getBlock(BakedModel model, float brightness, float red, float green, float blue) {
        if (model.isCustomRenderer()) {
            return null;
        }
        int color = toByte(brightness) << 24 | toByte(red) << 16 | toByte(green) << 8 | toByte(blue);
        return this.blockGeometries.computeIfAbsent(model, ignored -> new Int2ObjectOpenHashMap<>())
                .computeIfAbsent(color, ignored -> new BakedItemGeometry(
                        model, brightness, red, green, blue, true)
                );
    }

    void delete(CommandList commandList) {
        delete(this.itemGeometries, commandList);
        delete(this.fixedGeometries, commandList);
        delete(this.blockGeometries, commandList);
        this.layeredItems.clear();
    }

    private static void delete(Map<BakedModel, ? extends Map<?, InstanceGeometry>> geometries,
            CommandList commandList) {
        geometries.values().forEach(map -> map.values().forEach(geometry -> geometry.delete(commandList)));
        geometries.clear();
    }

    private static int toByte(float value) {
        return Math.clamp((int) (value * 255.0F + 0.5F), 0, 255);
    }

    private static void fillTints(IntArrayList output, BakedModel model, ItemStack item) {
        int highest = -1;
        for (Direction direction : Direction.values()) {
            highest = highestTintIndex(model.getQuads(direction), highest);
        }
        highest = highestTintIndex(model.getQuads(), highest);

        output.clear();
        for (int index = 0; index <= highest; index++) {
            output.add(item.getItem().getDisplayColor(item, index) | 0xFF000000);
        }
    }

    private static int highestTintIndex(Iterable<BakedQuad> quads, int highest) {
        for (BakedQuad quad : quads) {
            if (quad.hasTint()) {
                highest = Math.max(highest, quad.getTintIndex());
            }
        }
        return highest;
    }

    private static final class BakedItemGeometry extends InstanceGeometry {
        private final InstancedGeometryBuffer buffers;
        private final int vertexCount;

        private BakedItemGeometry(BakedModel model, float brightness, float red, float green, float blue, boolean block) {
            this(model, brightness, red, green, blue, block, -1, false, NO_TINTS);
        }

        private BakedItemGeometry(BakedModel model, int color, boolean fixed) {
            this(model, 1.0F, 1.0F, 1.0F, 1.0F, false, color, fixed, NO_TINTS);
        }

        private BakedItemGeometry(BakedModel model, int[] tints) {
            this(model, 1.0F, 1.0F, 1.0F, 1.0F, false, -1, false, tints);
        }

        private BakedItemGeometry(BakedModel model, float brightness, float red, float green, float blue, boolean block, int itemColor, boolean fixed, int[] tints) {
            int quads = model.getQuads().size();
            for (Direction direction : Direction.values()) {
                quads += model.getQuads(direction).size();
            }
            this.vertexCount = quads * 4;
            FloatBuffer vertices = BufferUtils.createFloatBuffer(this.vertexCount * 12);
            for (Direction direction : Direction.values()) {
                putQuads(vertices, model.getQuads(direction), brightness, red, green, blue, block, itemColor, fixed, tints);
            }
            putQuads(vertices, model.getQuads(), brightness, red, green, blue, block, itemColor, fixed, tints);
            vertices.flip();
            this.buffers = new InstancedGeometryBuffer(vertices, InstancedVertexFormats.ENTITY_VERTEX, InstancedVertexFormats.ENTITY_INSTANCE);
        }

        @Override
        public void render(CommandList commandList, Instances instances) {
            this.buffers.draw(commandList, instances.upload(), this.vertexCount, instances.count());
        }

        @Override
        public void delete(CommandList commandList) {
            this.buffers.delete(commandList);
        }

        private static void putQuads(FloatBuffer output, Iterable<BakedQuad> quads, float brightness,
                float red, float green, float blue, boolean block, int itemColor, boolean fixed, int[] tints) {
            for (BakedQuad quad : quads) {
                BakedQuadView view = (BakedQuadView)quad;
                Vec3i normal = quad.getFace().getNormal();
                int color = fixed ? itemColor : tintOf(quad, tints);
                float quadRed = block ? brightness * (quad.hasTint() ? red : 1.0F) : (color >> 16 & 0xFF) / 255.0F;
                float quadGreen = block ? brightness * (quad.hasTint() ? green : 1.0F) : (color >> 8 & 0xFF) / 255.0F;
                float quadBlue = block ? brightness * (quad.hasTint() ? blue : 1.0F) : (color & 0xFF) / 255.0F;
                float quadAlpha = block ? 1.0F : (color >>> 24) / 255.0F;
                for (int i = 0; i < 4; i++) {
                    output.put(view.getX(i)).put(view.getY(i)).put(view.getZ(i))
                            .put(view.getTexU(i)).put(view.getTexV(i))
                            .put(normal.getX()).put(normal.getY()).put(normal.getZ())
                            .put(quadRed).put(quadGreen).put(quadBlue).put(quadAlpha);
                }
            }
        }

        private static int tintOf(BakedQuad quad, int[] tints) {
            if (!quad.hasTint()) {
                return -1;
            }
            int index = quad.getTintIndex();
            return index >= 0 && index < tints.length ? tints[index] : -1;
        }
    }
}
