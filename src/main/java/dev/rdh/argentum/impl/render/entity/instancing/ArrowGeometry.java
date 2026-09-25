package dev.rdh.argentum.impl.render.entity.instancing;

import dev.rdh.argentum.impl.render.instancing.InstancedGeometryBuffer;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

final class ArrowGeometry extends InstanceGeometry {
    private final InstancedGeometryBuffer buffers;

    ArrowGeometry() {
        FloatBuffer vertices = BufferUtils.createFloatBuffer(24 * 12);
        quad(vertices, 1.0F, 0.0F, 0.0F, 0.0F, 0.15625F, 0.15625F, 0.3125F, -7, -2, -2, -7, -2, 2, -7, 2, 2, -7, 2, -2);
        quad(vertices, -1.0F, 0.0F, 0.0F, 0.0F, 0.15625F, 0.15625F, 0.3125F, -7, 2, -2, -7, 2, 2, -7, -2, 2, -7, -2, -2);
        for (int i = 1; i <= 4; i++) {
            float angle = i * (float)Math.PI * 0.5F;
            float sin = (float)Math.sin(angle);
            float cos = (float)Math.cos(angle);
            side(vertices, sin, cos);
        }
        vertices.flip();
        this.buffers = new InstancedGeometryBuffer(vertices, InstancedVertexFormats.ENTITY_VERTEX, InstancedVertexFormats.ENTITY_INSTANCE);
    }

    @Override
    public void render(CommandList commandList, Instances instances) {
        this.buffers.draw(commandList, instances.upload(), 24, instances.count());
    }

    @Override
    public void delete(CommandList commandList) {
        this.buffers.delete(commandList);
    }

    private static void side(FloatBuffer vertices, float sin, float cos) {
        vertex(vertices, -8, -2 * cos, -2 * sin, 0.0F, 0.0F, 0.0F, -sin, cos);
        vertex(vertices, 8, -2 * cos, -2 * sin, 0.5F, 0.0F, 0.0F, -sin, cos);
        vertex(vertices, 8, 2 * cos, 2 * sin, 0.5F, 0.15625F, 0.0F, -sin, cos);
        vertex(vertices, -8, 2 * cos, 2 * sin, 0.0F, 0.15625F, 0.0F, -sin, cos);
    }

    private static void quad(FloatBuffer vertices, float nx, float ny, float nz, float minU, float maxU, float minV, float maxV, float... positions) {
        for (int i = 0; i < 4; i++) {
            vertex(vertices, positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2], i == 0 || i == 3 ? minU : maxU, i < 2 ? minV : maxV, nx, ny, nz);
        }
    }

    private static void vertex(FloatBuffer vertices, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        vertices.put(x).put(y).put(z).put(u).put(v).put(nx * 0.05625F).put(ny * 0.05625F).put(nz * 0.05625F)
                .put(1.0F).put(1.0F).put(1.0F).put(1.0F);
    }

}
