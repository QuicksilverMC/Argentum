package dev.rdh.argentum.impl.render.environment;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.ext.MinecraftExtension;
import dev.rdh.argentum.impl.render.ArgentumGlStateService;
import dev.rdh.argentum.impl.render.terrain.fog.ArgentumFogService;
import dev.rdh.argentum.mixin.features.cloud.CameraAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.resource.Identifier;
import org.embeddedt.embeddium.api.util.ColorARGB;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.clouds.CloudFrame;
import org.embeddedt.embeddium.impl.render.clouds.CloudRenderState;
import org.embeddedt.embeddium.impl.render.clouds.CloudRenderer;
import org.embeddedt.embeddium.impl.render.clouds.CloudTexture;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;

public final class ArgentumCloudRenderer {
    private static final Identifier CLOUDS_TEXTURE = new Identifier("textures/environment/clouds.png");
    private static final float EXP_FOG_OPAQUE_DEPTH = (float) Math.log(255.0);

    private static final CloudRenderState RENDER_STATE = new CloudRenderState() {
        private boolean alphaTestWasEnabled;

        @Override
        public void setup(boolean fancy) {
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.enableDepthTest();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(true);
            this.alphaTestWasEnabled = GlStateManager.ALPHA_TEST.state.enabled;
            GlStateManager.disableAlphaTest();
            if (fancy) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
        }

        @Override
        public void restore() {
            GlStateManager.disableBlend();
            GlStateManager.enableCull();
            if (this.alphaTestWasEnabled) {
                GlStateManager.enableAlphaTest();
            }
        }
    };

    private final Matrix4f modelView = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private final FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
    private CloudRenderer renderer;
    private boolean failed;

    public static boolean isEnabled() {
        var target = ((MinecraftExtension) Minecraft.getInstance()).argentum$getInstanceDrawTarget();
        return Argentum.CONFIG.fasterClouds
                && CloudRenderer.isSupported()
                && (target == null || !target.active());
    }

    public boolean render(double x, double z, float relativeHeight, float red, float green, float blue, boolean fancy, float range) {
        if (this.failed) {
            return false;
        }
        float fogEnd = getFogEnd(range);
        if (fogEnd <= 0.0F || Float.isNaN(relativeHeight)) {
            return true;
        }

        this.modelView.set(CameraAccessor.argentum$getModelView());
        GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, this.projectionBuffer);
        this.projection.set(this.projectionBuffer);
        CloudFrame frame = new CloudFrame(this.modelView, this.projection, x, 0.0, z, relativeHeight - 0.33F, 0.0,
                red, green, blue, fancy, range, fogEnd);

        RenderDevice.enterManagedCode();
        try (CommandList commands = RenderDevice.INSTANCE.createCommandList()) {
            if (this.renderer == null) {
                this.renderer = new CloudRenderer(commands, ArgentumGlStateService.INSTANCE, loadTexture());
            }
            if (this.renderer.prepare(commands, frame)) {
                try {
                    this.renderer.render(commands, RENDER_STATE, frame);
                } catch (RuntimeException exception) {
                    GL20.glUseProgram(0);
                    RENDER_STATE.restore();
                    throw exception;
                }
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            this.failed = true;
            Argentum.LOGGER.error("Faster clouds disabled until reload", exception);
            return false;
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    private static float getFogEnd(float range) {
        ArgentumFogService fog = ArgentumFogService.INSTANCE;
        float end = switch (fog.getFogMode()) {
            case NONE -> range;
            case EXP -> EXP_FOG_OPAQUE_DEPTH / fog.getFogDensity();
            case EXP2 -> (float) Math.sqrt(EXP_FOG_OPAQUE_DEPTH) / fog.getFogDensity();
            default -> fog.getFogEnd();
        };
        return end < fog.getSkyFogEdgeEnd() ? Math.min(range, end) : range;
    }

    private static CloudTexture loadTexture() throws IOException {
        try (InputStream stream = Minecraft.getInstance().getResourceManager().getResource(CLOUDS_TEXTURE).asStream()) {
            BufferedImage image = TextureUtil.readImage(stream);
            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = ColorARGB.toABGR(pixels[i]);
            }
            return new CloudTexture(width, height, pixels);
        }
    }

    public void delete() {
        this.failed = false;
        if (this.renderer == null) {
            return;
        }
        RenderDevice.enterManagedCode();
        try (CommandList commands = RenderDevice.INSTANCE.createCommandList()) {
            ArgentumGlStateService.INSTANCE.bindTexture(CloudRenderer.COLORS_TEXTURE_UNIT, GL11.GL_TEXTURE_2D, 0);
            this.renderer.delete(commands);
            this.renderer = null;
        } finally {
            RenderDevice.exitManagedCode();
        }
    }
}
