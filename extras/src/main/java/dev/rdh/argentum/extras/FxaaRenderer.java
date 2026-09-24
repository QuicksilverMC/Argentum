package dev.rdh.argentum.extras;

import com.google.gson.JsonSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.PostChain;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;

import java.io.IOException;

public final class FxaaRenderer implements AutoCloseable {
    private PostChain chain;
    private boolean failed;

    public void render(Minecraft minecraft, float tickDelta) {
        if (!ArgentumExtras.CONFIG.fxaa) {
            this.close();
            return;
        }

        if (this.chain == null && !this.failed) {
            this.create(minecraft);
        }
        if (this.chain == null) return;

        GlStateManager.matrixMode(5890);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        try {
            this.chain.process(tickDelta);
        } finally {
            GlStateManager.popMatrix();
            minecraft.getRenderTarget().bindWrite(true);
        }
    }

    public void resize(int width, int height) {
        if (this.chain != null) this.chain.resize(width, height);
    }

    private void create(Minecraft minecraft) {
        try {
            ResourceManager resources = minecraft.getResourceManager();
            RenderTarget target = minecraft.getRenderTarget();
            this.chain = new PostChain(minecraft.getTextureManager(), resources, target, new Identifier("shaders/post/fxaa.json"));
            this.chain.resize(minecraft.width, minecraft.height);
        } catch (IOException | JsonSyntaxException e) {
            this.failed = true;
            ArgentumExtras.LOGGER.warn("Failed to load FXAA", e);
        }
    }

    @Override
    public void close() {
        if (this.chain != null) {
            this.chain.close();
            this.chain = null;
        }
        this.failed = false;
    }
}
