package dev.rdh.argentum.impl.render.terrain;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderFogComponent;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import org.joml.Matrix4f;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.entity.EntityOcclusionCuller;
import dev.rdh.argentum.impl.render.entity.EntityGatherer;
import dev.rdh.argentum.impl.render.entity.EntityShadowBatch;
import dev.rdh.argentum.impl.render.entity.instancing.EntityInstancing;
import dev.rdh.argentum.impl.render.entity.instancing.ModelInstancer;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;
import java.util.Objects;

/**
 * Provides an extension to vanilla's world renderer.
 */
public class ArgentumWorldRenderer extends SimpleWorldRenderer<World, ArgentumRenderSectionManager, BlockLayer, BlockEntity, Float> {
    private final EntityGatherer entityGatherer = new EntityGatherer();
    private final EntityOcclusionCuller entityOcclusionCuller = new EntityOcclusionCuller(this);
    private final EntityShadowBatch entityShadowBatch = new EntityShadowBatch();
    private final ModelInstancer modelInstancer = new ModelInstancer();
    private final EntityInstancing entityInstancing = new EntityInstancing(this.modelInstancer);

    private ChunkRenderMatrices matrices = null;

    /**
     * @return The ArgentumWorldRenderer based on the current dimension
     */
    public static ArgentumWorldRenderer instance() {
        var instance = instanceNullable();

        if (instance == null) {
            throw new IllegalStateException("No renderer attached to active world");
        }

        return instance;
    }

    /**
     * @return The ArgentumWorldRenderer based on the current dimension, or null if none is attached
     */
    public static ArgentumWorldRenderer instanceNullable() {
        var world = Minecraft.getInstance().worldRenderer;

        return world == null ? null : world.argentum$getWorldRenderer();
    }

