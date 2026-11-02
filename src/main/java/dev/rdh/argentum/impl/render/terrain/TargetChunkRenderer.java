package dev.rdh.argentum.impl.render.terrain;

import org.embeddedt.embeddium.impl.gl.array.GlVertexArray;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.gl.tessellation.GlVertexArrayTessellation;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderTextureSlot;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import dev.rdh.argentum.api.render.terrain.TerrainDrawTarget;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.terrain.fog.ArgentumFogService;

import net.minecraft.client.render.platform.GlStateManager;

/**
 * Draws terrain with whatever program is already bound, feeding it through the fixed function attributes and matrices.
 */
final class TargetChunkRenderer extends DefaultChunkRenderer {
    private final TerrainDrawTarget target;

    TargetChunkRenderer(RenderDevice device, RenderPassConfiguration<?> configuration, TerrainDrawTarget target) {
        super(device, configuration, ArgentumFogService.INSTANCE);
        this.target = target;
        // never bound or deleted, begin and end below keep it away from GL
        this.activeProgram = new GlProgram<>(0, context -> new BoundProgramInterface()) {};
    }

    @Override
    protected boolean useBlockFaceCulling() {
        // faces turned away from the camera can still face the light
        return Argentum.CONFIG.blockFaceCulling && !this.target.isShadowPass();
    }

    @Override
    protected void configureShaderInterface(ChunkShaderInterface shader) {
    }

    @Override
    protected void begin(TerrainRenderPass pass) {
        pass.startDrawing();
        if (pass.supportsFragmentDiscard()) {
            GlStateManager.enableAlphaTest();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        }
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
    }

    @Override
    protected void end(TerrainRenderPass pass) {
        GlStateManager.popMatrix();
        // drawing with a color array leaves the current color undefined
        GlStateManager.COLOR.r = -1.0F;
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        pass.endDrawing();
    }

    @Override
    protected GlTessellation createRegionTessellation(CommandList commandList, RenderRegion.DeviceResources resources) {
        var tessellation = new GlVertexArrayTessellation(new GlVertexArray(), this.makeTessellationBindingArray(commandList, resources)) {
            @Override
            protected void bindAttributes(CommandList commandList) {
                for (var binding : this.bindings) {
                    commandList.bindBuffer(binding.target(), binding.buffer());
                }
                TargetChunkRenderer.this.target.bindVertexAttributes();
            }
        };
        tessellation.init(commandList);
        return tessellation;
    }

    private static final class BoundProgramInterface implements ChunkShaderInterface {
        @Override
        public void setupState(TerrainRenderPass pass) {
        }

        @Override
        public GlPrimitiveType getPrimitiveType() {
            return GlPrimitiveType.TRIANGLES;
        }

        @Override
        public void setProjectionMatrix(Matrix4fc matrix) {
        }

        @Override
        public void setModelViewMatrix(Matrix4fc matrix) {
        }

        @Override
        public void setRegionOffset(float x, float y, float z) {
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            GlStateManager.translatef(x, y, z);
        }

        @Override
        public void setTextureSlot(ChunkShaderTextureSlot slot, int val) {
        }
    }
}
