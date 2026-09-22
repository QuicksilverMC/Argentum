package dev.rdh.argentum.impl.render.terrain.compile.light;

import java.nio.IntBuffer;

import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.util.math.BlockPos;

import org.embeddedt.embeddium.api.util.ColorARGB;
import org.embeddedt.embeddium.api.util.NormI8;
import org.embeddedt.embeddium.impl.model.light.LightMode;
import org.embeddedt.embeddium.impl.model.light.LightPipeline;
import org.embeddedt.embeddium.impl.model.light.LightPipelineProvider;
import org.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import org.embeddedt.embeddium.impl.model.quad.ModelQuad;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFlags;

public final class SmoothFluidLighter {
    private static final int STRIDE = 7;
    private static final int COLOR_OFFSET = 3;
    private static final int LIGHT_OFFSET = 6;

    private final LightPipelineProvider lighters;
    private final ModelQuad quad = new ModelQuad();
    private final QuadLightData quadLight = new QuadLightData();
    private boolean ambientOcclusion;

    public SmoothFluidLighter(LightPipelineProvider lighters) {
        this.lighters = lighters;
    }

    public void beginSection() {
        this.ambientOcclusion = Minecraft.isAmbientOcclusionEnabled();
    }

    public void relight(BufferBuilder buffer, int firstVertex, BlockState state, BlockPos pos,
                        int originX, int originY, int originZ) {
        int vertexCount = buffer.getVertexCount();
        if (vertexCount - firstVertex < 4) {
            return;
        }

        boolean smooth = this.ambientOcclusion && state.getBlock().getLight() == 0;
        LightPipeline lighter = this.lighters.getLighter(smooth ? LightMode.SMOOTH : LightMode.FLAT);
        IntBuffer data = buffer.argentum$rawIntBuffer();

        float blockX = pos.getX() - originX;
        float blockY = pos.getY() - originY;
        float blockZ = pos.getZ() - originZ;

        int previousNormal = 0;
        float previousCx = Float.NaN, previousCy = Float.NaN, previousCz = Float.NaN;
        ModelQuadFacing previousFace = null;

        for (int vertex = firstVertex; vertex + 4 <= vertexCount; vertex += 4) {
            int base = vertex * STRIDE;
            float cx = 0.0F, cy = 0.0F, cz = 0.0F;

            for (int i = 0; i < 4; i++) {
                int offset = base + i * STRIDE;
                float x = Float.intBitsToFloat(data.get(offset)) - blockX;
                float y = Float.intBitsToFloat(data.get(offset + 1)) - blockY;
                float z = Float.intBitsToFloat(data.get(offset + 2)) - blockZ;
                this.quad.setX(i, x);
                this.quad.setY(i, y);
                this.quad.setZ(i, z);
                cx += x * 0.25F;
                cy += y * 0.25F;
                cz += z * 0.25F;
            }

            int normal = this.quad.getComputedFaceNormal();
            ModelQuadFacing lightFace;
            if (previousFace != null && isOppositeNormal(normal, previousNormal)
                    && equal(cx, previousCx) && equal(cy, previousCy) && equal(cz, previousCz)) {
                lightFace = previousFace;
            } else {
                lightFace = dominantFacing(normal);
            }
            previousNormal = normal;
            previousCx = cx;
            previousCy = cy;
            previousCz = cz;
            previousFace = lightFace;

            this.quad.setLightFace(lightFace);
            this.quad.setFlags(ModelQuadFlags.getQuadFlags(this.quad, lightFace));
            lighter.calculate(this.quad, pos.getX(), pos.getY(), pos.getZ(), this.quadLight, lightFace, lightFace, true, true);

            for (int i = 0; i < 4; i++) {
                int offset = base + i * STRIDE;
                data.put(offset + COLOR_OFFSET, multiplyRgb(data.get(offset + COLOR_OFFSET), this.quadLight.br[i]));
                data.put(offset + LIGHT_OFFSET, this.quadLight.lm[i]);
            }
        }
    }

    private static boolean equal(float a, float b) {
        return Math.abs(a - b) < 0.0001F;
    }

    private static boolean isOppositeNormal(int a, int b) {
        return NormI8.unpackX(a) == -NormI8.unpackX(b)
                && NormI8.unpackY(a) == -NormI8.unpackY(b)
                && NormI8.unpackZ(a) == -NormI8.unpackZ(b);
    }

    private static ModelQuadFacing dominantFacing(int normal) {
        float x = NormI8.unpackX(normal), y = NormI8.unpackY(normal), z = NormI8.unpackZ(normal);
        float ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ay >= ax && ay >= az) {
            return y < 0.0F ? ModelQuadFacing.NEG_Y : ModelQuadFacing.POS_Y;
        }
        if (ax >= az) {
            return x < 0.0F ? ModelQuadFacing.NEG_X : ModelQuadFacing.POS_X;
        }
        return z < 0.0F ? ModelQuadFacing.NEG_Z : ModelQuadFacing.POS_Z;
    }

    private static int multiplyRgb(int color, float brightness) {
		return ColorARGB.pack(
                (int) (ColorARGB.unpackRed(color) * brightness),
                (int) (ColorARGB.unpackGreen(color) * brightness),
                (int) (ColorARGB.unpackBlue(color) * brightness),
                ColorARGB.unpackAlpha(color)
        );
    }
}
