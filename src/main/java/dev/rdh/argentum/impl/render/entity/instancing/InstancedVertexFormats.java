package dev.rdh.argentum.impl.render.entity.instancing;

import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;

public final class InstancedVertexFormats {

    public static final GlVertexFormat ENTITY_VERTEX = GlVertexFormat.builder(12 * Float.BYTES)
            .addElement("aPosition", 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement("aTexCoord", 3 * Float.BYTES, GlVertexAttributeFormat.FLOAT, 2, false, false)
            .addElement("aNormal", 5 * Float.BYTES, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement("aVertexColor", 8 * Float.BYTES, GlVertexAttributeFormat.FLOAT, 4, false, false)
            .build();
    public static final GlVertexFormat ENTITY_INSTANCE = GlVertexFormat.builder(19 * Integer.BYTES)
            .addElement("aModel0", 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement("aModel1", 3 * Float.BYTES, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement("aModel2", 6 * Float.BYTES, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement("aModel3", 9 * Float.BYTES, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement("aEffectTime", 12 * Float.BYTES, GlVertexAttributeFormat.FLOAT, 1, false, false)
            .addElement("aLightCoord", 13 * Float.BYTES, GlVertexAttributeFormat.UNSIGNED_BYTE, 3, false, false)
            .addElement("aColor", 14 * Float.BYTES, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
            .addElement("aOverlay", 15 * Float.BYTES, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
            .addElement("aBoxTexture", 16 * Float.BYTES, GlVertexAttributeFormat.UNSIGNED_SHORT, 4, false, false)
            .addElement("aBoxSize", 18 * Float.BYTES, GlVertexAttributeFormat.UNSIGNED_BYTE, 3, false, false)
            .build();

    private InstancedVertexFormats() {
    }
}
