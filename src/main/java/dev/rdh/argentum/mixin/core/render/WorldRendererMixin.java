package dev.rdh.argentum.mixin.core.render;

import dev.rdh.argentum.impl.ext.WorldExtension;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.objectweb.asm.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.ext.WorldRendererExtension;
import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;
import dev.rdh.argentum.impl.render.terrain.NoopRenderChunkStorage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.world.RenderChunkFactory;
import net.minecraft.client.render.world.RenderChunkStorage;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Mixin(value = WorldRenderer.class, priority = 900)
public abstract class WorldRendererMixin implements WorldRendererExtension {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private int lastViewDistance;

    @Shadow
    public abstract void reload();

    @Shadow
    private ClientWorld world;
    @Shadow
    private int renderedEntityCount;

    @Shadow
    private boolean viewChanged;

    @Shadow
    private VertexBuffer darkSkyBuffer;

    @Unique
    private ArgentumWorldRenderer renderer;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        this.renderer = new ArgentumWorldRenderer();
    }

    @Override
    public ArgentumWorldRenderer argentum$getWorldRenderer() {
        return this.renderer;
    }

    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void celeritas$beginEntityRendering(Entity camera, Culler culler, float tickDelta, CallbackInfo ci) {
        this.renderer.beginEntityRendering();
    }

    @Inject(method = "setWorld", at = @At("RETURN"))
    private void onWorldChanged(@Coerce World world, CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.setWorld(world);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }


    /**
     * @reason Redirect the chunk layer render passes to our renderer
     * @author JellySquid
     */
    @Overwrite
    public int render(BlockLayer layer, double ticks, int anaglyphRenderPass, Entity viewEntity) {
        RenderDevice.enterManagedCode();

        Lighting.turnOff();

        double d3 = viewEntity.prevX + (viewEntity.x - viewEntity.prevX) * ticks;
        // Do not apply eye height here or weird offsets will happen
        double d4 = viewEntity.prevY + (viewEntity.y - viewEntity.prevY) * ticks;
        double d5 = viewEntity.prevZ + (viewEntity.z - viewEntity.prevZ) * ticks;

        this.minecraft.gameRenderer.enableLightMap();

        try {
            this.renderer.drawChunkLayer(layer, d3, d4, d5);
        } finally {
            RenderDevice.exitManagedCode();
        }

        this.minecraft.gameRenderer.disableLightMap();

        return 1;
    }

    @Unique
    private int frame = 0;

    /**
     * @reason Redirect the terrain setup phase to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void setupRender(Entity camera, double tickDelta, Culler culler, int frame, boolean loadChunks) {
        if (this.minecraft.options.viewDistance != this.lastViewDistance) {
            this.reload();
        }

        if (this.viewChanged) {
            this.renderer.getRenderSectionManager().markGraphDirty();
            this.viewChanged = false;
        }

        updateFrustums(culler, (float)tickDelta);
    }

    public void updateFrustums(Culler camera, float tick) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.setupTerrain(((ViewportProvider)camera).sodium$createViewport(),
                    ArgentumWorldRenderer.captureCameraState(tick),
                    this.frame++, this.minecraft.player.noClip, false
            );
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void markDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, false);
        ((WorldExtension)this.world).argentum$getBlockEntityLight()
                .invalidate(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Inject(method = "reload()V", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        if (!this.renderer.isRenderingWorld(this.world)) {
            return;
        }

        RenderDevice.enterManagedCode();

        try {
            this.renderer.reload();
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Redirect(
            method = "reload()V",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/World;ILnet/minecraft/client/render/world/WorldRenderer;Lnet/minecraft/client/render/world/RenderChunkFactory;)Lnet/minecraft/client/render/world/RenderChunkStorage;")
    )
    private RenderChunkStorage celeritas$skipVanillaChunkStorage(World world, int viewDistance, WorldRenderer renderer, RenderChunkFactory factory) {
        return new NoopRenderChunkStorage(world, viewDistance, renderer, factory);
    }

    // vanilla forgot to not use display lists for this one call, which breaks it with vbos on
    @Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;callList(I)V", ordinal = 3))
    private void celeritas$renderLowerSky(int list) {
        this.darkSkyBuffer.bind();
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
        this.darkSkyBuffer.draw(GL11.GL_QUADS);
        this.darkSkyBuffer.unbind();
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    /**
     * @author embeddedt
     * @reason Disable vanilla chunk compilation
     */
    @Overwrite
    public void compileChunksUntil(long time) {

    }

    @Inject(method = "renderEntities", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/world/WorldRenderer;globalBlockEntities:Ljava/util/Set;", ordinal = 0, opcode = Opcodes.GETFIELD))
    public void sodium$renderTileEntities(CallbackInfo ci, @Local(ordinal = 0, argsOnly = true) float partialTicks) {
        this.renderer.renderBlockEntities(partialTicks);
    }

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=entities"))
    private void celeritas$renderEntities(Entity camera, Culler culler, float tickDelta, CallbackInfo ci, @Local(ordinal = 0) double d, @Local(ordinal = 1) double e, @Local(ordinal = 2) double g) {
        this.renderedEntityCount += this.renderer.renderEntities(camera, culler, tickDelta, d, e, g);
    }

    @Redirect(method = "renderEntities", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 0))
    private Iterator<?> celeritas$skipVanillaEntityChunks(List<?> chunks) {
        return Collections.emptyIterator();
    }

    /**
     * @reason Replace the debug string
     * @author JellySquid
     */
    @Overwrite
    public String getChunkDebugInfo() {
        return this.renderer.getChunksDebugString();
    }

}
