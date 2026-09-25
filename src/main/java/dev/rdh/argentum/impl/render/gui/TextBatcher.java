package dev.rdh.argentum.impl.render.gui;

import dev.rdh.argentum.impl.Argentum;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.vertex.VertexFormat;
import net.minecraft.resource.Identifier;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Map;

public final class TextBatcher {
    private static final int WIDTH_CACHE_SIZE = 2048;
    private static final int GEOMETRY_CACHE_SIZE = 1024;

    private static final char SECTION = '§';

    public static final int OBFUSCATED =    0b00001;
    public static final int BOLD =          0b00010;
    public static final int STRIKETHROUGH = 0b00100;
    public static final int UNDERLINED =    0b01000;
    public static final int ITALIC =        0b10000;
    private static final int UNCACHEABLE_STYLE = OBFUSCATED | STRIKETHROUGH | UNDERLINED;

    private static final VertexFormat FORMAT = DefaultVertexFormat.POSITION_TEX_COLOR;
    private static final int STRIDE = FORMAT.getIntSize();
    private static final int COLOR_INDEX = FORMAT.getColorOffset() / Integer.BYTES;
    private static final int ALPHA_SHIFT = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? 24 : 0;

    // this got proguarded out in vanilla so we have to duplicate it
    private static final String CHARACTERS = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000";

    private final BufferBuilder buffer = new BufferBuilder(64 * 1024 / Integer.BYTES);
    private final BufferBuilder decorationBuffer = new BufferBuilder(4 * 1024 / Integer.BYTES);
    private final Map<Identifier, BufferBuilder> elementBuffers = new Object2ObjectLinkedOpenHashMap<>(4);
    private final BufferUploader uploader = new BufferUploader();

    private final float[] widths = new float[256];

    private final Object2IntOpenHashMap<String> widthCache = new Object2IntOpenHashMap<>(256);
    { this.widthCache.defaultReturnValue(-1); }
    private final Object2ObjectLinkedOpenHashMap<GeometryKey, Geometry> geometryCache = new Object2ObjectLinkedOpenHashMap<>(256);
    private final GeometryKey lookupKey = new GeometryKey();

    private Identifier texture;
    private boolean batching;
    private boolean drawing;
    private boolean drawingDecorations;
    private boolean blend;

    private float red = 1.0F;
    private float green = 1.0F;
    private float blue = 1.0F;
    private float alpha = 1.0F;

    private GeometryKey pendingKey;
    private final List<Segment> pendingSegments = new ObjectArrayList<>(4);
    private boolean appendable;
    private float originX;
    private float originY;

    private int elementBatchDepth;
    private Runnable beforeImmediateText;

    private int endStyle;
    private ByteBuffer uploadBuffer;

    public void readWidths(Identifier fontLocation, int[] characterWidths) {
        BufferedImage image;
        try {
            image = TextureUtil.readImage(
                    Minecraft.getInstance().getResourceManager().getResource(fontLocation).asStream());
        } catch (IOException e) {
            Argentum.LOGGER.warn("Could not read font {} for glyph widths", fontLocation, e);
            for (int i = 0; i < this.widths.length; i++) this.widths[i] = characterWidths[i];
            return;
        }

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int[] pixels = new int[imageWidth * imageHeight];
        image.getRGB(0, 0, imageWidth, imageHeight, pixels, 0, imageWidth);

        int glyphHeight = imageHeight / 16;
        int glyphWidth = imageWidth / 16;
        float scale = 8.0F / glyphWidth;

        for (int character = 0; character < 256; character++) {
            int column = character % 16;
            int row = character / 16;

            int lastUsed;
            for (lastUsed = glyphWidth - 1; lastUsed >= 0; lastUsed--) {
                int x = column * glyphWidth + lastUsed;
                boolean empty = true;
                for (int y = 0; y < glyphHeight; y++) {
					if((pixels[x + (row * glyphWidth + y) * imageWidth] >> 24 & 0xFF) != 0) {
						empty = false;
						break;
					}
                }
                if (!empty) break;
            }

            this.widths[character] = (lastUsed + 1) * scale + 1.0F;
        }

        // vanilla leaves widths[32]=1 and hardcodes 4 in getWidth(), but we want people to be able to override this
        this.widths[32] = 4.0F;

        for (int i = 0; i < characterWidths.length; i++) {
            characterWidths[i] = Math.round(this.widths[i]);
        }
        this.clearCaches();
    }

