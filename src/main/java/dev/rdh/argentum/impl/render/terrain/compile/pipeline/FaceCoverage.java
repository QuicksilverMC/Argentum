package dev.rdh.argentum.impl.render.terrain.compile.pipeline;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.embeddedt.embeddium.impl.model.quad.BakedQuadView;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;

import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.util.math.Direction;

final class FaceCoverage {
    private static final float EPSILON = 1.0E-4F;
    private static final int TOUCHED = 4;
    private static final int CULLABLE = 8;

    private final Reference2ObjectOpenHashMap<BakedModel, long[][]> masks = new Reference2ObjectOpenHashMap<>();

    boolean isHidden(BakedModel model, BakedModel neighbor, Direction face) {
        long[] own = this.mask(model, face);
        if (own[CULLABLE] == 0L) return false;
        long[] other = this.mask(neighbor, face.getOpposite());
        for (int i = 0; i < 4; i++) {
            if ((own[TOUCHED + i] & ~other[i]) != 0L) return false;
        }
        return true;
    }

    private long[] mask(BakedModel model, Direction face) {
        long[][] faces = this.masks.get(model);
        if (faces == null) this.masks.put(model, faces = new long[6][]);
        long[] mask = faces[face.ordinal()];
        if (mask == null) faces[face.ordinal()] = mask = compute(model, face);
        return mask;
    }

    private static long[] compute(BakedModel model, Direction face) {
        long[] mask = new long[9];
        var quads = model.getQuads(face);
        boolean cullable = !quads.isEmpty();
        float plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0F : 0.0F;
        int axis = face.getAxis().ordinal();
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            float minU = Float.MAX_VALUE, minV = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
            boolean onPlane = true;
            for (int i = 0; i < 4; i++) {
                onPlane &= Math.abs(coordinate(vertices, i, axis) - plane) < EPSILON;
                float u = coordinate(vertices, i, (axis + 1) % 3);
                float v = coordinate(vertices, i, (axis + 2) % 3);
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
            if (!onPlane || minU < -EPSILON || minV < -EPSILON || maxU > 1.0F + EPSILON || maxV > 1.0F + EPSILON) {
                cullable = false;
                continue;
            }
            fill(mask, TOUCHED, (int)Math.floor(minU * 16.0F + EPSILON), (int)Math.ceil(maxU * 16.0F - EPSILON),
                    (int)Math.floor(minV * 16.0F + EPSILON), (int)Math.ceil(maxV * 16.0F - EPSILON));

            int corners = 0;
            for (int i = 0; i < 4; i++) {
                float u = coordinate(vertices, i, (axis + 1) % 3);
                float v = coordinate(vertices, i, (axis + 2) % 3);
                boolean lowU = Math.abs(u - minU) < EPSILON, lowV = Math.abs(v - minV) < EPSILON;
                if ((lowU || Math.abs(u - maxU) < EPSILON) && (lowV || Math.abs(v - maxV) < EPSILON)) {
                    corners |= 1 << ((lowU ? 0 : 1) | (lowV ? 0 : 2));
                }
            }
            if (corners == 0b1111 && quad.getFace() == face
                    && BakedQuadView.of(quad).getTransparencyLevel() == SpriteTransparencyLevel.OPAQUE) {
                fill(mask, 0, (int)Math.ceil(minU * 16.0F - EPSILON), (int)Math.floor(maxU * 16.0F + EPSILON),
                        (int)Math.ceil(minV * 16.0F - EPSILON), (int)Math.floor(maxV * 16.0F + EPSILON));
            }
        }
        mask[CULLABLE] = cullable ? 1L : 0L;
        return mask;
    }

    private static float coordinate(int[] vertices, int vertex, int axis) {
        return Float.intBitsToFloat(vertices[vertex * 7 + axis]);
    }

    private static void fill(long[] mask, int offset, int minU, int maxU, int minV, int maxV) {
        for (int v = Math.max(minV, 0); v < Math.min(maxV, 16); v++) {
            for (int u = Math.max(minU, 0); u < Math.min(maxU, 16); u++) {
                int cell = v * 16 + u;
                mask[offset + (cell >> 6)] |= 1L << (cell & 63);
            }
        }
    }
}
