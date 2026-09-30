package dev.rdh.argentum.impl.render.texture;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public final class AtlasUploads {
    private static final int RECT_FIELDS = 8;
    private static ByteBuffer pixels = MemoryUtil.memAlloc(1 << 20);
    private static int[] rects = new int[8 * 64]; // level, x, y, width, height, format, type, byte offset
    private static int count, buffer;
    private static boolean collecting;

    public static void begin() {
        collecting = true;
    }

    public static boolean collect(int target, int level, int x, int y, int width, int height, int format, int type, IntBuffer data) {
        if (!collecting || target != GL11C.GL_TEXTURE_2D) {
            return false;
        }
        int bytes = data.remaining() * 4;
        if (pixels.remaining() < bytes) {
            pixels = MemoryUtil.memRealloc(pixels, Math.max(pixels.capacity() * 2, pixels.position() + bytes));
        }
        if (rects.length < (count + 1) * RECT_FIELDS) {
            rects = java.util.Arrays.copyOf(rects, rects.length * 2);
        }
        int i = count++ * RECT_FIELDS;
        rects[i] = level;
        rects[i + 1] = x;
        rects[i + 2] = y;
        rects[i + 3] = width;
        rects[i + 4] = height;
        rects[i + 5] = format;
        rects[i + 6] = type;
        rects[i + 7] = pixels.position();
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), MemoryUtil.memAddress(pixels), bytes);
        pixels.position(pixels.position() + bytes);
        return true;
    }

    public static void flush() {
        collecting = false;
        if (count == 0) {
            return;
        }
        if (buffer == 0) {
            buffer = GL15C.glGenBuffers();
        }
        GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, buffer);
        pixels.flip();
        GL15C.glBufferData(GL21C.GL_PIXEL_UNPACK_BUFFER, pixels, GL15C.GL_STREAM_DRAW);
        for (int i = 0; i < count * RECT_FIELDS; i += RECT_FIELDS) {
            GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, rects[i], rects[i + 1], rects[i + 2], rects[i + 3], rects[i + 4],
                    rects[i + 5], rects[i + 6], rects[i + 7]);
        }
        GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
        pixels.clear();
        count = 0;
    }
}
