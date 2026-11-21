package dev.rdh.argentum.impl.render.terrain;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass.PipelineState;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.embeddedt.embeddium.impl.render.chunk.terrain.sampling.AtlasEdgeMask;
import org.embeddedt.embeddium.impl.render.chunk.terrain.sampling.TerrainSampling;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL33C;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.ArgentumGlStateService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureManager;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RenderPassConfigurationBuilder {
    public static final Object DECAL = new Object();
    public static final Object BLENDED_DECAL = new Object();
    public static final Object UNMIPPED_SOLID = new Object();
    static final int ATLAS_EDGE_MASK_TEXTURE_UNIT = 2;

    private static int linearAtlasSampler;

    private static int getLinearAtlasSampler() {
        if (linearAtlasSampler == 0) {
            int sampler = GL33C.glGenSamplers();
            GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_LINEAR_MIPMAP_LINEAR);
            GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR);
            GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
            GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
            linearAtlasSampler = sampler;
        }
        return linearAtlasSampler;
    }

    private static PipelineState withEdgeMask(PipelineState base, @Nullable AtlasEdgeMask edgeMask) {
        if (edgeMask == null) {
            return base;
        }
        return new PipelineState() {
            @Override
            public void setup() {
                base.setup();
                GL33C.glBindSampler(0, getLinearAtlasSampler());
                int previous = ArgentumGlStateService.INSTANCE.activateTextureUnit(GLX.GL_TEXTURE0 + ATLAS_EDGE_MASK_TEXTURE_UNIT);
                GlStateManager.bindTexture(edgeMask.handle());
                edgeMask.upload();
                GL33C.glBindSampler(ATLAS_EDGE_MASK_TEXTURE_UNIT, 0);
                ArgentumGlStateService.INSTANCE.activateTextureUnit(previous);
            }

            @Override
            public void clear() {
                GL33C.glBindSampler(0, 0);
                ArgentumGlStateService.INSTANCE.bindTexture(ATLAS_EDGE_MASK_TEXTURE_UNIT, GL11.GL_TEXTURE_2D, 0);
                base.clear();
            }
        };
    }

    private static TerrainSampling createTerrainSampling(boolean allowFiltering) {
        if (!allowFiltering || !Argentum.CONFIG.rgssTextureFiltering || !Argentum.rgssFilteringSupported()) {
            return TerrainSampling.NATIVE;
        }
        TextureAtlas atlas = Minecraft.getInstance().getBlocksAtlas();
        AtlasEdgeMask mask = AtlasEdgeMask.create(
                atlas.argentum$getWidth(),
                atlas.argentum$getHeight(),
                atlas.argentum$getMipLevel(),
                atlas.argentum$getSpriteRects()
        );
        return mask != null ? new TerrainSampling.EdgeMasked(TerrainSampling.Filter.RGSS, mask) : TerrainSampling.NATIVE;
    }

    private static final TerrainRenderPass.PipelineState DISABLE_BLEND_PIPELINE_STATE = new PipelineState() {
        @Override
        public void setup() {
            GlStateManager.disableAlphaTest();
        }

        @Override
        public void clear() {
            GlStateManager.enableAlphaTest();
        }
    };

    private static final TerrainRenderPass.PipelineState DECAL_PIPELINE_STATE = new PipelineState() {
        @Override
        public void setup() {
            GlStateManager.disableAlphaTest();
            GlStateManager.enablePolygonOffset();
            GlStateManager.polygonOffset(-1.0F, -1.0F);
        }

        @Override
        public void clear() {
            GlStateManager.disablePolygonOffset();
            GlStateManager.enableAlphaTest();
        }
    };

    private static final TerrainRenderPass.PipelineState UNMIPPED_PIPELINE_STATE = new PipelineState() {
        @Override
        public void setup() {
            GlStateManager.disableAlphaTest();
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
            textureManager.get(TextureAtlas.BLOCKS_LOCATION).pushFilter(false, false);
        }

        @Override
        public void clear() {
            Minecraft.getInstance().getTextureManager().get(TextureAtlas.BLOCKS_LOCATION).popFilter();
            GlStateManager.enableAlphaTest();
        }
    };

    private static TerrainRenderPass.TerrainRenderPassBuilder builderForRenderType(PipelineState pipelineState, ChunkVertexType vertexType, Map<String, String> extraDefines) {
        var builder = TerrainRenderPass.builder();
        builder.pipelineState(pipelineState);
        builder.vertexType(vertexType).primitiveType(QuadPrimitiveType.TRIANGULATED).extraDefines(extraDefines);
        return builder;
    }

    public static RenderPassConfiguration<?> build(ChunkVertexType vertexType, boolean translucencySorting, int chunkFadeInDuration, boolean separateCutout) {
        TerrainSampling sampling = createTerrainSampling(!separateCutout);
        AtlasEdgeMask edgeMask = sampling instanceof TerrainSampling.EdgeMasked masked ? masked.mask() : null;
        PipelineState opaqueState = withEdgeMask(DISABLE_BLEND_PIPELINE_STATE, edgeMask);
        PipelineState decalState = withEdgeMask(DECAL_PIPELINE_STATE, edgeMask);
        PipelineState translucentState = withEdgeMask(PipelineState.DEFAULT, edgeMask);

        Map<String, String> extraDefines = chunkFadeInDuration > 0
                ? Map.of("CHUNK_FADE_IN_DURATION_MS", Integer.toString(chunkFadeInDuration))
                : Map.of();
        TerrainRenderPass solidPass = builderForRenderType(opaqueState, vertexType, extraDefines)
                .name("solid")
                .fragmentDiscard(false)
                .useReverseOrder(false)
                .build();
        TerrainRenderPass cutoutMippedPass = builderForRenderType(opaqueState, vertexType, extraDefines)
                .name("cutout_mipped")
                .fragmentDiscard(true)
                .useReverseOrder(false)
                .build();
        TerrainRenderPass cutoutPass = !separateCutout ? cutoutMippedPass : builderForRenderType(UNMIPPED_PIPELINE_STATE, vertexType, extraDefines)
                .name("cutout")
                .fragmentDiscard(true)
                .useReverseOrder(false)
                .build();
        TerrainRenderPass decalPass = builderForRenderType(decalState, vertexType, extraDefines)
                .name("decal")
                .fragmentDiscard(true)
                .useReverseOrder(false)
                .build();
        TerrainRenderPass translucentPass = builderForRenderType(translucentState, vertexType, extraDefines)
                .name("translucent")
                .fragmentDiscard(false)
                .useReverseOrder(true)
                .useTranslucencySorting(translucencySorting)
                .build();
        Material translucentMaterial = new Material(translucentPass, AlphaCutoffParameter.ZERO, true);
        Material solidMaterial = new Material(solidPass, AlphaCutoffParameter.ZERO, true);
        Material cutoutMippedMaterial = new Material(cutoutMippedPass, AlphaCutoffParameter.ONE_TENTH, true);
        Material cutoutMaterial = new Material(cutoutPass, AlphaCutoffParameter.ONE_TENTH, false);
        Material decalMaterial = new Material(decalPass, AlphaCutoffParameter.ONE_TENTH, false);

        Map<Object, Collection<TerrainRenderPass>> vanillaRenderStages = new Reference2ReferenceOpenHashMap<>();
        vanillaRenderStages.put(BlockLayer.SOLID, separateCutout
                ? List.of(solidPass, cutoutMippedPass, cutoutPass, decalPass)
                : List.of(solidPass, cutoutMippedPass, decalPass));
        vanillaRenderStages.put(BlockLayer.TRANSLUCENT, List.of(translucentPass));

        Map<Object, Material> renderTypeToMaterialMap = new Reference2ReferenceOpenHashMap<>();
        renderTypeToMaterialMap.put(BlockLayer.SOLID, solidMaterial);
        renderTypeToMaterialMap.put(BlockLayer.CUTOUT, cutoutMaterial);
        renderTypeToMaterialMap.put(BlockLayer.CUTOUT_MIPPED, cutoutMippedMaterial);
        renderTypeToMaterialMap.put(BlockLayer.TRANSLUCENT, translucentMaterial);
        renderTypeToMaterialMap.put(DECAL, decalMaterial);
        renderTypeToMaterialMap.put(BLENDED_DECAL, new Material(translucentPass, AlphaCutoffParameter.ZERO, false));
        renderTypeToMaterialMap.put(UNMIPPED_SOLID, new Material(solidPass, AlphaCutoffParameter.ZERO, false));

        return new RenderPassConfiguration<>(renderTypeToMaterialMap, vanillaRenderStages, solidMaterial, cutoutMippedMaterial, translucentMaterial, sampling);
    }
}
