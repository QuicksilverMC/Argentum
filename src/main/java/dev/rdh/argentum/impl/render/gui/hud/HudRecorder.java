package dev.rdh.argentum.impl.render.gui.hud;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;

public final class HudRecorder {
    public static final int LAYER_BACKGROUND = 0;
    public static final int LAYER_CONTENT = 1;
    public static final int LAYER_OVERLAY = 2;
    private static final int LAYERS = 3;

    public static final int MATERIAL_ALPHA = 0;
    public static final int MATERIAL_OPAQUE = 1;
    public static final int MATERIAL_PREMULTIPLIED = 2;
    private static final int MATERIALS = 3;

    private static final int MAX_TEXTURES = 8;
    private static final int BUCKETS = LAYERS * MATERIALS * (MAX_TEXTURES + 1);

    public static final int UNTEXTURED = 0;

    private static final int FLOATS_PER_QUAD = 9;
    private static final int INITIAL_QUADS = 1024;

    private final BufferBuilder buffer = new BufferBuilder(256 * 1024 / Integer.BYTES);
    private final BufferUploader uploader = new BufferUploader();

    private int[] keys = new int[INITIAL_QUADS];
    private int[] textures = new int[INITIAL_QUADS];
    private int[] colorsTop = new int[INITIAL_QUADS];
    private int[] colorsBottom = new int[INITIAL_QUADS];
    private float[] data = new float[INITIAL_QUADS * FLOATS_PER_QUAD];
    private int size;

    private final int[] textureRanks = new int[MAX_TEXTURES];
    private int rankCount;

    private final int[] bucketCounts = new int[BUCKETS];
    private final int[] bucketCursors = new int[BUCKETS];
    private int[] order = new int[INITIAL_QUADS];

    public boolean isEmpty() {
        return this.size == 0;
    }

    public void quad(int layer, int material, int texture,
            float x0, float y0, float x1, float y1,
            float u0, float v0, float u1, float v1,
            float z, int colorTop, int colorBottom) {
        int index = this.size;
        if (index == this.keys.length) this.grow();

        this.keys[index] = ((layer * MATERIALS + material) * (MAX_TEXTURES + 1)) + this.rankOf(texture);
        this.textures[index] = texture;
        this.colorsTop[index] = colorTop;
        this.colorsBottom[index] = colorBottom;

        int cursor = index * FLOATS_PER_QUAD;
        this.data[cursor] = x0;
        this.data[cursor + 1] = y0;
        this.data[cursor + 2] = x1;
        this.data[cursor + 3] = y1;
        this.data[cursor + 4] = u0;
        this.data[cursor + 5] = v0;
        this.data[cursor + 6] = u1;
        this.data[cursor + 7] = v1;
        this.data[cursor + 8] = z;

        this.size = index + 1;
    }

    public void fill(int layer, int left, int top, int right, int bottom, int color) {
        this.quad(layer, MATERIAL_ALPHA, UNTEXTURED,
                Math.min(left, right), Math.min(top, bottom),
                Math.max(left, right), Math.max(top, bottom),
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, color, color);
    }

    private int rankOf(int texture) {
        for (int i = 0; i < this.rankCount; i++) {
            if (this.textureRanks[i] == texture) return i;
        }
        if (this.rankCount == MAX_TEXTURES) return MAX_TEXTURES;
        this.textureRanks[this.rankCount] = texture;
        return this.rankCount++;
    }

    public void flush() {
        if (this.size == 0) return;

        try {
            this.sort();
            this.emit();
        } finally {
            this.size = 0;
            this.rankCount = 0;
        }
    }

    private void sort() {
        Arrays.fill(this.bucketCounts, 0);
        for (int i = 0; i < this.size; i++) this.bucketCounts[this.keys[i]]++;

        int running = 0;
        for (int bucket = 0; bucket < BUCKETS; bucket++) {
            this.bucketCursors[bucket] = running;
            running += this.bucketCounts[bucket];
        }

        if (this.order.length < this.size) this.order = new int[this.keys.length];
        for (int i = 0; i < this.size; i++) this.order[this.bucketCursors[this.keys[i]]++] = i;
    }

    private void emit() {
        GlStateManager.enableBlend();

        try {
            int runStart = 0;
            while (runStart < this.size) {
                int first = this.order[runStart];
                int texture = this.textures[first];
                int key = this.keys[first];

                int runEnd = runStart + 1;
                while (runEnd < this.size) {
                    int candidate = this.order[runEnd];
                    if (this.textures[candidate] != texture || this.keys[candidate] != key) break;
                    runEnd++;
                }

                this.draw(runStart, runEnd, texture, materialOf(key));
                runStart = runEnd;
            }
        } finally {
            GlStateManager.enableTexture();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableBlend();
        }
    }

    private static int materialOf(int key) {
        return (key / (MAX_TEXTURES + 1)) % MATERIALS;
    }

    private void draw(int from, int to, int texture, int material) {
        switch (material) {
            case MATERIAL_PREMULTIPLIED -> GlStateManager.blendFuncSeparate(1, 771, 1, 771);
            case MATERIAL_OPAQUE -> GlStateManager.blendFuncSeparate(1, 0, 1, 0);
            default -> GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        }

        if (texture == UNTEXTURED) {
            GlStateManager.disableTexture();
        } else {
            GlStateManager.enableTexture();
            GlStateManager.bindTexture(texture);
        }

        this.buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int position = from; position < to; position++) {
            int index = this.order[position];
            int cursor = index * FLOATS_PER_QUAD;
            float x0 = this.data[cursor];
            float y0 = this.data[cursor + 1];
            float x1 = this.data[cursor + 2];
            float y1 = this.data[cursor + 3];
            float u0 = this.data[cursor + 4];
            float v0 = this.data[cursor + 5];
            float u1 = this.data[cursor + 6];
            float v1 = this.data[cursor + 7];
            float z = this.data[cursor + 8];

            this.vertex(x0, y1, z, u0, v1, this.colorsBottom[index]);
            this.vertex(x1, y1, z, u1, v1, this.colorsBottom[index]);
            this.vertex(x1, y0, z, u1, v0, this.colorsTop[index]);
            this.vertex(x0, y0, z, u0, v0, this.colorsTop[index]);
        }

        this.buffer.end();
        this.uploader.end(this.buffer);
    }

    private void vertex(float x, float y, float z, float u, float v, int color) {
        this.buffer.vertex(x, y, z)
                .texture(u, v)
                .color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24)
                .nextVertex();
    }

    private void grow() {
        int grown = this.keys.length * 2;
        this.keys = Arrays.copyOf(this.keys, grown);
        this.textures = Arrays.copyOf(this.textures, grown);
        this.colorsTop = Arrays.copyOf(this.colorsTop, grown);
        this.colorsBottom = Arrays.copyOf(this.colorsBottom, grown);
        this.data = Arrays.copyOf(this.data, grown * FLOATS_PER_QUAD);
    }
}
