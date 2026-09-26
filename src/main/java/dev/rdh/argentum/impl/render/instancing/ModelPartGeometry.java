package dev.rdh.argentum.impl.render.instancing;

import dev.rdh.argentum.mixin.features.model.instancing.BoxAccessor;
import dev.rdh.argentum.mixin.features.model.instancing.PolygonAccessor;
import net.minecraft.client.render.model.Box;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.Polygon;
import net.minecraft.client.render.model.Vertex;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

public record ModelPartGeometry(FloatBuffer vertices, int vertexCount) {
    public static ModelPartGeometry create(ModelPart part, float scale) {
        int vertexCount = 0;
        for (Box box : part.boxes) {
            for (Polygon polygon : ((BoxAccessor)box).celeritas$getFaces()) {
                vertexCount += polygon.vertices.length;
            }
        }
        FloatBuffer vertices = BufferUtils.createFloatBuffer(vertexCount * 12);
        for (Box box : part.boxes) {
            for (Polygon polygon : ((BoxAccessor)box).celeritas$getFaces()) {
                putPolygon(vertices, polygon, scale);
            }
        }
        return new ModelPartGeometry(vertices.flip(), vertexCount);
    }

    private static void putPolygon(FloatBuffer output, Polygon polygon, float scale) {
        Vertex[] vertices = polygon.vertices;
        float ax = (float)(vertices[0].pos.x - vertices[1].pos.x);
        float ay = (float)(vertices[0].pos.y - vertices[1].pos.y);
        float az = (float)(vertices[0].pos.z - vertices[1].pos.z);
        float bx = (float)(vertices[2].pos.x - vertices[1].pos.x);
        float by = (float)(vertices[2].pos.y - vertices[1].pos.y);
        float bz = (float)(vertices[2].pos.z - vertices[1].pos.z);
        float nx = by * az - bz * ay;
        float ny = bz * ax - bx * az;
        float nz = bx * ay - by * ax;
        float inverseLength = 1.0F / (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (((PolygonAccessor)polygon).celeritas$isNormalFlipped()) {
            inverseLength = -inverseLength;
        }
        nx *= inverseLength;
        ny *= inverseLength;
        nz *= inverseLength;
        for (Vertex vertex : vertices) {
            output.put((float)vertex.pos.x * scale).put((float)vertex.pos.y * scale)
                    .put((float)vertex.pos.z * scale).put(vertex.u).put(vertex.v)
                    .put(nx).put(ny).put(nz).put(1.0F).put(1.0F).put(1.0F).put(1.0F);
        }
    }
}
