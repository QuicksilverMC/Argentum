package dev.rdh.argentum.impl.render.terrain.compile;

import org.embeddedt.embeddium.impl.model.light.DiffuseProvider;
import org.embeddedt.embeddium.impl.model.light.LightPipelineProvider;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.util.QuadUtil;
import org.lwjgl.opengl.GL11C;

import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;

import java.nio.IntBuffer;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.terrain.compile.light.LightDataCache;
import dev.rdh.argentum.impl.render.terrain.compile.light.SmoothFluidLighter;
import dev.rdh.argentum.impl.render.terrain.compile.pipeline.FastBlockRenderer;
import dev.rdh.argentum.impl.world.biome.BiomeColorCache;
import dev.rdh.argentum.impl.world.cloned.ChunkRenderContext;

public class ArgentumChunkBuildContext extends ChunkBuildContext {
    private static final BlockLayer[] LAYERS = BlockLayer.values();

    private final BufferBuilder[] layerBuffers = new BufferBuilder[LAYERS.length];
    private final boolean[] usedLayerBuffers = new boolean[LAYERS.length];
    private final TextureAtlas textureAtlas;
    private final RenderPassConfiguration<?> renderPassConfiguration;
    private final LightDataCache lightCache = new LightDataCache();
    private final short[] renderLightCache = new short[20 * 20 * 20];
    private final BiomeColorCache biomeColorCache = new BiomeColorCache(Argentum.CONFIG.biomeBlendRadius);
    private final boolean renderPassOptimization = Argentum.CONFIG.renderPassOptimization;
    private final LightPipelineProvider lighters = new LightPipelineProvider(this.lightCache, DiffuseProvider.NONE, true);
    private final FastBlockRenderer blockRenderer = new FastBlockRenderer(this, this.lighters);
    private final SmoothFluidLighter fluidLighter = new SmoothFluidLighter(this.lighters);
    private int originX;
    private int originY;
    private int originZ;

    public ArgentumChunkBuildContext(RenderPassConfiguration renderPassConfiguration) {
        super(renderPassConfiguration);
        this.renderPassConfiguration = renderPassConfiguration;
        this.textureAtlas = Minecraft.getInstance().getBlocksAtlas();
    }

    public void beginSection(ChunkRenderContext world, int x, int y, int z) {
        this.originX = x;
        this.originY = y;
        this.originZ = z;
        this.biomeColorCache.update(world);
        world.resetCaches(this.renderLightCache, this.biomeColorCache);
        this.lightCache.reset(world, x, y, z);
        this.lighters.reset();
        this.blockRenderer.beginSection();
        this.fluidLighter.beginSection();
    }

    public FastBlockRenderer getBlockRenderer() {
        return this.blockRenderer;
    }

    public void renderFluid(BlockState state, BlockPos pos, ChunkRenderContext world, BlockLayer layer) {
        BufferBuilder buffer = this.getBuffer(layer);
        int firstVertex = buffer.getVertexCount();
        Minecraft.getInstance().getBlockRenderDispatcher().render(state, pos, world, buffer);
        this.fluidLighter.relight(buffer, firstVertex, state, pos, this.originX, this.originY, this.originZ);
    }

    public BufferBuilder getBuffer(BlockLayer layer) {
        int index = layer.ordinal();
        BufferBuilder buffer = this.layerBuffers[index];
        if (buffer == null) {
            buffer = new BufferBuilder(getInitialBufferSize(layer) * 1024 / Integer.BYTES);
            this.layerBuffers[index] = buffer;
        }
        if (!this.usedLayerBuffers[index]) {
            buffer.begin(GL11C.GL_QUADS, DefaultVertexFormat.BLOCK);
            buffer.offset(-this.originX, -this.originY, -this.originZ);
            this.usedLayerBuffers[index] = true;
        }
        return buffer;
    }

    public void finishSection(ChunkBuildBuffers buffers) {
        for (int i = 0; i < this.layerBuffers.length; i++) {
            if (!this.usedLayerBuffers[i]) {
                continue;
            }

            BufferBuilder buffer = this.layerBuffers[i];
            buffer.end();
            this.usedLayerBuffers[i] = false;
            try {
                this.copyRawBuffer(buffer.getBuffer().asIntBuffer(), buffer.getVertexCount(), buffers,
                        buffers.getRenderPassConfiguration().getMaterialForRenderType(LAYERS[i])
                );
            } finally {
                buffer.clear();
                buffer.offset(0, 0, 0);
            }
        }
    }

