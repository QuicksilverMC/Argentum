package dev.rdh.argentum.impl.render.terrain.fog;

import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderEnvironment;

import dev.rdh.argentum.impl.Argentum;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GlStateManager;

public class ArgentumFogService implements FogService {
    public static final ArgentumFogService INSTANCE = new ArgentumFogService();
    public static final ChunkShaderEnvironment ENVIRONMENT = new ChunkShaderEnvironment(INSTANCE);

    @Override
    public float getFogEnd() {
        return GlStateManager.FOG.end;
    }

    @Override
    public float getFogStart() {
        return GlStateManager.FOG.start;
    }

    @Override
    public float getFogDensity() {
        return GlStateManager.FOG.density;
    }

    @Override
    public int getFogShapeIndex() {
        return Argentum.CONFIG.fogShape.index;
    }

    @Override
    public float getFogCutoff() {
        return getFogEnd();
    }

    @Override
    public float[] getFogColor() {
        GameRenderer gr = Minecraft.getInstance().gameRenderer;
        return new float[]{gr.fogRed, gr.fogGreen, gr.fogBlue, 1.0F};
    }

    @Override
    public ChunkFogMode getFogMode() {
        if (!GlStateManager.FOG.state.enabled) {
            return ChunkFogMode.NONE;
        }
        return ChunkFogMode.fromGLMode(GlStateManager.FOG.mode);
    }

    public enum FogShape {
        SPHERICAL(FOG_SHAPE_SPHERICAL),
        CYLINDICAL(FOG_SHAPE_CYLINDRICAL),
        PLANAR(FOG_SHAPE_PLANAR),
        ;

        public final int index;

        FogShape(int i) {
            this.index = i;
        }
    }
}
