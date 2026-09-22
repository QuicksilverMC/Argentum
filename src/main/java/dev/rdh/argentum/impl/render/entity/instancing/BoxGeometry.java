package dev.rdh.argentum.impl.render.entity.instancing;

import dev.rdh.argentum.impl.render.instancing.BoxTemplate;
import dev.rdh.argentum.impl.render.instancing.InstancedGeometryBuffer;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

/**
 * One unit cube shared by every box in the game. The instance matrix maps it onto the box, and the instance carries
 * the texture parameters the shader needs to rebuild the same uv layout {@link net.minecraft.client.render.model.Box}
 * bakes.
 * <p>
 * A mirrored box gets a negative x scale from its matrix, which reverses winding, so it draws from a second copy
 * with each quad's vertices reversed - exactly what vanilla does by flipping the polygons.
 */
final class BoxGeometry extends InstanceGeometry {
    private static final int VERTEX_COUNT = 24;

    private static final float[][] FACE_NORMALS = {
            {1.0F, 0.0F, 0.0F},
            {-1.0F, 0.0F, 0.0F},
            {0.0F, -1.0F, 0.0F},
            {0.0F, 1.0F, 0.0F},
            {0.0F, 0.0F, -1.0F},
            {0.0F, 0.0F, 1.0F},
    };

    private final InstancedGeometryBuffer buffers;

    BoxGeometry(boolean mirrored) {
        this.buffers = new InstancedGeometryBuffer(build(mirrored),
                InstancedVertexFormats.ENTITY_VERTEX, InstancedVertexFormats.ENTITY_INSTANCE);
    }

    private static FloatBuffer build(boolean mirrored) {
        // the entity vertex format, filled differently: position is the 0/1 corner selector, the texture
        // coordinate goes unused, and the colour carries the coefficients that rebuild this vertex's uv.
        // Attribute locations are positional, so a format of its own would shift every instance attribute.
        FloatBuffer vertices = BufferUtils.createFloatBuffer(VERTEX_COUNT * 12);
        for (int face = 0; face < 6; face++) {
            float[] normal = FACE_NORMALS[face];
            for (int vertex = 0; vertex < 4; vertex++) {
                int corner = mirrored ? 3 - vertex : vertex;
                int index = BoxTemplate.FACE_CORNERS[face][corner];
                int uSide = corner == 0 || corner == 3 ? 1 : 0;
                int vSide = corner <= 1 ? 0 : 1;
                vertices.put(BoxTemplate.cornerHighX(index) ? 1.0F : 0.0F)
                        .put(BoxTemplate.cornerHighY(index) ? 1.0F : 0.0F)
                        .put(BoxTemplate.cornerHighZ(index) ? 1.0F : 0.0F)
                        .put(0.0F).put(0.0F)
                        .put(normal[0]).put(normal[1]).put(normal[2])
                        .put(BoxTemplate.FACE_U[face][uSide]).put(BoxTemplate.FACE_U_Z[face][uSide])
                        .put(BoxTemplate.FACE_V[face][vSide]).put(BoxTemplate.FACE_V_Z[face][vSide]);
            }
        }
        return vertices.flip();
    }

    @Override
    boolean usesBoxInstancing() {
        return true;
    }

    @Override
    public void render(CommandList commandList, Instances instances) {
        this.buffers.draw(commandList, instances.upload(), VERTEX_COUNT, instances.count());
    }

    @Override
    public void delete(CommandList commandList) {
        this.buffers.delete(commandList);
    }
}
