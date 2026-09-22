package dev.rdh.argentum.impl.render.entity.instancing;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import dev.rdh.argentum.impl.render.instancing.InstancedGeometryBuffer;
import dev.rdh.argentum.impl.render.instancing.InstanceDataBuffer;
import dev.rdh.argentum.impl.render.instancing.BoxTemplate;
import dev.rdh.argentum.impl.render.instancing.ModelPartGeometry;

import net.minecraft.client.render.model.ModelPart;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.joml.Matrix4f;
import org.joml.Vector4fc;

public final class ModelGeometry {
    private final ObjectArrayList<PartGeometry> parts = new ObjectArrayList<>();
    private int partIndex;

    public void begin() {
        this.partIndex = 0;
    }

    public InstanceGeometry getGeometry(ModelPart part, float scale) {
        if (this.partIndex < this.parts.size()) {
            PartGeometry geometry = this.parts.get(this.partIndex++);
            if (geometry.part() == part) {
                return geometry;
            }
        } else {
            this.partIndex++;
        }
        for (PartGeometry geometry : this.parts) {
            if (geometry.part() == part) {
                return geometry;
            }
        }
        PartGeometry geometry = new PartGeometry(part, scale);
        this.parts.add(geometry);
        return geometry;
    }

    void delete(CommandList commandList) {
        this.parts.forEach(geometry -> geometry.delete(commandList));
        this.parts.clear();
    }
}

final class PartGeometry extends InstanceGeometry {
    private final ModelPart part;
    private final InstancedGeometryBuffer buffers;
    private final int vertexCount;

    PartGeometry(ModelPart part, float scale) {
        this.part = part;
        ModelPartGeometry geometry = ModelPartGeometry.create(part, scale);
        this.vertexCount = geometry.vertexCount();
        this.buffers = new InstancedGeometryBuffer(geometry.vertices(), InstancedVertexFormats.ENTITY_VERTEX, InstancedVertexFormats.ENTITY_INSTANCE);
    }

    ModelPart part() {
        return this.part;
    }

    @Override
    public void render(CommandList commandList, Instances instances) {
        this.buffers.draw(commandList, instances.upload(), this.vertexCount, instances.count());
    }

    @Override
    public void delete(CommandList commandList) {
        this.buffers.delete(commandList);
    }

}

final class Instances extends InstanceDataBuffer {
    private static final int INTS = 19;
    private static final int TRANSLATION_OFFSET = 9;

    Instances() {
        super(INTS, TRANSLATION_OFFSET, 64);
    }

    void add(Matrix4f matrix, float u, float v, int layer, Vector4fc color, float effectTime, Vector4fc overlayColor,
            BoxTemplate box) {
        int i = this.appendOffset();
        int[] data = this.data();
        data[i++] = Float.floatToRawIntBits(matrix.m00());
        data[i++] = Float.floatToRawIntBits(matrix.m01());
        data[i++] = Float.floatToRawIntBits(matrix.m02());
        data[i++] = Float.floatToRawIntBits(matrix.m10());
        data[i++] = Float.floatToRawIntBits(matrix.m11());
        data[i++] = Float.floatToRawIntBits(matrix.m12());
        data[i++] = Float.floatToRawIntBits(matrix.m20());
        data[i++] = Float.floatToRawIntBits(matrix.m21());
        data[i++] = Float.floatToRawIntBits(matrix.m22());
        data[i++] = Float.floatToRawIntBits(matrix.m30());
        data[i++] = Float.floatToRawIntBits(matrix.m31());
        data[i++] = Float.floatToRawIntBits(matrix.m32());
        data[i++] = Float.floatToRawIntBits(effectTime);
        data[i++] = (int)u & 0xFF | ((int)v & 0xFF) << 8 | (layer & 0xFF) << 16;
        data[i++] = pack(color);
        data[i++] = pack(overlayColor);
        if (box == null) {
            data[i++] = 0;
            data[i++] = 0;
            data[i] = 0;
        } else {
            data[i++] = box.textureU() & 0xFFFF | (box.textureV() & 0xFFFF) << 16;
            data[i++] = (int)box.textureWidth() & 0xFFFF | ((int)box.textureHeight() & 0xFFFF) << 16;
            data[i] = box.sizeX() & 0xFF | (box.sizeY() & 0xFF) << 8 | (box.sizeZ() & 0xFF) << 16;
        }
        this.finishInstance();
    }

    private static int pack(Vector4fc color) {
        return toByte(color.x()) | toByte(color.y()) << 8 | toByte(color.z()) << 16 | toByte(color.w()) << 24;
    }

    private static int toByte(float value) {
        return Math.clamp((int)(value * 255.0F + 0.5F), 0, 255);
    }
}