    public float getCharWidth(int index) {
        return this.widths[index];
    }

    public void setCharWidth(int index, float width) {
        this.widths[index] = width;
    }

    public void clearCaches() {
        this.widthCache.clear();
        this.invalidateGeometry();
    }

    /** Characters vanilla has no glyph for but which should both render and measure as a space */
    public static char normalizeSpace(char character) {
        return character == '\u202f' || character == '\u00a0' || character == '\u2007' ? ' ' : character;
    }

    public float charWidth(char character, boolean unicode, byte[] glyphSizes) {
        if (character == SECTION) return -1.0F;

        int index = CHARACTERS.indexOf(character);
        if ((character > 0 && index != -1 && !unicode) || character == ' ') return this.widths[index];

        if (glyphSizes[character] == 0) return 0.0F;
        int left = glyphSizes[character] >>> 4;
        int right = glyphSizes[character] & 15;
        if (right > 7) {
            right = 15;
            left = 0;
        }
        return (right + 1 - left) / 2 + 1;
    }

    public int stringWidth(String text, boolean unicode, byte[] glyphSizes) {
        if (text == null) return 0;

        int cached = this.widthCache.getInt(text);
        if (cached != -1) return cached;

        float total = 0.0F;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char character = normalizeSpace(text.charAt(i));
            float width = this.charWidth(character, unicode, glyphSizes);
            if (width < 0.0F && i < text.length() - 1) {
                character = text.charAt(++i);
                if (character == 'l' || character == 'L') bold = true;
                else if (character == 'r' || character == 'R') bold = false;
                width = 0.0F;
            }
            total += width;
            if (bold && width > 0.0F) total += 1.0F;
        }