    @Override
    protected void unloadWorld() {
        this.entityOcclusionCuller.clear();
        this.entityInstancing.discardBatch();
        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.entityShadowBatch.close(commandList);
            this.modelInstancer.close(commandList);
        }
        super.unloadWorld();
    }

    @Override
    public void reload() {
        boolean reloadModels = this.modelInstancer.isInitialized();
        if (reloadModels || this.entityShadowBatch.isInitialized()) {
            try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
                if (reloadModels) {
                    this.entityInstancing.discardBatch();
                    this.modelInstancer.reload(commandList);
                }
                this.entityShadowBatch.close(commandList);
            }
        }
        super.reload();
    }

    public EntityInstancing getEntityInstancing() {
        return this.entityInstancing;
    }

    public EntityShadowBatch getEntityShadowBatch() {
        return this.entityShadowBatch;
    }

    public ModelInstancer getModelInstancer() {
        return this.modelInstancer;
    }

    public void beginEntityRendering() {
        this.entityShadowBatch.beginFrame();
        this.entityInstancing.beginBatch();
    }

    public boolean isRenderingWorld(World world) {
        return this.world == world;
    }

    public static CameraState captureCameraState(double ticks) {
        Entity viewEntity = Minecraft.getInstance().getCamera();

        Objects.requireNonNull(viewEntity, "Client must have view entity");

        double x = viewEntity.lastX + (viewEntity.x - viewEntity.lastX) * ticks;
        double y = viewEntity.lastY + (viewEntity.y - viewEntity.lastY) * ticks + (double) viewEntity.getEyeHeight();
        double z = viewEntity.lastZ + (viewEntity.z - viewEntity.lastZ) * ticks;

        float pitch = viewEntity.pitch;
        float yaw = viewEntity.yaw;
        float fogDistance = ChunkShaderFogComponent.FOG_SERVICE.getFogCutoff();

        return new CameraState(x, y, z, pitch, yaw, fogDistance);
    }

    @Override
    public int getEffectiveRenderDistance() {
        return Minecraft.getInstance().options.viewDistance;
    }

    @Override
    public int getMinimumBuildHeight() {
        return 0;
    }

    @Override
    public int getMaximumBuildHeight() {
        return this.world.getHeight();
    }

    @Override
    public ChunkRenderMatrices createChunkRenderMatrices() {
        return Objects.requireNonNull(this.matrices, "Render matrices have not been captured");
    }

    @Override
    protected ArgentumRenderSectionManager createRenderSectionManager(CommandList commandList) {
        ChunkTrackerHolder.get(this.world).setRequiredNeighborRadius(Argentum.CONFIG.safeChunkEdges ? 1 : 0);
        ChunkVertexType vertexType = Argentum.CONFIG.compactVertexFormat ? ChunkMeshFormats.COMPACT : ChunkMeshFormats.VANILLA_LIKE;
        return ArgentumRenderSectionManager.create(vertexType, this.world, this.renderDistance, commandList);
    }

    public void captureMatrices(float[] projection, float[] modelView) {
        matrices = new ChunkRenderMatrices(new Matrix4f().set(projection), new Matrix4f().set(modelView));
    }

    public boolean isEntityVisible(Entity entity) {
        if (!Argentum.CONFIG.entityCulling) {
            return true;
        }

        var box = entity.getShape();
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)) {
            return true;
        }

        return this.isEntitySectionVisible(box) && this.entityOcclusionCuller.isVisible(entity);
    }

    public static boolean hasNameTag(Entity entity) {
        return entity.shouldShowNameTag() || entity.hasCustomName() || entity instanceof PlayerEntity;
    }

    public boolean isEntitySectionVisible(net.minecraft.util.math.Box box) {
        return this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public int renderEntities(Entity camera, Culler culler, float tickDelta, double cameraX, double cameraY, double cameraZ) {
        Minecraft minecraft = Minecraft.getInstance();
        var dispatcher = minecraft.getEntityRenderDispatcher();
        boolean batching = this.entityInstancing.isBatchActive();
        List<Entity> entities = this.entityGatherer.getLoadedEntityList((ClientWorld)this.world,
                MathHelper.floor(cameraX) >> 4, MathHelper.floor(cameraZ) >> 4, this.getEffectiveRenderDistance() + 1);
        this.entityOcclusionCuller.prepare(entities, camera, cameraX, cameraY, cameraZ);

        int rendered = 0;
        boolean isSelfSleeping = minecraft.getCamera() instanceof LivingEntity living && living.isSleeping();
        BlockPos.Mutable entityBlockPos = new BlockPos.Mutable();
        try {
            for (Entity entity : entities) {
                boolean inFrustum = dispatcher.shouldRender(entity, culler, cameraX, cameraY, cameraZ);
                boolean visible = inFrustum && this.isEntityVisible(entity);

                if (!visible && entity.rider != minecraft.player) {
                    if (entity instanceof WitherSkullEntity || (inFrustum && hasNameTag(entity))) {
                        dispatcher.renderNameTag(entity, tickDelta);
                    }
                    continue;
                }

                if (entity == minecraft.getCamera() && minecraft.options.perspective == 0 && !isSelfSleeping) {
                    continue;
                }

                if (entity.y >= 0.0 && entity.y < 256.0) {
                    entityBlockPos.set(MathHelper.floor(entity.x), MathHelper.floor(entity.y), MathHelper.floor(entity.z));
                    if (!this.world.isChunkLoaded(entityBlockPos)) {
                        continue;
                    }
                }

                rendered++;
                dispatcher.render(entity, tickDelta);
            }
        } catch (RuntimeException | Error exception) {
            this.entityInstancing.discardBatch();
            throw exception;
        }
        RenderDevice.enterManagedCode();
        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.entityShadowBatch.flush(commandList);
            if (batching) {
                this.entityInstancing.flush(commandList);
            }
        } finally {
            RenderDevice.exitManagedCode();
        }
        this.entityInstancing.renderNameTags();
        return rendered;
    }

    public boolean isParticleVisible(Particle particle) {
        if (!Argentum.CONFIG.particleCulling || this.getLastViewport() == null) {
            return true;
        }

        var box = particle.getShape();
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)) {
            return true;
        }

        return this.getLastViewport().isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
                && this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @Override
    public int renderBlockEntities(Float partialTicks) {
        boolean batching = this.entityInstancing.resumeBatch();
        int count;
        try {
            count = super.renderBlockEntities(partialTicks);
        } catch (RuntimeException | Error exception) {
            this.entityInstancing.discardBatch();
            throw exception;
        }
        if (batching) {
            RenderDevice.enterManagedCode();
            try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
                this.entityInstancing.flush(commandList);
            } finally {
                RenderDevice.exitManagedCode();
            }
        }
        return count;
    }

    @Override
    protected void renderBlockEntityList(List<BlockEntity> list, Float partialTicksBoxed) {
        float partialTicks = partialTicksBoxed;
        for (var blockEntity : list) {
            try {
                BlockEntityRenderDispatcher.INSTANCE.render(blockEntity, partialTicks, -1);
            } catch(RuntimeException e) {
                if(blockEntity.isRemoved()) {
                    Argentum.LOGGER.warn("Suppressing crash from invalid tile entity");
                } else {
                    throw e;
                }
            }
        }
    }
}
