package dev.rdh.argentum.impl.render.blockentity;

import dev.rdh.argentum.impl.render.gui.GlyphSink;
import dev.rdh.argentum.impl.render.gui.TextBatcher;
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

        Layout layout = new Layout(textRenderer, batcher, pages, unicode);
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

    private static final class Layout implements GlyphSink {
        private final TextRenderer textRenderer;
        private final TextBatcher batcher;
        private final SlotSheet sheet;
        private final boolean unicode;
        private final List<SlotSheet.Entry> pages = new ArrayList<>();
        private final FloatArrayList vertices = new FloatArrayList();
        private final IntArrayList colors = new IntArrayList();
        private final ByteArrayList textures = new ByteArrayList();
        private int glyphColor;
        private int glyphTexture;
        private int glyphVertices;

        private Layout(TextRenderer textRenderer, TextBatcher batcher, SlotSheet sheet, boolean unicode) {
            this.textRenderer = textRenderer;
            this.batcher = batcher;
            this.sheet = sheet;
            this.unicode = unicode;
        }

        private boolean line(String text, float x, float y) {
            int color = baseColor();
            boolean bold = false;
            boolean italic = false;
            for (int i = 0; i < text.length(); i++) {
                char character = TextBatcher.normalizeSpace(text.charAt(i));
                if (character == '§' && i + 1 < text.length()) {
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

                float advance = this.glyph(character, italic, x, y, color);
                if (Float.isNaN(advance)) return false;
                if (bold) {
                    this.glyph(character, italic, x + (this.unicode ? 0.5F : 1.0F), y, color);
                    advance++;
                }
                x += (int)advance;
            }
            return true;
        }

        private float glyph(char character, boolean italic, float x, float y, int color) {
            if (character == ' ') return 4.0F;
            int index = TextBatcher.CHARACTERS.indexOf(character);
            if (index != -1 && !this.unicode) {
                float width = this.batcher.getCharWidth(index);
                float right = width - 0.01F - 1.0F;
                this.quad(x, y, index % 16 * 8, index / 16 * 8, right, right, 7.99F, italic, 128.0F, color, 0);
                return width;
            }

            this.glyphColor = color;
            this.glyphTexture = 0;
            this.glyphVertices = 0;
            float advance = this.textRenderer.argentum$captureUnicodeGlyph(character, italic, x, y, this);
            return this.glyphTexture < 0 ? Float.NaN : advance;
        }

        @Override
        public void texture(Identifier location) {
            SlotSheet.Entry page = this.sheet == null ? null : this.sheet.upload(location);
            if (page == null) {
                this.glyphTexture = -1;
                return;
            }
            int texture = this.pages.indexOf(page);
            if (texture < 0) {
                texture = this.pages.size();
                this.pages.add(page);
            }
            this.glyphTexture = texture + 1;
        }

        @Override
        public void vertex(float x, float y, float u, float v) {
            if (this.glyphTexture <= 0) return;
            int index = this.glyphVertices++;
            this.vertex(x, y, u, v, 1.0F);
            if (index == 3) {
                int end = this.vertices.size();
                for (int i = 0; i < 4; i++) {
                    float swap = this.vertices.getFloat(end - 8 + i);
                    this.vertices.set(end - 8 + i, this.vertices.getFloat(end - 4 + i));
                    this.vertices.set(end - 4 + i, swap);
                }
                this.colors.add(this.glyphColor);
                this.textures.add((byte)this.glyphTexture);
            }
        }

        private void quad(float x, float y, float u, float v, float width, float textureWidth, float textureHeight, boolean italic,
                float size, int color, int texture) {
            float slant = italic ? 1.0F : 0.0F;
            this.vertex(x + slant, y, u, v, size);
            this.vertex(x - slant, y + 7.99F, u, v + textureHeight, size);
            this.vertex(x + width - slant, y + 7.99F, u + textureWidth, v + textureHeight, size);
            this.vertex(x + width + slant, y, u + textureWidth, v, size);
            this.colors.add(color);
            this.textures.add((byte)texture);
        }

        private void vertex(float x, float y, float u, float v, float size) {
            this.vertices.add(x);
            this.vertices.add(y);
            this.vertices.add(u / size);
            this.vertices.add(v / size);
        }
    }
}
