package dev.rdh.argentum.impl.render.gui.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.AbstractTexture;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

final class GlyphAtlas {
    static final int SIZE = 512;
    static final float WHITE = 0.5F / SIZE;

    private final String name;
    private final List<Page> pages = new ObjectArrayList<>(1);
    private int x;
    private int y;
    private int rowHeight;

    GlyphAtlas(String name) {
        this.name = name;
    }

    void bake(Glyph glyph) {
        int width = glyph.srcWidth;
        int height = glyph.srcHeight;
        if (width >= SIZE || height >= SIZE) return;

        if (this.pages.isEmpty()) this.addPage();
        if (this.x + width > SIZE) this.newRow();
        if (this.y + height > SIZE) {
            this.addPage();
            if (this.x + width > SIZE) this.newRow();
        }

        int[] texels = new int[width * height];
        for (int row = 0; row < height; row++) {
            System.arraycopy(glyph.pixels, (glyph.srcY + row) * glyph.stride + glyph.srcX, texels, row * width, width);
        }
        Page page = this.pages.get(this.pages.size() - 1);
        page.upload(texels, width, height, this.x, this.y);

        glyph.page = page.id;
        glyph.u0 = this.x / (float) SIZE;
        glyph.v0 = this.y / (float) SIZE;
        glyph.u1 = (this.x + glyph.width * glyph.oversample) / SIZE;
        glyph.v1 = (this.y + (glyph.height - 0.01F) * glyph.oversample) / SIZE;

        this.x += width;
        this.rowHeight = Math.max(this.rowHeight, height);
    }

    Identifier whitePage(Identifier current) {
        for (Page page : this.pages) {
            if (page.id == current) return current;
        }
        if (this.pages.isEmpty()) this.addPage();
        return this.pages.get(0).id;
    }

    void reset() {
        for (Page page : this.pages) page.clearGlId();
        this.pages.clear();
    }

    private void newRow() {
        this.x = 0;
        this.y += this.rowHeight;
        this.rowHeight = 0;
    }

    private void addPage() {
        Page page = new Page(new Identifier("argentum", "font/" + this.name + "/" + this.pages.size()));
        Minecraft.getInstance().getTextureManager().register(page.id, page);
        this.pages.add(page);
        page.upload(new int[] {-1}, 1, 1, 0, 0);
        this.x = 1;
        this.y = 0;
        this.rowHeight = 1;
    }

    private static final class Page extends AbstractTexture {
        private final Identifier id;

        private Page(Identifier id) {
            this.id = id;
        }

        @Override
        public void load(ResourceManager resourceManager) {
        }

        private void upload(int[] texels, int width, int height, int x, int y) {
            int previous = GlStateManager.TEXTURES[0].texture;
            if (this.glId == -1) {
                TextureUtil.prepare(this.getGlId(), SIZE, SIZE);
            } else {
                GlStateManager.bindTexture(this.glId);
            }
            TextureUtil.upload(new int[][] {texels}, width, height, x, y, false, false);
            GlStateManager.bindTexture(previous);
        }
    }
}
