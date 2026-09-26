package dev.rdh.argentum.impl.render.terrain;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass.PipelineState;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;

import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GlStateManager;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RenderPassConfigurationBuilder {
    public static final Object DECAL = new Object();
    public static final Object UNMIPPED_SOLID = new Object();

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

    private static TerrainRenderPass.TerrainRenderPassBuilder builderForRenderType(boolean disableBlend, ChunkVertexType vertexType, Map<String, String> extraDefines) {
        var builder = TerrainRenderPass.builder();
        builder.pipelineState(disableBlend ? DISABLE_BLEND_PIPELINE_STATE : TerrainRenderPass.PipelineState.DEFAULT);
        builder.vertexType(vertexType).primitiveType(QuadPrimitiveType.TRIANGULATED).extraDefines(extraDefines);
        return builder;
    }

    public static RenderPassConfiguration<?> build(ChunkVertexType vertexType, boolean translucencySorting, int chunkFadeInDuration) {
        Map<String, String> extraDefines = chunkFadeInDuration > 0
                ? Map.of("CHUNK_FADE_IN_DURATION_MS", Integer.toString(chunkFadeInDuration))
                : Map.of();
        TerrainRenderPass solidPass = builderForRenderType(true, vertexType, extraDefines)
                .name("solid")
                .fragmentDiscard(false)
                .useReverseOrder(false)
                .build();
        TerrainRenderPass cutoutMippedPass = builderForRenderType(true, vertexType, extraDefines)
                .name("cutout_mipped")
                .fragmentDiscard(true)
                .useReverseOrder(false)
                .build();
        TerrainRenderPass decalPass = builderForRenderType(true, vertexType, extraDefines)
                .pipelineState(DECAL_PIPELINE_STATE)
                .name("decal")
                .fragmentDiscard(true)
                .useReverseOrder(false)
                .build();
        TerrainRenderPass translucentPass = builderForRenderType(false, vertexType, extraDefines)
                .name("translucent")
                .fragmentDiscard(false)
                .useReverseOrder(true)
                .useTranslucencySorting(translucencySorting)
                .build();
        Material translucentMaterial = new Material(translucentPass, AlphaCutoffParameter.ZERO, true);
        Material solidMaterial = new Material(solidPass, AlphaCutoffParameter.ZERO, true);
        Material cutoutMippedMaterial = new Material(cutoutMippedPass, AlphaCutoffParameter.ONE_TENTH, true);
        Material cutoutMaterial = new Material(cutoutMippedPass, AlphaCutoffParameter.ONE_TENTH, false);
        Material decalMaterial = new Material(decalPass, AlphaCutoffParameter.ONE_TENTH, false);

        Map<Object, Collection<TerrainRenderPass>> vanillaRenderStages = new Reference2ReferenceOpenHashMap<>();
        vanillaRenderStages.put(BlockLayer.SOLID, List.of(solidPass, cutoutMippedPass, decalPass));
        vanillaRenderStages.put(BlockLayer.TRANSLUCENT, List.of(translucentPass));

        Map<Object, Material> renderTypeToMaterialMap = new Reference2ReferenceOpenHashMap<>();
        renderTypeToMaterialMap.put(BlockLayer.SOLID, solidMaterial);
        renderTypeToMaterialMap.put(BlockLayer.CUTOUT, cutoutMaterial);
        renderTypeToMaterialMap.put(BlockLayer.CUTOUT_MIPPED, cutoutMippedMaterial);
        renderTypeToMaterialMap.put(BlockLayer.TRANSLUCENT, translucentMaterial);
        renderTypeToMaterialMap.put(DECAL, decalMaterial);
        renderTypeToMaterialMap.put(UNMIPPED_SOLID, new Material(solidPass, AlphaCutoffParameter.ZERO, false));

        return new RenderPassConfiguration<>(renderTypeToMaterialMap, vanillaRenderStages, solidMaterial, cutoutMippedMaterial, translucentMaterial);
    }
}
