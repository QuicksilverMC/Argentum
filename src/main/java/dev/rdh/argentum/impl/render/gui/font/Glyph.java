package dev.rdh.argentum.impl.render.gui.font;

import net.minecraft.resource.Identifier;

public final class Glyph {
    static final Glyph EMPTY = new Glyph(0.0F, false);

    public final float advance;
    public final float width;
    public final float top;
    public final float height;
    public final float oversample;
    public final boolean unicodePage;

    final int[] pixels;
    final int stride;
    final int srcX;
    final int srcY;
    final int srcWidth;
    final int srcHeight;

    Identifier page;
    public float u0;
    public float v0;
    public float u1;
    public float v1;

    Glyph(float advance, float width, float top, float height, float oversample, boolean unicodePage,
            int[] pixels, int stride, int srcX, int srcY, int srcWidth, int srcHeight) {
        this.advance = advance;
        this.width = width;
        this.top = top;
        this.height = height;
        this.oversample = oversample;
        this.unicodePage = unicodePage;
        this.pixels = pixels;
        this.stride = stride;
        this.srcX = srcX;
        this.srcY = srcY;
        this.srcWidth = srcWidth;
        this.srcHeight = srcHeight;
    }

    Glyph(float advance, boolean unicodePage) {
        this(advance, 0.0F, 0.0F, 8.0F, 1.0F, unicodePage, null, 0, 0, 0, 0, 0);
    }

    public boolean visible() {
        return this.pixels != null;
    }

    public Identifier page() {
        return this.page;
    }
}
