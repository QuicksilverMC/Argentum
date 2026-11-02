package dev.rdh.cera.modules.shaders;

import dev.rdh.cera.Cera;

import com.ventooth.swansong.resources.pack.DimensionInfo;
import com.ventooth.swansong.shader.HostRenderer;
import com.ventooth.swansong.shader.Report;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import org.jetbrains.annotations.Nullable;

import java.time.LocalTime;

public final class CeraHostRenderer implements HostRenderer {
    private static final String CHAT_PREFIX = "[" + Formatting.GOLD + "Cera" + Formatting.RESET + "] ";

    private final CeraShaderPackManager shaderPacks = new CeraShaderPackManager(FabricLoader.getInstance().getGameDir());

    @Override
    public java.io.InputStream openResource(String location) throws java.io.IOException {
        return Minecraft.getInstance().getResourceManager().getResource(new Identifier(location)).asStream();
    }

    @Override
    public CeraShaderPackManager shaderPacks() {
        return shaderPacks;
    }

    @Override
    public boolean isHandVisible() {
        var mc = Minecraft.getInstance();
        var camera = mc.getCamera();
        if (camera == null) {
            return false;
        }
        var sleeping = camera instanceof LivingEntity living && living.isSleeping();
        return !mc.options.hideGui && mc.options.perspective == 0 && !sleeping;
    }

    @Override
    public double fieldOfView(float partialTick) {
        return Minecraft.getInstance().gameRenderer.getFov(partialTick, false);
    }

    @Override
    public double farPlaneDistance() {
        return Minecraft.getInstance().gameRenderer.renderDistance;
    }

    @Override
    public void applyCameraEffects(float partialTick) {
        var mc = Minecraft.getInstance();
        mc.gameRenderer.applyHurtCam(partialTick);
        if (mc.options.viewBobbing) {
            mc.gameRenderer.applyViewBobbing(partialTick);
        }
    }

    @Override
    public void renderFirstPersonItem(float partialTick) {
        var gameRenderer = Minecraft.getInstance().gameRenderer;
        gameRenderer.enableLightMap();
        gameRenderer.itemInHandRenderer.renderInFirstPerson(partialTick);
        gameRenderer.disableLightMap();
    }

    @Override
    public double anaglyphOffset() {
        return Minecraft.getInstance().options.anaglyph ? 1 : 0;
    }

    @Override
    public int anaglyphField() {
        return GameRenderer.anaglyphFilter;
    }

    @Override
    public void bindMainFramebuffer(boolean setViewport) {
        Minecraft.getInstance()
                 .getRenderTarget()
                 .bindWrite(setViewport);
    }

    @Override
    public void resetLightmapCoords() {
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, 240F, 240F);
    }

    @Override
    public void reloadChunkRenderers() {
        Minecraft.getInstance().worldRenderer.reload();
    }

    @Override
    public @Nullable DimensionInfo currentDimension() {
        var world = Minecraft.getInstance().world;
        if (world == null || world.dimension == null) {
            return null;
        }
        var dimension = world.dimension;
        return new DimensionInfo(dimension.getId(), dimension.getName(), dimension.getClass());
    }

    @Override
    public int mainFramebufferId() {
        return Minecraft.getInstance()
                        .getRenderTarget().frameBufferId;
    }

    @Override
    public int mainFramebufferTexture() {
        return Minecraft.getInstance()
                        .getRenderTarget().colorTextureId;
    }

    @Override
    public int mainFramebufferWidth() {
        return Minecraft.getInstance()
                        .getRenderTarget().width;
    }

    @Override
    public int mainFramebufferHeight() {
        return Minecraft.getInstance()
                        .getRenderTarget().height;
    }

    @Override
    public void onEngineInit() {
    }

    @Override
    public void onEngineDeinit() {
    }

    @Override
    public double handDepth() {
        return 0.125 * Cera.CONFIG.handDepth;
    }

    @Override
    public double renderQuality() {
        return Cera.CONFIG.renderQuality;
    }

    @Override
    public double shadowQuality() {
        return Cera.CONFIG.shadowQuality;
    }

    @Override
    public boolean allowDepthOfField() {
        return Cera.CONFIG.depthOfField;
    }

    @Override
    public @Nullable Integer findBlockId(String modId, String blockName) {
        var id = new Identifier(modId, blockName);
        return Block.REGISTRY.containsKey(id) ? Block.getId(Block.REGISTRY.get(id)) : null;
    }

    @Override
    public void onLifecycle(Lifecycle stage) {
    }

    @Override
    public void reportToChat(Report report) {
        if (!Cera.CONFIG.shaderErrorReports || report.erroredShaders.isEmpty()) {
            return;
        }
        chat("-----------------");
        chat(Formatting.YELLOW + LocalTime.now()
                                          .toString());
        chat(Formatting.RED + "Failed to load shaders:");
        for (var shader : report.erroredShaders) {
            chat("  " + Formatting.RED + shader);
        }
        chat(Formatting.YELLOW + "Check the log for more details");
        chat("-----------------");
    }

    private static void chat(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.addMessage(new LiteralText(CHAT_PREFIX + message));
        }
    }

    @Override
    public void invalidateTextureBindings() {
        for (var texture : GlStateManager.TEXTURES) {
            texture.texture = -1;
        }
        GlStateManager.texture = 0;
    }
}