    // what size should the buffer for this layer be? in kib
    private static int getInitialBufferSize(BlockLayer layer) {
		return switch(layer) {
		    case SOLID, CUTOUT_MIPPED -> 512;
		    case TRANSLUCENT -> 128;
		    default -> 32;
	    };
    }

    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();

    public void copyRawBuffer(IntBuffer rawBuffer, int vertexCount, ChunkBuildBuffers buffers, Material material) {
        if (vertexCount == 0) {
            return;
        }

        // Require
        if ((vertexCount & 0x3) != 0) {
            throw new IllegalStateException();
        }

		outputQuads(rawBuffer, this.vertices, vertexCount / 4, buffers, material);
    }

    private void outputQuads(IntBuffer rawBuffer, ChunkVertexEncoder.Vertex[] vertices, int numQuads, ChunkBuildBuffers buffers, Material material) {
        int ptr = 0;
        for (int quadIdx = 0; quadIdx < numQuads; quadIdx++) {
            float uSum = 0, vSum = 0;
            for (int vIdx = 0; vIdx < 4; vIdx++) {
                var vertex = vertices[vIdx];
                vertex.x = Float.intBitsToFloat(rawBuffer.get(ptr++));
                vertex.y = Float.intBitsToFloat(rawBuffer.get(ptr++));
                vertex.z = Float.intBitsToFloat(rawBuffer.get(ptr++));

                vertex.color = rawBuffer.get(ptr++);

                float u = Float.intBitsToFloat(rawBuffer.get(ptr++));
                float v = Float.intBitsToFloat(rawBuffer.get(ptr++));
                vertex.u = u;
                uSum += u;
                vertex.v = v;
                vSum += v;
                vertex.light = rawBuffer.get(ptr++);
            }
            int trueNormal = QuadUtil.calculateNormal(vertices);
            for (int vIdx = 0; vIdx < 4; vIdx++) {
                vertices[vIdx].trueNormal = trueNormal;
            }
            ModelQuadFacing facing = QuadUtil.findNormalFace(trueNormal);
            TextureAtlasSprite sprite = this.textureAtlas.argentum$findFromUV(uSum * 0.25F, vSum * 0.25F);
            if (sprite != null && sprite.isAnimated()
                    && buffers.getSectionContextBundle() instanceof PrimitiveBuiltRenderSectionData renderData) {
                renderData.animatedSprites.add(sprite);
            }
            Material selectedMaterial = this.selectMaterial(material, sprite);
            buffers.get(selectedMaterial).getVertexBuffer(facing).push(vertices, selectedMaterial);
        }
    }

    public Material selectMaterial(Material material, TextureAtlasSprite sprite) {
        if (!this.renderPassOptimization || sprite == null || sprite.getClass() != TextureAtlasSprite.class || sprite.isAnimated()) {
            return material;
        }

        SpriteTransparencyLevel transparency = sprite.embeddium$getTransparencyLevel();
        if (material == this.renderPassConfiguration.defaultTranslucentMaterial()
                && transparency != SpriteTransparencyLevel.TRANSLUCENT) {
            return this.renderPassConfiguration.defaultCutoutMippedMaterial();
        }
        if (transparency == SpriteTransparencyLevel.OPAQUE
                && material != this.renderPassConfiguration.defaultSolidMaterial()) {
            return this.renderPassConfiguration.defaultSolidMaterial();
        }
        return material;
    }

    @Override
    public void cleanup() {
        for (int i = 0; i < this.layerBuffers.length; i++) {
            if (this.usedLayerBuffers[i]) {
                BufferBuilder buffer = this.layerBuffers[i];
                try {
                    buffer.end();
                } finally {
                    buffer.clear();
                    buffer.offset(0, 0, 0);
                    this.usedLayerBuffers[i] = false;
                }
            }
        }
        super.cleanup();
    }
}
