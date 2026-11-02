package dev.rdh.cera.modules.shaders;

import dev.rdh.argentum.impl.ext.WorldRendererExtension;
import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

import com.ventooth.swansong.shader.DrawBuffers;
import com.ventooth.swansong.shader.ShadowPassRenderer;
import com.ventooth.swansong.shader.ShadowProperties;
import com.ventooth.swansong.shader.StateGraph.Node;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.util.math.Box;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.frustum.ShadowSearchFrustum;
import org.joml.FrustumIntersection;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

final class CeraShadowPassRenderer implements ShadowPassRenderer {
    @Override
    public void renderShadowPass(ShadowProperties shadow, DrawBuffers buffers) {
        var mc = Minecraft.getInstance();
        var engine = mc.cera$getShaderEngine();
        var worldRenderer = mc.worldRenderer;
        var camera = mc.getCamera();
        var state = engine.state();
        var partialTick = state.getSubTick();
        var instancing = ArgentumWorldRenderer.instance().getEntityInstancing();

        engine.graph().moveTo(Node.ShadowBegin);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();

        mc.gameRenderer.setupCamera(partialTick, 2);
        Frustum.getInstance();
        var playerCuller = new FrustumCuller();
        playerCuller.prepare(state.cameraX(), state.cameraY(), state.cameraZ());

        state.setCameraShadow(shadow.resolution, shadow.distance, shadow.fov, shadow.intervalSize);
        Camera.setup(mc.player, false);

        var perspective = mc.options.perspective;
        mc.options.perspective = 1;

        buffers.shadow.bindDraw();
        GlStateManager.clearColor(1F, 1F, 1F, 1F);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        var casters = new Casters(state.shadowProjection(), state.shadowModelView(), state.camPos());
        ((WorldRendererExtension)worldRenderer).argentum$setupShadowTerrain(playerCuller, new Viewport(casters, new Vector3d(state.camPos())), partialTick);

        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.enableDepthTest();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.disableCull();
        mc.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.enableAlphaTest();

        engine.graph().moveTo(Node.ShadowChunk0);
        worldRenderer.render(BlockLayer.SOLID, partialTick, 2, camera);

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();

        instancing.setShadowPass(true);
        try {
            Lighting.turnOn();
            worldRenderer.renderEntities(camera, casters, partialTick);
            Lighting.turnOff();
        } finally {
            instancing.setShadowPass(false);
        }

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();

        engine.unlockShader();
        engine.blitDepth(buffers.shadowDepthTex0, buffers.shadowDepthTex1);
        buffers.shadow.bind();
        engine.lockShader();

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        mc.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        engine.graph().moveTo(Node.ShadowChunk1);
        worldRenderer.render(BlockLayer.TRANSLUCENT, partialTick, 2, camera);

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();

        engine.graph().moveTo(Node.ShadowLast);
        mc.options.perspective = perspective;

        if (shadow.depthMipmapEnabled(0)) {
            engine.genMipmap(buffers.shadowDepthTex0);
        }
        if (shadow.depthMipmapEnabled(1)) {
            engine.genMipmap(buffers.shadowDepthTex1);
        }
        if (shadow.colorMipmapEnabled(0)) {
            engine.genMipmap(buffers.shadowColorTex0);
        }
        if (shadow.colorMipmapEnabled(1)) {
            engine.genMipmap(buffers.shadowColorTex1);
        }

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);

        mc.getRenderTarget().bindWrite(false);
        mc.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
    }

    private record Casters(FrustumIntersection frustum, Vector3f light, Vector3dc camera)
            implements org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum, ShadowSearchFrustum, Culler {

        Casters(Matrix4dc projection, Matrix4dc modelView, Vector3dc camera) {
            var view = new Matrix4f(modelView);
            this(new FrustumIntersection(new Matrix4f(projection).mul(view)), view.positiveZ(new Vector3f()), new Vector3d(camera));
        }

        @Override
        public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            return this.frustum.testAab(minX, minY, minZ, maxX, maxY, maxZ);
        }

        @Override
        public boolean supportsOcclusionSearch() {
            return true;
        }

        @Override
        public float shadowLightX() {
            return this.light.x;
        }

        @Override
        public float shadowLightY() {
            return this.light.y;
        }

        @Override
        public float shadowLightZ() {
            return this.light.z;
        }

        @Override
        public boolean isVisible(Box box) {
            return this.testAab(
                    (float) (box.minX - this.camera.x()),
                    (float) (box.minY - this.camera.y()),
                    (float) (box.minZ - this.camera.z()),
                    (float) (box.maxX - this.camera.x()),
                    (float) (box.maxY - this.camera.y()),
                    (float) (box.maxZ - this.camera.z())
            );
        }

        @Override
        public void prepare(double x, double y, double z) {
        }
    }
}
