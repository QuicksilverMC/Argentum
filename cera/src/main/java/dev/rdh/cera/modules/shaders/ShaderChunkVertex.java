package dev.rdh.cera.modules.shaders;

import com.ventooth.swansong.shader.ShaderEntityData;
import com.ventooth.swansong.tessellator.BlockVertexAttribs;
import com.ventooth.swansong.tessellator.ShaderVert;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

final class ShaderChunkVertex implements ChunkVertexType {
    static final ShaderChunkVertex INSTANCE = new ShaderChunkVertex();

    private static final int STRIDE = BlockVertexAttribs.STRIDE;
    private static final int COLOR = 12;
    private static final int TEXTURE = 16;
    private static final int LIGHT = 24;
    private static final int NORMAL = 28;
    private static final int ENTITY = 32;
    private static final int MID_TEXTURE = 40;
    private static final int TANGENT = 48;
    private static final int SECTION_SIZE = 16;

    private static final GlVertexFormat FORMAT = GlVertexFormat.builder(STRIDE)
            .addElement("position", 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .build();

    private ShaderChunkVertex() {
    }

    static void bindAttributes() {
        GL11.glVertexPointer(3, GL11.GL_FLOAT, STRIDE, 0L);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, STRIDE, COLOR);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GL13.glClientActiveTexture(GL13.GL_TEXTURE1);
        GL11.glTexCoordPointer(2, GL11.GL_SHORT, STRIDE, LIGHT);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, STRIDE, TEXTURE);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        BlockVertexAttribs.enableVbo();
    }

    @Override
    public float getPositionScale() {
        return 1F;
    }

    @Override
    public float getPositionOffset() {
        return 0F;
    }

    @Override
    public float getTextureScale() {
        return 1F;
    }

    @Override
    public GlVertexFormat getVertexFormat() {
        return FORMAT;
    }

    @Override
    public ChunkVertexEncoder createEncoder() {
        return new Encoder();
    }

    private static final class Encoder implements ChunkVertexEncoder {
        private final ShaderVert[] quad = {new ShaderVert(), new ShaderVert(), new ShaderVert(), new ShaderVert()};
        private int vertexCount;

        @Override
        public boolean supportsBilinearCorrection() {
            return false;
        }

        @Override
        public long write(long ptr, Material material, Vertex vertex, int sectionIndex) {
            var vert = this.quad[this.vertexCount++];
            vert.positionX = vertex.x + LocalSectionIndex.unpackX(sectionIndex) * SECTION_SIZE;
            vert.positionY = vertex.y + LocalSectionIndex.unpackY(sectionIndex) * SECTION_SIZE;
            vert.positionZ = vertex.z + LocalSectionIndex.unpackZ(sectionIndex) * SECTION_SIZE;
            vert.textureU = vertex.u;
            vert.textureV = vertex.v;

            MemoryUtil.memPutFloat(ptr, vert.positionX);
            MemoryUtil.memPutFloat(ptr + 4, vert.positionY);
            MemoryUtil.memPutFloat(ptr + 8, vert.positionZ);
            MemoryUtil.memPutInt(ptr + COLOR, vertex.color);
            MemoryUtil.memPutFloat(ptr + TEXTURE, vert.textureU);
            MemoryUtil.memPutFloat(ptr + TEXTURE + 4, vert.textureV);
            MemoryUtil.memPutInt(ptr + LIGHT, vertex.light);

            if (this.vertexCount == 4) {
                this.vertexCount = 0;
                this.completeQuad(ptr - 3L * STRIDE);
            }
            return ptr + STRIDE;
        }

        private void completeQuad(long first) {
            ShaderVert.completeQuad(this.quad[0], this.quad[1], this.quad[2], this.quad[3]);
            long entity = ShaderEntityData.get().getPackedEntityData();
            for (int i = 0; i < 4; i++) {
                var vert = this.quad[i];
                long ptr = first + (long)i * STRIDE;
                MemoryUtil.memPutInt(ptr + NORMAL, packSigned(vert.normalX, vert.normalY, vert.normalZ, 0F));
                MemoryUtil.memPutInt(ptr + ENTITY, ShaderEntityData.unpackEntityData(entity));
                MemoryUtil.memPutInt(ptr + ENTITY + 4, ShaderEntityData.unpackEntityData2(entity));
                MemoryUtil.memPutFloat(ptr + MID_TEXTURE, vert.midTextureU);
                MemoryUtil.memPutFloat(ptr + MID_TEXTURE + 4, vert.midTextureV);
                MemoryUtil.memPutInt(ptr + TANGENT, packSigned(vert.tangentX, vert.tangentY, vert.tangentZ, vert.tangentW));
            }
        }

        private static int packSigned(float x, float y, float z, float w) {
            return ((int)(x * 127F) & 0xFF)
                    | ((int)(y * 127F) & 0xFF) << 8
                    | ((int)(z * 127F) & 0xFF) << 16
                    | ((int)(w * 127F) & 0xFF) << 24;
        }
    }
}
