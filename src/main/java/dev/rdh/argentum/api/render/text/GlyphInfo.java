package dev.rdh.argentum.api.render.text;

public record GlyphInfo(
        float advance, float boldShift, float top, float width, float height,
        float texelsPerUnit, int imageWidth, int imageHeight) {
}
