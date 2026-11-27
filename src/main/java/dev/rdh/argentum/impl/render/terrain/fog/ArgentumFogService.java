package dev.rdh.argentum.impl.render.terrain.fog;

import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.fog.SkyTextureCopy;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderEnvironment;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.ArgentumGlStateService;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GlStateManager;

public class ArgentumFogService implements FogService {
    public static final ArgentumFogService INSTANCE = new ArgentumFogService();
    public static final ChunkShaderEnvironment ENVIRONMENT = new ChunkShaderEnvironment(INSTANCE);
    public static final List<ChunkFogMode> GL_FOG_MODES = List.of(ChunkFogMode.NONE, ChunkFogMode.EXP, ChunkFogMode.EXP2, ChunkFogMode.SMOOTH);

    private final SkyTextureCopy skyTexture = new SkyTextureCopy(ArgentumGlStateService.INSTANCE);

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

    public ChunkFogMode getTerrainFogMode() {
        ChunkFogMode mode = this.getFogMode();
        return mode == ChunkFogMode.SMOOTH && this.getSkyTexture() != 0 ? ChunkFogMode.SMOOTH_SKY : mode;
    }

    @Override
    public int getSkyTexture() {
        return this.useSkyFog() ? this.skyTexture.getTexture() : 0;
    }

    private boolean useSkyFog() {
        var target = Minecraft.getInstance().argentum$getTerrainDrawTarget();
        return Argentum.CONFIG.useSkyFog && (target == null || target.vertexType() == null);
    }

    public void captureSkyTexture() {
        if (!this.useSkyFog()) {
            this.skyTexture.delete();
            return;
        }

        var target = Minecraft.getInstance().getRenderTarget();
        this.skyTexture.copy(target.viewWidth, target.viewHeight);
    }

    public void invalidateSkyTexture() {
        this.skyTexture.invalidate();
    }

    public void deleteSkyTexture() {
        this.skyTexture.delete();
    }

    @Override
    public float getSkyFogEdgeStart() {
        return this.getSkyFogEdgeEnd() * 0.75F;
    }

    @Override
    public float getSkyFogEdgeEnd() {
        return Minecraft.getInstance().options.viewDistance * 16.0F;
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