        int rounded = Math.round(total);
        this.widthCache.put(text, rounded);
        if (this.widthCache.size() > WIDTH_CACHE_SIZE) this.widthCache.clear();
        return rounded;
    }

    public void setColor(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    public boolean isBatching() {
        return this.batching;
    }

    public BufferBuilder decorationBuffer() {
        return this.decorationBuffer;
    }

    public boolean beginDecorations(int mode) {
        if (this.batching && !this.drawingDecorations) {
            this.decorationBuffer.begin(mode, DefaultVertexFormat.POSITION_COLOR);
            this.drawingDecorations = true;
        }
        return !this.batching;
    }

    public void colorDecoration(BufferBuilder buffer) {
        if (this.batching) buffer.color(this.red, this.green, this.blue, this.alpha);
    }

    public boolean shouldFlushBeforeImmediate(String text) {
        return this.elementBatchDepth > 0 && this.beforeImmediateText != null
                && text != null && !text.isEmpty()
                && (!Argentum.CONFIG.fontBatching || !cacheable(text));
    }

    public void runBeforeImmediateText() {
        this.beforeImmediateText.run();
    }

    public float begin(String text, boolean shadow, int style, float x, float y, TextureManager textureManager) {
        if (text.isEmpty()) return 0.0F;

        this.batching = Argentum.CONFIG.fontBatching;
        this.pendingKey = null;
        this.pendingSegments.clear();
        this.appendable = false;

        if (!this.batching || !cacheable(text) || (style & UNCACHEABLE_STYLE) != 0) {
            this.flushElementBatch(textureManager);
            return Float.NaN;
        }
        this.appendable = this.elementBatchDepth > 0;

        GeometryKey key = this.lookupKey.set(text, shadow, style,
                Float.floatToIntBits(this.red), Float.floatToIntBits(this.green), Float.floatToIntBits(this.blue)
        );
        Geometry geometry = this.geometryCache.getAndMoveToLast(key);
        if (geometry != null) {
            int alpha = this.alphaByte();
            for (Segment segment : geometry.segments()) {
                if (this.appendable) {
                    segment.setAlpha(alpha);
                    this.append(segment.texture, segment.vertices, x, y);
                } else {
                    textureManager.bind(segment.texture);
                    this.drawCached(segment, alpha, x, y);
                }
            }
            GlStateManager.color4f(geometry.red(), geometry.green(), geometry.blue(), this.alpha);
            this.setColor(geometry.red(), geometry.green(), geometry.blue(), this.alpha);
            this.endStyle = geometry.style();
            this.batching = false;
            return geometry.advance();
        }

        this.pendingKey = new GeometryKey(key);
        this.originX = x;
        this.originY = y;
        return Float.NaN;
    }

    public int endStyle() {
        return this.endStyle;
    }

    public void end(float x, int style) {
        this.flush();
        this.flushDecorations();
        if (!this.pendingSegments.isEmpty()) {
            this.geometryCache.put(this.pendingKey, new Geometry(this.pendingSegments.toArray(new Segment[0]),
                    x - this.originX, style, this.red, this.green, this.blue));
            if (this.geometryCache.size() > GEOMETRY_CACHE_SIZE) {
                delete(this.geometryCache.removeFirst());
            }
        }
        this.pendingKey = null;
        this.pendingSegments.clear();
        this.appendable = false;
        this.batching = false;
    }

    public float drawBasicGlyph(int character, boolean italic, float x, float y,
            TextureManager textureManager, Identifier fontLocation) {
        if (!this.batching) return Float.NaN;

        int textureX = character % 16 * 8;
        int textureY = character / 16 * 8;
        int slant = italic ? 1 : 0;
        float width = this.widths[character];
        float right = width - 0.01F;

        this.useTexture(textureManager, fontLocation);
        this.quad(
                x + slant, y, textureX / 128.0F, textureY / 128.0F,
                x - slant, y + 7.99F, textureX / 128.0F, (textureY + 7.99F) / 128.0F,
                x + right - 1.0F + slant, y, (textureX + right - 1.0F) / 128.0F, textureY / 128.0F,
                x + right - 1.0F - slant, y + 7.99F,
                (textureX + right - 1.0F) / 128.0F, (textureY + 7.99F) / 128.0F
        );
        return width;
    }

    public float drawUnicodeGlyph(char character, boolean italic, float x, float y,
            TextureManager textureManager, Identifier page, byte[] glyphSizes) {
        if (!this.batching) return Float.NaN;
        if (glyphSizes[character] == 0) return 0.0F;

        int left = glyphSizes[character] >>> 4;
        int right = (glyphSizes[character] & 15) + 1;
        float textureX = character % 16 * 16 + left;
        float textureY = (character & 255) / 16 * 16;
        float width = right - left - 0.02F;
        float slant = italic ? 1.0F : 0.0F;

        this.useTexture(textureManager, page);
        this.quad(
                x + slant, y, textureX / 256.0F, textureY / 256.0F,
                x - slant, y + 7.99F, textureX / 256.0F, (textureY + 15.98F) / 256.0F,
                x + width / 2.0F + slant, y, (textureX + width) / 256.0F, textureY / 256.0F,
                x + width / 2.0F - slant, y + 7.99F,
                (textureX + width) / 256.0F, (textureY + 15.98F) / 256.0F
        );
        return (right - left) / 2.0F + 1.0F;
    }

    public void beginElementBatch(Runnable beforeImmediateText) {
        if (this.elementBatchDepth++ == 0) this.beforeImmediateText = beforeImmediateText;
    }

    public void endElementBatch(TextureManager textureManager) {
        if (this.elementBatchDepth == 0) throw new IllegalStateException("Text batch not active");
        if (--this.elementBatchDepth == 0) {
            this.flushElementBatch(textureManager);
            this.beforeImmediateText = null;
        }
    }

    public void invalidateGeometry() {
        for (Geometry geometry : this.geometryCache.values()) {
            delete(geometry);
        }
        this.geometryCache.clear();
    }

    private static void delete(Geometry geometry) {
        for (Segment segment : geometry.segments()) {
            if (segment.buffer != null) segment.buffer.delete();
        }
    }

    public void setBlend(boolean blend) {
        this.blend = blend;
    }

    private boolean pushBlend() {
        if (!this.blend || GL11.glIsEnabled(GL11.GL_BLEND)) return false;
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        return true;
    }

    private void popBlend(boolean pushed) {
        if (pushed) GlStateManager.disableBlend();
    }

    private void upload(BufferBuilder buffer) {
        boolean pushed = this.pushBlend();
        try {
            this.uploader.end(buffer);
        } finally {
            this.popBlend(pushed);
        }
    }

    private void flush() {
        if (!this.drawing) return;

        this.buffer.end();
        if (this.pendingKey == null) {
            this.upload(this.buffer);
        } else {
            IntBuffer source = this.buffer.getBuffer().asIntBuffer();
            int[] vertices = new int[source.remaining()];
            source.get(vertices);
            this.pendingSegments.add(new Segment(this.texture, vertices, this.alphaByte()));
            if (this.appendable) {
                this.append(this.texture, vertices, this.originX, this.originY);
            } else {
                GlStateManager.pushMatrix();
                GlStateManager.translatef(this.originX, this.originY, 0.0F);
                this.upload(this.buffer);
                GlStateManager.popMatrix();
            }
        }
        this.drawing = false;
    }

    private void useTexture(TextureManager textureManager, Identifier texture) {
        if (this.drawing && (texture == this.texture || texture.equals(this.texture))) return;

        this.flush();
        textureManager.bind(texture);
        this.texture = texture;
        this.drawing = true;
        this.buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    private void quad(float x0, float y0, float u0, float v0,
            float x1, float y1, float u1, float v1,
            float x2, float y2, float u2, float v2,
            float x3, float y3, float u3, float v3) {
        this.vertex(x0, y0, u0, v0);
        this.vertex(x1, y1, u1, v1);
        this.vertex(x3, y3, u3, v3);
        this.vertex(x2, y2, u2, v2);
    }

    private void vertex(float x, float y, float u, float v) {
        if (this.pendingKey != null) {
            x -= this.originX;
            y -= this.originY;
        }
        this.buffer.vertex(x, y, 0.0D).texture(u, v)
                .color(this.red, this.green, this.blue, this.alpha)
                .nextVertex();
    }

    private void flushDecorations() {
        if (!this.drawingDecorations) return;

        this.decorationBuffer.end();
        GlStateManager.disableTexture();
        this.upload(this.decorationBuffer);
        GlStateManager.enableTexture();
        this.drawingDecorations = false;
    }

    private static boolean cacheable(String text) {
        if (text.isEmpty()) return false;

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == SECTION && i + 1 < text.length()) {
                char formatting = Character.toLowerCase(text.charAt(++i));
                // obfuscated text changes every frame, and decorations are not part of the geometry
                if (formatting == 'k' || formatting == 'm' || formatting == 'n') return false;
            }
        }
        return true;
    }

    private int alphaByte() {
        return (int) (this.alpha * 255.0F);
    }

    private void drawCached(Segment segment, int alpha, float x, float y) {
        if (segment.buffer != null && segment.bufferAlpha == alpha) {
            this.draw(segment.buffer, x, y);
            return;
        }

        segment.setAlpha(alpha);
        if (segment.lastAlpha == alpha) {
            if (segment.buffer == null) segment.buffer = new VertexBuffer(FORMAT);
            segment.buffer.upload(this.uploadBuffer(segment.vertices));
            segment.bufferAlpha = alpha;
            this.draw(segment.buffer, x, y);
            return;
        }

        segment.lastAlpha = alpha;
        this.buffer.begin(GL11.GL_QUADS, FORMAT);
        this.buffer.argentum$appendTranslated(segment.vertices, x, y);
        this.buffer.end();
        this.upload(this.buffer);
    }

    private ByteBuffer uploadBuffer(int[] vertices) {
        int bytes = vertices.length * Integer.BYTES;
        if (this.uploadBuffer == null || this.uploadBuffer.capacity() < bytes) {
            this.uploadBuffer = BufferUtils.createByteBuffer(Math.max(bytes, 16 * 1024));
        }
        this.uploadBuffer.clear();
        this.uploadBuffer.asIntBuffer().put(vertices);
        this.uploadBuffer.limit(bytes);
        return this.uploadBuffer;
    }

    private void draw(VertexBuffer buffer, float x, float y) {
        boolean pushed = this.pushBlend();
        GlStateManager.pushMatrix();
        GlStateManager.translatef(x, y, 0.0F);
        buffer.bind();
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        int stride = format.getVertexSize();
        GL11.glVertexPointer(3, GL11.GL_FLOAT, stride, format.getOffset(0));
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, stride, format.getUvOffset(0));
        GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, stride, format.getColorOffset());
        buffer.draw(GL11.GL_QUADS);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.clearColor();
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        buffer.unbind();
        GlStateManager.popMatrix();
        this.popBlend(pushed);
    }

    private void append(Identifier texture, int[] vertices, float x, float y) {
        BufferBuilder buffer = this.elementBuffers.get(texture);
        if (buffer == null) {
            buffer = new BufferBuilder(32 * 1024 / Integer.BYTES);
            this.elementBuffers.put(texture, buffer);
        }
        if (buffer.getVertexCount() == 0) {
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        }
        buffer.argentum$appendTranslated(vertices, x, y);
    }

    private void flushElementBatch(TextureManager textureManager) {
        for (Map.Entry<Identifier, BufferBuilder> entry : this.elementBuffers.entrySet()) {
            BufferBuilder buffer = entry.getValue();
            if (buffer.getVertexCount() == 0) continue;

            buffer.end();
            textureManager.bind(entry.getKey());
            this.upload(buffer);
        }
    }

    private static final class GeometryKey {
        private String text;
        private boolean shadow;
        private int style;
        private int red;
        private int green;
        private int blue;

        private GeometryKey() {
        }

        private GeometryKey(GeometryKey key) {
            this.set(key.text, key.shadow, key.style, key.red, key.green, key.blue);
        }

        private GeometryKey set(String text, boolean shadow, int style, int red, int green, int blue) {
            this.text = text;
            this.shadow = shadow;
            this.style = style;
            this.red = red;
            this.green = green;
            this.blue = blue;
            return this;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof GeometryKey key)) return false;
            return this.shadow == key.shadow
                    && this.style == key.style
                    && this.red == key.red
                    && this.green == key.green
                    && this.blue == key.blue
                    && this.text.equals(key.text);
        }

        @Override
        public int hashCode() {
            int hash = this.text.hashCode();
            hash = 31 * hash + Boolean.hashCode(this.shadow);
            hash = 31 * hash + this.style;
            hash = 31 * hash + this.red;
            hash = 31 * hash + this.green;
            return 31 * hash + this.blue;
        }
    }

    private static final class Segment {
        private final Identifier texture;
        private final int[] vertices;
        private int verticesAlpha;
        private int lastAlpha;
        private VertexBuffer buffer;
        private int bufferAlpha;

        private Segment(Identifier texture, int[] vertices, int alpha) {
            this.texture = texture;
            this.vertices = vertices;
            this.verticesAlpha = alpha;
            this.lastAlpha = alpha;
        }

        private void setAlpha(int alpha) {
            if (alpha == this.verticesAlpha) return;
            for (int i = COLOR_INDEX; i < this.vertices.length; i += STRIDE) {
                this.vertices[i] = this.vertices[i] & ~(0xFF << ALPHA_SHIFT) | alpha << ALPHA_SHIFT;
            }
            this.verticesAlpha = alpha;
        }
    }

    private record Geometry(Segment[] segments, float advance, int style, float red, float green, float blue) {}
}
