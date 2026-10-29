package dev.rdh.argentum.impl.render.gui.font;

import dev.rdh.argentum.impl.Argentum;

import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public final class Glyph {
    static final Glyph EMPTY = new Glyph(0.0F, false);

    public final float advance;
    public final float width;
    public final float top;
    public final float height;
    public final float oversample;
    public final boolean unicodePage;

    final Sheet sheet;
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
            Sheet sheet, int srcX, int srcY, int srcWidth, int srcHeight) {
        this.advance = advance;
        this.width = width;
        this.top = top;
        this.height = height;
        this.oversample = oversample;
        this.unicodePage = unicodePage;
        this.sheet = sheet;
        this.srcX = srcX;
        this.srcY = srcY;
        this.srcWidth = srcWidth;
        this.srcHeight = srcHeight;
    }

    Glyph(float advance, boolean unicodePage) {
        this(advance, 0.0F, 0.0F, 8.0F, 1.0F, unicodePage, null, 0, 0, 0, 0);
    }

    public boolean visible() {
        return this.sheet != null;
    }

    public Identifier page() {
        return this.page;
    }

    static final class Sheet {
        private final ResourceManager resources;
        private final Identifier location;
        private final int nominalWidth;
        int[] pixels;
        int width;
        int scale = 1;

        Sheet(int[] pixels, int width) {
            this.resources = null;
            this.location = null;
            this.nominalWidth = width;
            this.pixels = pixels;
            this.width = width;
        }

        Sheet(ResourceManager resources, Identifier location, int nominalWidth) {
            this.resources = resources;
            this.location = location;
            this.nominalWidth = nominalWidth;
        }

        boolean load() {
            if (this.pixels == null) {
                try (InputStream stream = this.resources.getResource(this.location).asStream()) {
                    BufferedImage image = TextureUtil.readImage(stream);
                    this.width = image.getWidth();
                    this.scale = Math.max(1, this.width / this.nominalWidth);
                    this.pixels = image.getRGB(0, 0, this.width, image.getHeight(), null, 0, this.width);
                } catch (IOException | RuntimeException e) {
                    Argentum.LOGGER.warn("Could not read font sheet {}", this.location, e);
                    this.pixels = new int[0];
                }
            }
            return this.pixels.length != 0;
        }
    }
}
