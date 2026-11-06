package dev.rdh.argentum.mixin.core.render;

import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;
import dev.rdh.argentum.impl.render.terrain.PlaneFrustum;

import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.FrustumData;

@Mixin(FrustumCuller.class)
public class FrustumCullerMixin implements ViewportProvider {
    @Shadow
    private FrustumData frustum;

    @Shadow
    private double offsetX, offsetY, offsetZ;

    @Override
    public Viewport sodium$createViewport() {
        ArgentumWorldRenderer.instance().captureMatrices(this.frustum.projectionMatrix, this.frustum.modelMatrix);

        Matrix4f modelMatrix = new Matrix4f();
        modelMatrix.set(frustum.modelMatrix);
        modelMatrix.invert();
        Vector3f offset = new Vector3f();
        modelMatrix.transformPosition(offset);
        final float[] planes = this.argentum$flattenPlanes(offset);
        return new Viewport(
                new PlaneFrustum(planes),
                new Vector3d(this.offsetX + offset.x, this.offsetY + offset.y, this.offsetZ + offset.z),
                this.argentum$viewProjection()
        );
    }

    @Unique
    private Matrix4f argentum$viewProjection() {
        return new Matrix4f().set(this.frustum.projectionMatrix)
                .mul(new Matrix4f().set(this.frustum.modelMatrix).setTranslation(0.0F, 0.0F, 0.0F));
    }

    @Unique
    private float[] argentum$flattenPlanes(Vector3f offset) {
        final float[][] source = this.frustum.frustum;
        final float[] planes = new float[source.length * 4];

        for (int i = 0; i < source.length; i++) {
            final float[] plane = source[i];
            final int base = i * 4;
            planes[base] = plane[0];
            planes[base + 1] = plane[1];
            planes[base + 2] = plane[2];
            planes[base + 3] = plane[3] + plane[0] * offset.x + plane[1] * offset.y + plane[2] * offset.z;
        }

        return planes;
    }
}
