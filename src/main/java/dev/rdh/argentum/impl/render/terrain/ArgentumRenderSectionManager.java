package dev.rdh.argentum.impl.render.terrain;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.DirectMultiDrawEmitter;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;
import org.embeddedt.embeddium.impl.render.chunk.lists.SectionTicker;
import org.embeddedt.embeddium.impl.render.chunk.sprite.GenericSectionSpriteTicker;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderTextureSlot;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.util.position.SectionPos;
import org.jetbrains.annotations.Nullable;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.terrain.compile.ArgentumChunkBuildContext;
import dev.rdh.argentum.impl.render.terrain.compile.task.ChunkBuilderMeshingTask;
import dev.rdh.argentum.impl.world.cloned.ChunkRenderContext;
import dev.rdh.argentum.impl.world.cloned.ClonedChunkSectionCache;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.world.World;

public class ArgentumRenderSectionManager extends RenderSectionManager {
    private final World world;
    private final ClonedChunkSectionCache sectionCache;

    public ArgentumRenderSectionManager(RenderPassConfiguration<?> configuration, World world, int renderDistance, CommandList commandList, int minSection, int maxSection, int requestedThreads) {
        super(configuration, () -> new ArgentumChunkBuildContext(configuration), ChunkRenderer::new, renderDistance, commandList, minSection, maxSection, requestedThreads, false);
        this.world = world;
        this.sectionCache = new ClonedChunkSectionCache(world);
    }

    public static ArgentumRenderSectionManager create(ChunkVertexType vertexType, World world, int renderDistance, CommandList commandList) {
        int maxSection = world.getHeight() / 16;
        return new ArgentumRenderSectionManager(RenderPassConfigurationBuilder.build(vertexType,
                Argentum.CONFIG.translucencySorting, Argentum.CONFIG.chunkFadeInDuration), world, renderDistance, commandList,
                0, maxSection,
                Argentum.CONFIG.chunkBuilderThreads
        );
    }

    @Override
    protected AsyncOcclusionMode getAsyncOcclusionMode() {
        return Argentum.CONFIG.asyncOcclusion;
    }

    @Override
    protected @Nullable SectionTicker createSectionTicker() {
        return new GenericSectionSpriteTicker<>(TextureAtlasSprite::argentum$markActive);
    }

	@Override
    protected boolean useFogOcclusion() {
        return Argentum.CONFIG.fogCulling && GlStateManager.FOG.end >= 16.0F;
    }

    @Override
    protected boolean shouldUseOcclusionCulling(Viewport positionedViewport, boolean spectator) {
        var camBlockPos = positionedViewport.getBlockCoord();

        var block = this.world.getBlockState(new BlockPos(camBlockPos.x(), camBlockPos.y(), camBlockPos.z())).getBlock();

		return !spectator || !block.isSolidRender();
    }

    @Override
    protected boolean isSectionVisuallyEmpty(int x, int y, int z) {
        return ChunkRenderContext.isSectionEmpty(this.world, x, y, z);
    }

    @Override
    protected @Nullable ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame) {
        ChunkRenderContext context = ChunkRenderContext.prepare(this.world,
                new SectionPos(render.getChunkX(), render.getChunkY(), render.getChunkZ()), this.sectionCache
        );
        if (context == null) {
            return null;
        }

        return new ChunkBuilderMeshingTask(render, context, frame, this.cameraPosition);
    }

    @Override
    protected void invalidateCachedSectionData(RenderSection section) {
        this.sectionCache.invalidate(section.getChunkX(), section.getChunkY(), section.getChunkZ());
    }

    @Override
    protected boolean allowImportantRebuilds() {
        return !Argentum.CONFIG.deferChunkUpdates;
    }

    private static class ChunkRenderer extends DefaultChunkRenderer {

        public ChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration) {
            super(device, renderPassConfiguration, new DirectMultiDrawEmitter());
        }

        @Override
        protected boolean useBlockFaceCulling() {
            return Argentum.CONFIG.blockFaceCulling;
        }

        @Override
        protected void configureShaderInterface(ChunkShaderInterface shader) {
            shader.setTextureSlot(ChunkShaderTextureSlot.BLOCK, 0);
            shader.setTextureSlot(ChunkShaderTextureSlot.LIGHT, 1);
        }
    }
}
