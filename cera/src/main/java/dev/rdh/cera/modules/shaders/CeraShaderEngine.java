package dev.rdh.cera.modules.shaders;

import dev.rdh.argentum.api.render.instancing.InstanceDrawTarget;
import dev.rdh.argentum.api.render.terrain.TerrainDrawTarget;
import dev.rdh.argentum.impl.ext.MinecraftExtension;
import dev.rdh.argentum.impl.render.entity.instancing.InstanceKind;
import dev.rdh.argentum.impl.render.entity.instancing.InstanceRenderPass;
import dev.rdh.cera.Cera;

import com.ventooth.swansong.EnvInfo;
import com.ventooth.swansong.gl.GLEnvProbe;
import com.ventooth.swansong.shader.ShaderEngine;
import com.ventooth.swansong.shader.ShaderEntityData;
import com.ventooth.swansong.shader.StateGraph;
import com.ventooth.swansong.shader.StateGraph.Stack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.lwjgl.opengl.GL11;

import static com.ventooth.swansong.shader.StateGraph.Node.*;

public final class CeraShaderEngine extends ShaderEngine<CeraShaderState> implements InstanceDrawTarget, TerrainDrawTarget {
    private static final StateGraph.Order STAGE_ORDER = StateGraph.Order.frame()
            .edge(ShadowBegin, ShadowChunk0)
            .edge(ShadowChunk0, ShadowEntities0, ShadowChunk1)
            .edge(ShadowEntities0, ShadowEntities0, ShadowBlockEntities0)
            .edge(ShadowBlockEntities0, ShadowChunk1)
            .edge(ShadowChunk1, ShadowLast)
            .edge(RenderBegin, RenderSkyBasic, RenderClouds, RenderChunk0)
            .edge(RenderSkyBasic, RenderSkyBasic, RenderSkyTextured, RenderClouds, RenderChunk0)
            .edge(RenderSkyTextured, RenderSkyBasic, RenderSkyTextured, RenderClouds, RenderChunk0)
            .edge(RenderClouds, RenderChunk0, RenderLast)
            .edge(RenderChunk0, RenderWeatherEntities0, RenderSelectionBox, RenderBlockDamage)
            .edge(RenderWeatherEntities0, RenderEntities0)
            .edge(RenderEntities0, RenderBlockEntities0)
            .edge(RenderBlockEntities0, RenderSelectionBox)
            .edge(RenderSelectionBox, RenderBlockDamage)
            .edge(RenderBlockDamage, RenderParticlesLit, RenderWeather)
            .edge(RenderParticlesLit, RenderParticles)
            .edge(RenderParticles, RenderWeather)
            .edge(RenderWeather, RenderHand0)
            .edge(RenderHand0, RenderChunk1)
            .edge(RenderChunk1, RenderClouds, RenderLast);

    private DynamicTexture whiteTexture;

    public CeraShaderEngine() {
        super(new CeraShaderState(), new CeraHostRenderer(), new CeraShadowPassRenderer(), STAGE_ORDER);
    }

    public void start() {
        var version = FabricLoader.getInstance().getModContainer("cera").orElseThrow().getMetadata().getVersion();
        EnvInfo.init(GLEnvProbe.probe("1.8.9", version.getFriendlyString()));
        shaderPacks().init(Cera.CONFIG.shaderPack);
        firstInit();
        var minecraft = (MinecraftExtension)Minecraft.getInstance();
        minecraft.argentum$setInstanceDrawTarget(this);
        minecraft.argentum$setTerrainDrawTarget(this);
    }

    @Override
    public ChunkVertexType vertexType() {
        return isInitializedImpl() ? ShaderChunkVertex.INSTANCE : null;
    }

    @Override
    public void bindVertexAttributes() {
        ShaderChunkVertex.bindAttributes();
    }

    @Override
    public boolean hasShadowPass() {
        return shadowPassExists();
    }

    @Override
    public boolean isShadowPass() {
        return graph().isShadowPass();
    }

    public boolean pushBlock(BlockState state) {
        if (!isInitializedImpl()) {
            return false;
        }
        var block = state.getBlock();
        int meta = block.getMetadataFromState(state);
        ShaderEntityData.get().pushEntityBlock(remapBlockID(Block.getId(block), meta), block.getRenderType(), meta);
        return true;
    }

    public void bindWhiteTexture() {
        if (whiteTexture == null) {
            whiteTexture = new DynamicTexture(1, 1);
            whiteTexture.getPixels()[0] = -1;
            whiteTexture.upload();
        }
        GlStateManager.bindTexture(whiteTexture.getGlId());
    }

    public void nextEntity(Entity entity) {
        if (isInitializedImpl()) {
            state().nextEntity(Entities.getId(entity));
        }
    }

    public void nextBlockEntity(BlockEntity blockEntity) {
        if (isInitializedImpl()) {
            state().nextBlockEntity(remapBlockID(Block.getId(blockEntity.getBlock()), blockEntity.getBlockMetadata()));
        }
    }

    @Override
    public boolean active() {
        return isInitializedImpl() && graph().isManaged();
    }

    @Override
    public void beginPass(InstanceRenderPass pass) {
        switch (pass) {
            case EMISSIVE -> {
                graph().push(Stack.SpiderEyes);
                GlStateManager.alphaFunc(GL11.GL_GREATER, 0F);
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager.depthMask(true);
            }
            case GLINT, ITEM_GLINT -> graph().push(Stack.ArmorGlint);
            default -> {
            }
        }
    }

    @Override
    public void beginDraw(InstanceKind kind, int id) {
        if (kind == InstanceKind.BLOCK_ENTITY) {
            state().nextBlockEntity(remapBlockID(id >> 4, id & 15));
        } else {
            state().nextEntity(id);
        }
    }

    @Override
    public void endPass(InstanceRenderPass pass) {
        switch (pass) {
            case EMISSIVE -> {
                GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
                graph().pop(Stack.SpiderEyes);
            }
            case GLINT, ITEM_GLINT -> graph().pop(Stack.ArmorGlint);
            default -> {
            }
        }
    }
}
