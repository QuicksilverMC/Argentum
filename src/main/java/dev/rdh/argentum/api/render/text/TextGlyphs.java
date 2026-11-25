package dev.rdh.argentum.api.render.text;

import net.minecraft.client.render.TextRenderer;
import org.jetbrains.annotations.Nullable;

import java.util.random.RandomGenerator;

public final class TextGlyphs {
    private TextGlyphs() {
    }

    public static @Nullable GlyphInfo glyph(TextRenderer renderer, int codepoint) {
        boolean unicode = renderer.getUnicode();
        return renderer.argentum$getBatcher().fonts().glyph(codepoint, unicode).info(unicode);
    }

    public static int obfuscate(TextRenderer renderer, int codepoint, RandomGenerator random) {
        return renderer.argentum$getBatcher().obfuscate(codepoint, renderer.getUnicode(), random);
    }
}
