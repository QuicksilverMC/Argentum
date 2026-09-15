package dev.rdh.argentum.impl.render.hud.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.VertexFormat;
import net.minecraft.client.render.vertex.VertexFormatElement;
import net.minecraft.resource.Identifier;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.Arrays;

final class GuiItemGlints {
    private static final Identifier TEXTURE = new Identifier("textures/misc/enchanted_item_glint.png");

    private static final VertexFormat FORMAT = new VertexFormat();

    static {
        FORMAT.addElement(DefaultVertexFormat.POSITION_ELEMENT);
        FORMAT.addElement(DefaultVertexFormat.UV0_ELEMENT);
        FORMAT.addElement(new VertexFormatElement(1, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2));
    }

    private final BufferBuilder buffer = new BufferBuilder(64 * 1024 / Integer.BYTES);
    private final BufferUploader uploader = new BufferUploader();

    private float[] data = new float[1024 * 10];
    private int size;

    void quad(int slot, int x, int y, float z, float extent, TextureAtlasSprite sprite) {
        int offset = this.size++ * 10;
        if (offset == this.data.length) this.data = Arrays.copyOf(this.data, this.data.length * 2);

        this.data[offset] = x;
        this.data[offset + 1] = y;
        this.data[offset + 2] = z;
        this.data[offset + 3] = GuiItemAtlas.u0(slot);
        this.data[offset + 4] = GuiItemAtlas.v0(slot);
        this.data[offset + 5] = extent;
        this.data[offset + 6] = sprite.getUMin();
        this.data[offset + 7] = sprite.getVMin();
        this.data[offset + 8] = sprite.getUMax();
        this.data[offset + 9] = sprite.getVMax();
    }

    boolean isEmpty() {
        return this.size == 0;
    }

    void flush(int atlasTexture) {
        if (this.size == 0) return;

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(768, 1, 768, 1);
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(514);
        GlStateManager.color4f(0.5F, 0.25F, 0.8F, 1.0F);

        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        GlStateManager.enableTexture();
        GlStateManager.bindTexture(atlasTexture);
        this.maskTexture();

        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        GlStateManager.enableTexture();
        Minecraft.getInstance().getTextureManager().bind(TEXTURE);
        this.glintTexture();

        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.pushMatrix();
        try {
            this.drawPass(3000, 1.0F, -50.0F);
            this.drawPass(4873, -1.0F, 10.0F);
        } finally {
            GlStateManager.popMatrix();
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GlStateManager.disableTexture();
            GlStateManager.activeTexture(GLX.GL_TEXTURE0);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GlStateManager.depthFunc(515);
            GlStateManager.depthMask(true);
            this.size = 0;
        }
    }

    private void drawPass(int period, float direction, float rotation) {
        GlStateManager.loadIdentity();
        GlStateManager.scalef(8.0F, 8.0F, 8.0F);
        float offset = (float) (Minecraft.getTime() % period) / period / 8.0F;
        GlStateManager.translatef(direction * offset, 0.0F, 0.0F);
        GlStateManager.rotatef(rotation, 0.0F, 0.0F, 1.0F);

        this.buffer.begin(GL11.GL_QUADS, FORMAT);
        for (int i = 0; i < this.size; i++) {
            int offsetIndex = i * 10;
            float x = this.data[offsetIndex];
            float y = this.data[offsetIndex + 1];
            float z = this.data[offsetIndex + 2];
            float u = this.data[offsetIndex + 3];
            float v = this.data[offsetIndex + 4];
            float extent = this.data[offsetIndex + 5];
            float glintU0 = this.data[offsetIndex + 6];
            float glintV0 = this.data[offsetIndex + 7];
            float glintU1 = this.data[offsetIndex + 8];
            float glintV1 = this.data[offsetIndex + 9];
            this.vertex(x, y + 16, z, u, v, glintU0, glintV1);
            this.vertex(x + 16, y + 16, z, u + extent, v, glintU1, glintV1);
            this.vertex(x + 16, y, z, u + extent, v + extent, glintU1, glintV0);
            this.vertex(x, y, z, u, v + extent, glintU0, glintV0);
        }
        this.buffer.end();
        this.uploader.end(this.buffer);
    }

    private void vertex(float x, float y, float z, float maskU, float maskV, float glintU, float glintV) {
        this.buffer.vertex(x, y, z).texture(maskU, maskV).texture(glintU, glintV).nextVertex();
    }

    private void maskTexture() {
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
    }

    private void glintTexture() {
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL13.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL13.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL11.GL_TEXTURE);
    }
}
