package dev.rdh.argentum.impl.render;

import org.lwjgl.opengl.GL15C;

public final class BufferUpload {
    private static final int CHUNK = 8000;

    public static void stream(int target, long address, long size, int usage) {
        GL15C.nglBufferData(target, size, 0L, usage);
        for (long offset = 0; offset < size; offset += CHUNK) {
            GL15C.nglBufferSubData(target, offset, Math.min(CHUNK, size - offset), address + offset);
        }
    }
}
