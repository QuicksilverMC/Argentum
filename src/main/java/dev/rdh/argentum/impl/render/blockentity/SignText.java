package dev.rdh.argentum.impl.render.blockentity;

import dev.rdh.argentum.impl.render.gui.TextBatcher;
import dev.rdh.argentum.impl.render.gui.font.FontSet;
import dev.rdh.argentum.impl.render.gui.font.Glyph;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.render.TextRenderUtils;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public record SignText(Text[] lines, String font, BakedBlockEntities.Region region, boolean unicode, List<SlotSheet.Entry> pages,
        float[] vertices, int[] colors, byte[] textures) {
    private static final String FORMATTING = "0123456789abcdefklmnor";

    private static int baseColor() {
        return 0;
    }

    public boolean current(Text[] lines, BakedBlockEntities.Region region, boolean unicode) {
        if (this.region != region || this.unicode != unicode) return false;
        for (int i = 0; i < this.lines.length; i++) {
            if (this.lines[i] != lines[i]) return false;
        }
        for (SlotSheet.Entry page : this.pages) {
            if (!page.use()) return false;
        }
        return true;
    }

    public boolean bakeable() {
        return this.vertices != null;
    }

    public BakedBlockEntities.Region texture(int glyph) {
        int texture = this.textures[glyph];
        return texture == 0 ? this.region : this.pages.get(texture - 1).region();
    }

    static SignText of(SignBlockEntity sign, TextRenderer textRenderer, String font, BakedBlockEntities.Region region, SlotSheet pages) {
        Text[] lines = sign.lines.clone();
        boolean unicode = textRenderer.getUnicode();
        TextBatcher batcher = textRenderer.argentum$getBatcher();
        if (region == null || textRenderer.isBidirectional() || batcher.isBlending()) {
            return new SignText(lines, font, region, unicode, List.of(), null, null, null);
        }

        Layout layout = new Layout(textRenderer, batcher, region, pages, unicode);
        for (int row = 0; row < lines.length; row++) {
            if (lines[row] == null) continue;
            List<Text> wrapped = TextRenderUtils.wrapText(lines[row], 90, textRenderer, false, true);
            String text = !wrapped.isEmpty() ? wrapped.getFirst().getFormattedString() : "";
            if (!layout.line(text, -textRenderer.getWidth(text) / 2, row * 10 - lines.length * 5)) {
                return new SignText(lines, font, region, unicode, List.of(), null, null, null);
            }
        }
        return new SignText(lines, font, region, unicode, List.copyOf(layout.pages), layout.vertices.toFloatArray(),
                layout.colors.toIntArray(), layout.textures.toByteArray());
    }

    private static final class Layout {
        private final TextRenderer textRenderer;
        private final FontSet fonts;
        private final Identifier fontLocation;
        private final BakedBlockEntities.Region region;
        private final SlotSheet sheet;
        private final boolean unicode;
        private final List<SlotSheet.Entry> pages = new ArrayList<>();
        private final FloatArrayList vertices = new FloatArrayList();
        private final IntArrayList colors = new IntArrayList();
        private final ByteArrayList textures = new ByteArrayList();

        private Layout(TextRenderer textRenderer, TextBatcher batcher, BakedBlockEntities.Region region, SlotSheet sheet, boolean unicode) {
            this.textRenderer = textRenderer;
            this.fonts = batcher.fonts();
            this.fontLocation = textRenderer.argentum$getFontLocation();
            this.region = region;
            this.sheet = sheet;
            this.unicode = unicode;
        }

        private boolean line(String text, float x, float y) {
            int color = baseColor();
            boolean bold = false;
            boolean italic = false;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '§' && i + 1 < text.length()) {
                    int code = FORMATTING.indexOf(Character.toLowerCase(text.charAt(++i)));
                    if (code < 16) {
                        bold = false;
                        italic = false;
                        color = this.textRenderer.getColor(FORMATTING.charAt(code < 0 ? 15 : code));
                    } else if (code == 17) {
                        bold = true;
                    } else if (code == 20) {
                        italic = true;
                    } else if (code == 21) {
                        bold = false;
                        italic = false;
                        color = baseColor();
                    } else {
                        return false;
                    }
                    continue;
                }

                int codepoint = text.codePointAt(i);
                i += Character.charCount(codepoint) - 1;
                Glyph glyph = this.fonts.glyph(codepoint, this.unicode);
                if (glyph.visible()) {
                    if (!this.glyph(glyph, italic, x, y, color)) return false;
                    if (bold) this.glyph(glyph, italic, x + (this.unicode || glyph.unicodePage ? 0.5F : 1.0F), y, color);
                }
                x += (int) (bold ? glyph.advance + 1.0F : glyph.advance);
            }
            return true;
        }

        private boolean glyph(Glyph glyph, boolean italic, float x, float y, int color) {
            float[] texels = glyph.texels();
            if (texels == null) return false;

            BakedBlockEntities.Region texture = this.region;
            int index = 0;
            if (!glyph.texture().equals(this.fontLocation)) {
                SlotSheet.Entry page = this.sheet == null ? null : this.sheet.upload(glyph.texture());
                if (page == null) return false;
                index = this.pages.indexOf(page);
                if (index < 0) {
                    index = this.pages.size();
                    this.pages.add(page);
                }
                index++;
                texture = page.region();
            }

            float top = y + glyph.top;
            float bottom = top + glyph.height - 0.01F;
            float right = x + glyph.width;
            float topSlant = italic ? 1.0F - 0.25F * glyph.top : 0.0F;
            float bottomSlant = italic ? 1.0F - 0.25F * (glyph.top + glyph.height) : 0.0F;
            float u0 = texels[0] / texture.texelWidth();
            float v0 = texels[1] / texture.texelHeight();
            float u1 = texels[2] / texture.texelWidth();
            float v1 = texels[3] / texture.texelHeight();
            this.vertex(x + topSlant, top, u0, v0);
            this.vertex(x + bottomSlant, bottom, u0, v1);
            this.vertex(right + bottomSlant, bottom, u1, v1);
            this.vertex(right + topSlant, top, u1, v0);
            this.colors.add(color);
            this.textures.add((byte) index);
            return true;
        }

        private void vertex(float x, float y, float u, float v) {
            this.vertices.add(x);
            this.vertices.add(y);
            this.vertices.add(u);
            this.vertices.add(v);
        }
    }
}
