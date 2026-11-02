package dev.rdh.argentum.impl.render.entity.instancing;

import org.embeddedt.embeddium.impl.gl.buffer.GlBufferTarget;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

final class StandardVertexStream {
    private static final int STRIDE = 36;
    private static final int SOURCE_STRIDE = 12 * Float.BYTES;
    private static final float EYE_LIGHT = 61680.0F;
    private static final float FULL_BLOCK_LIGHT = 240.0F;

    private ByteBuffer data = BufferUtils.createByteBuffer(STRIDE << 16);
    private GlMutableBuffer buffer;
    private int count;

    void begin() {
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL13.glClientActiveTexture(GL13.GL_TEXTURE1);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
    }

    void reset() {
        this.count = 0;
    }

    int count() {
        return this.count;
    }

    void upload(CommandList commandList) {
        if (this.buffer == null) {
            this.buffer = commandList.createMutableBuffer();
        }
        commandList.unbindVertexArray();
        commandList.uploadData(this.buffer, MemoryUtil.memAddress(this.data), (long)this.count * STRIDE, GlBufferUsage.STREAM_DRAW);
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, STRIDE, 20L);
        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, STRIDE, 12L);
        GL13.glClientActiveTexture(GL13.GL_TEXTURE1);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, STRIDE, 0L);
        GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, STRIDE, 28L);
        GL11.glNormalPointer(GL11.GL_BYTE, STRIDE, 32L);
    }

    void draw(int first, int vertices) {
        GL11.glDrawArrays(GL11.GL_QUADS, first, vertices);
    }

    void end(CommandList commandList) {
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        commandList.bindBuffer(GlBufferTarget.ARRAY_BUFFER, null);
    }

    void delete(CommandList commandList) {
        if (this.buffer != null) {
            commandList.deleteBuffer(this.buffer);
            this.buffer = null;
        }
    }

    void put(InstanceGeometry geometry, Instances instances, InstanceRenderPass pass, int layer, Matrix4fc itemGlint) {
        var source = geometry.buffers().vertices();
        long sourceStart = MemoryUtil.memAddress(source);
        int sourceCount = source.remaining() / 12;
        boolean box = geometry.usesBoxInstancing();
        int[] data = instances.raw();
        long out = this.reserve(sourceCount * instances.count());
        float glintSin = (float)Math.sin(Math.toRadians(30.0F - layer * 60.0F));
        float glintCos = (float)Math.cos(Math.toRadians(30.0F - layer * 60.0F));

        for (int i = 0, o = 0; i < instances.count(); i++, o += Instances.INTS) {
            float m00 = Float.intBitsToFloat(data[o]);
            float m01 = Float.intBitsToFloat(data[o + 1]);
            float m02 = Float.intBitsToFloat(data[o + 2]);
            float m10 = Float.intBitsToFloat(data[o + 3]);
            float m11 = Float.intBitsToFloat(data[o + 4]);
            float m12 = Float.intBitsToFloat(data[o + 5]);
            float m20 = Float.intBitsToFloat(data[o + 6]);
            float m21 = Float.intBitsToFloat(data[o + 7]);
            float m22 = Float.intBitsToFloat(data[o + 8]);
            float m30 = Float.intBitsToFloat(data[o + 9]);
            float m31 = Float.intBitsToFloat(data[o + 10]);
            float m32 = Float.intBitsToFloat(data[o + 11]);
            float time = Float.intBitsToFloat(data[o + 12]);
            int light = data[o + 13];
            int color = data[o + 14];

            float lightU = pass == InstanceRenderPass.EMISSIVE ? EYE_LIGHT
                    : pass == InstanceRenderPass.EMISSIVE_REPLACE ? FULL_BLOCK_LIGHT : light & 0xFF;
            float lightV = pass == InstanceRenderPass.EMISSIVE ? 0.0F : light >> 8 & 0xFF;

            float uu = 1.0F, uv = 0.0F, ut = 0.0F, vu = 0.0F, vv = 1.0F, vt = 0.0F;
            if (pass == InstanceRenderPass.GLINT) {
                float scroll = time * (0.001F + layer * 0.003F) * 20.0F;
                uu = glintCos / 3.0F;
                uv = -glintSin / 3.0F;
                ut = uv * scroll;
                vu = glintSin / 3.0F;
                vv = glintCos / 3.0F;
                vt = vv * scroll;
            } else if (pass == InstanceRenderPass.ITEM_GLINT) {
                uu = itemGlint.m00();
                uv = itemGlint.m10();
                ut = itemGlint.m30();
                vu = itemGlint.m01();
                vv = itemGlint.m11();
                vt = itemGlint.m31();
            } else if (pass.chargePass == 1) {
                ut = time * 0.01F;
                vt = time * 0.01F;
            } else if (pass.chargePass == 2) {
                ut = (float)Math.cos(time * 0.02F) * 3.0F;
                vt = time * 0.01F;
            }

            float n00 = m00, n01 = m01, n02 = m02, n10 = m10, n11 = m11, n12 = m12, n20 = m20, n21 = m21, n22 = m22;
            if (!box && !similarity(m00, m01, m02, m10, m11, m12, m20, m21, m22)) {
                n00 = m11 * m22 - m12 * m21;
                n01 = m12 * m20 - m10 * m22;
                n02 = m10 * m21 - m11 * m20;
                n10 = m21 * m02 - m22 * m01;
                n11 = m22 * m00 - m20 * m02;
                n12 = m20 * m01 - m21 * m00;
                n20 = m01 * m12 - m02 * m11;
                n21 = m02 * m10 - m00 * m12;
                n22 = m00 * m11 - m01 * m10;
                float determinant = m00 * n00 + m01 * n01 + m02 * n02;
                n00 /= determinant;
                n01 /= determinant;
                n02 /= determinant;
                n10 /= determinant;
                n11 /= determinant;
                n12 /= determinant;
                n20 /= determinant;
                n21 /= determinant;
                n22 /= determinant;
            }

            int boxU = data[o + 16] & 0xFFFF;
            int boxV = data[o + 16] >>> 16;
            float boxWidth = Math.max(data[o + 17] & 0xFFFF, 1);
            float boxHeight = Math.max(data[o + 17] >>> 16, 1);
            int boxSizeX = data[o + 18] & 0xFF;
            int boxSizeY = data[o + 18] >> 8 & 0xFF;
            int boxSizeZ = data[o + 18] >> 16 & 0xFF;

            long in = sourceStart;
            for (int vertex = 0; vertex < sourceCount; vertex++, in += SOURCE_STRIDE, out += STRIDE) {
                float x = MemoryUtil.memGetFloat(in);
                float y = MemoryUtil.memGetFloat(in + 4);
                float z = MemoryUtil.memGetFloat(in + 8);
                float nx = MemoryUtil.memGetFloat(in + 20);
                float ny = MemoryUtil.memGetFloat(in + 24);
                float nz = MemoryUtil.memGetFloat(in + 28);
                float r = MemoryUtil.memGetFloat(in + 32);
                float g = MemoryUtil.memGetFloat(in + 36);
                float b = MemoryUtil.memGetFloat(in + 40);
                float a = MemoryUtil.memGetFloat(in + 44);

                float u, v;
                int vertexColor;
                if (box) {
                    u = (boxU + r * boxSizeX + g * boxSizeZ) / boxWidth;
                    v = (boxV + b * boxSizeY + a * boxSizeZ) / boxHeight;
                    vertexColor = color;
                } else {
                    u = MemoryUtil.memGetFloat(in + 12);
                    v = MemoryUtil.memGetFloat(in + 16);
                    vertexColor = scale(color, r, g, b, a);
                }

                MemoryUtil.memPutFloat(out, m00 * x + m10 * y + m20 * z + m30);
                MemoryUtil.memPutFloat(out + 4, m01 * x + m11 * y + m21 * z + m31);
                MemoryUtil.memPutFloat(out + 8, m02 * x + m12 * y + m22 * z + m32);
                MemoryUtil.memPutFloat(out + 12, uu * u + uv * v + ut);
                MemoryUtil.memPutFloat(out + 16, vu * u + vv * v + vt);
                MemoryUtil.memPutFloat(out + 20, lightU);
                MemoryUtil.memPutFloat(out + 24, lightV);
                MemoryUtil.memPutInt(out + 28, vertexColor);

                float tx = n00 * nx + n10 * ny + n20 * nz;
                float ty = n01 * nx + n11 * ny + n21 * nz;
                float tz = n02 * nx + n12 * ny + n22 * nz;
                float length = 127.0F / (float)Math.sqrt(tx * tx + ty * ty + tz * tz);
                MemoryUtil.memPutInt(out + 32, (int)(tx * length) & 0xFF | ((int)(ty * length) & 0xFF) << 8 | ((int)(tz * length) & 0xFF) << 16);
            }
        }
        this.count += sourceCount * instances.count();
    }

    private long reserve(int vertices) {
        int used = this.count * STRIDE;
        int needed = used + vertices * STRIDE;
        if (needed > this.data.capacity()) {
            ByteBuffer grown = BufferUtils.createByteBuffer(Math.max(needed, this.data.capacity() * 2));
            grown.put(0, this.data, 0, used);
            this.data = grown;
        }
        return MemoryUtil.memAddress(this.data) + used;
    }

    private static int scale(int color, float r, float g, float b, float a) {
        return (int)((color & 0xFF) * r + 0.5F)
                | (int)((color >> 8 & 0xFF) * g + 0.5F) << 8
                | (int)((color >> 16 & 0xFF) * b + 0.5F) << 16
                | (int)((color >>> 24) * a + 0.5F) << 24;
    }

    private static boolean similarity(float m00, float m01, float m02, float m10, float m11, float m12, float m20, float m21, float m22) {
        float x = m00 * m00 + m01 * m01 + m02 * m02;
        float y = m10 * m10 + m11 * m11 + m12 * m12;
        float z = m20 * m20 + m21 * m21 + m22 * m22;
        float tolerance = 1.0E-4F * Math.max(x, Math.max(y, z));
        return Math.abs(x - y) <= tolerance && Math.abs(y - z) <= tolerance
                && Math.abs(m00 * m10 + m01 * m11 + m02 * m12) <= tolerance
                && Math.abs(m10 * m20 + m11 * m21 + m12 * m22) <= tolerance
                && Math.abs(m00 * m20 + m01 * m21 + m02 * m22) <= tolerance;
    }
}
