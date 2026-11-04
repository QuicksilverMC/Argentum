package dev.rdh.argentum.impl.render.entity;

import net.minecraft.client.render.platform.GlStateManager;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat4v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformInt;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderComponent;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBTextureFloat;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.terrain.fog.ArgentumFogService;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Map;

final class NameTagInstancing {
    private static final int TEXT_STRIDE = 6;
    private static final int QUADS_PER_ROW = 512;
    private static final int QUAD_TEXELS = 5;
    private static final int QUAD_ROWS = 128;
    private static final int TAGS_PER_ROW = 1024;
    private static final int TAG_TEXELS = 3;
    private static final int TAG_ROWS = 32;
    private static final int TAG_FLOATS = TAG_TEXELS * 4;
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(Float.BYTES)
            .addElement("aCorner", 0, GlVertexAttributeFormat.FLOAT, 1, false, false)
            .build();
    private static final GlVertexFormat INSTANCE_FORMAT = GlVertexFormat.builder(2 * Float.BYTES)
            .addElement("aRef", 0, GlVertexAttributeFormat.FLOAT, 2, false, false)
            .build();

    private final Map<ChunkShaderComponent.Factory<?>, GlProgram<NameTagShader>> programs = new Object2ObjectOpenHashMap<>();
    private final FloatBuffer quadUpload = BufferUtils.createFloatBuffer(QUADS_PER_ROW * QUAD_TEXELS * 4);
    private final float[] texelSizes = {1.0F / (QUADS_PER_ROW * QUAD_TEXELS), 1.0F / QUAD_ROWS,
            1.0F / (TAGS_PER_ROW * TAG_TEXELS), 1.0F / TAG_ROWS};
    private FloatBuffer tags;
    private IntBuffer refs = BufferUtils.createIntBuffer(4096);
    private boolean initialized;
    private boolean supported;
    private int quadTexture;
    private int tagTexture;
    private InstancedGeometryBuffer geometry;
    private int generation = 1;
    private int quadRow;
    private int quadColumn;
    private int tagCount;
    private GlProgram<NameTagShader> program;

    boolean available() {
        if (!this.initialized) {
            this.initialized = true;
            var capabilities = GL.getCapabilities();
            this.supported = capabilities.OpenGL20 && capabilities.GL_ARB_draw_instanced && capabilities.GL_ARB_instanced_arrays
                    && (capabilities.OpenGL30 || capabilities.GL_ARB_texture_float)
                    && GL11.glGetInteger(GL20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS) >= 2;
            if (this.supported) {
                this.tags = BufferUtils.createFloatBuffer(TAGS_PER_ROW * TAG_ROWS * TAG_FLOATS);
                this.quadTexture = createTexture(QUADS_PER_ROW * QUAD_TEXELS, QUAD_ROWS);
                this.tagTexture = createTexture(TAGS_PER_ROW * TAG_TEXELS, TAG_ROWS);
            } else {
                Argentum.LOGGER.warn("Instanced name tags disabled: required OpenGL features are missing");
            }
        }
        return this.supported;
    }

    void beginFrame() {
        if (this.quadRow == QUAD_ROWS) {
            this.generation++;
            this.quadRow = 0;
            this.quadColumn = 0;
        }
    }

    int quads(NameTagBatch.CachedQuads cached, int[] vertices, int length) {
        if (cached.generation == this.generation) return cached.base;
        if (cached.rejected) return -1;

        int vertexCount = length / TEXT_STRIDE;
        int count = vertexCount / 4;
        if (count == 0 || count * 4 != vertexCount || count > QUADS_PER_ROW) {
            cached.rejected = true;
            return -1;
        }
        if (this.quadColumn + count > QUADS_PER_ROW) {
            this.quadRow++;
            this.quadColumn = 0;
        }
        if (this.quadRow == QUAD_ROWS) return -1;

        FloatBuffer upload = this.quadUpload.clear();
        for (int quad = 0; quad < count; quad++) {
            int first = quad * 4 * TEXT_STRIDE;
            int color = vertices[first + 5];
            for (int corner = 0; corner < 4; corner++) {
                int vertex = first + corner * TEXT_STRIDE;
                if (vertices[vertex + 5] != color) {
                    cached.rejected = true;
                    return -1;
                }
                upload.put(Float.intBitsToFloat(vertices[vertex])).put(Float.intBitsToFloat(vertices[vertex + 1]))
                        .put(Float.intBitsToFloat(vertices[vertex + 3])).put(Float.intBitsToFloat(vertices[vertex + 4]));
            }
            int red = LITTLE_ENDIAN ? color : color >>> 24;
            int green = LITTLE_ENDIAN ? color >>> 8 : color >>> 16;
            int blue = LITTLE_ENDIAN ? color >>> 16 : color >>> 8;
            upload.put((red & 0xFF) / 255.0F).put((green & 0xFF) / 255.0F).put((blue & 0xFF) / 255.0F).put(1.0F);
        }
        upload.flip();
        upload(this.quadTexture, this.quadColumn * QUAD_TEXELS, this.quadRow, count * QUAD_TEXELS, 1, upload);

        cached.generation = this.generation;
        cached.base = this.quadRow * QUADS_PER_ROW + this.quadColumn;
        this.quadColumn += count;
        return cached.base;
    }

    int tag(Matrix4fc matrix, float x, float y, float lightU, float lightV, int alpha) {
        if (this.tagCount == TAGS_PER_ROW * TAG_ROWS) return -1;
        float m00 = matrix.m00(), m01 = matrix.m01(), m02 = matrix.m02();
        float m10 = matrix.m10(), m11 = matrix.m11(), m12 = matrix.m12();
        int offset = this.tagCount * TAG_FLOATS;
        this.tags.put(offset, m00).put(offset + 1, m10).put(offset + 2, matrix.m30() + m00 * x + m10 * y).put(offset + 3, lightU)
                .put(offset + 4, m01).put(offset + 5, m11).put(offset + 6, matrix.m31() + m01 * x + m11 * y).put(offset + 7, lightV)
                .put(offset + 8, m02).put(offset + 9, m12).put(offset + 10, matrix.m32() + m02 * x + m12 * y).put(offset + 11, alpha / 255.0F);
        return this.tagCount++;
    }

    boolean hasTags() {
        return this.tagCount > 0;
    }

    void discardTags() {
        this.tagCount = 0;
    }

    boolean begin(CommandList commandList) {
        GlProgram<NameTagShader> program;
        try {
            if (this.geometry == null) {
                FloatBuffer corners = BufferUtils.createFloatBuffer(4).put(new float[]{0.0F, 1.0F, 2.0F, 3.0F}).flip();
                this.geometry = new InstancedGeometryBuffer(corners, VERTEX_FORMAT, INSTANCE_FORMAT);
                this.geometry.initialize(commandList);
            }
            program = this.program();
        } catch (RuntimeException exception) {
            this.supported = false;
            Argentum.LOGGER.error("Instanced name tags failed to initialize", exception);
            return false;
        }

        int rows = (this.tagCount + TAGS_PER_ROW - 1) / TAGS_PER_ROW;
        FloatBuffer tags = this.tags.limit(rows * TAGS_PER_ROW * TAG_FLOATS).position(0);
        upload(this.tagTexture, 0, 0, TAGS_PER_ROW * TAG_TEXELS, rows, tags);
        this.tags.clear();

        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.quadTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.tagTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + GlStateManager.texture);

        this.program = program;
        return true;
    }

    void draw(CommandList commandList, int[] refs, int count, float alphaRef) {
        if (this.refs.capacity() < count * 2) this.refs = BufferUtils.createIntBuffer(Math.max(count * 2, this.refs.capacity() * 2));
        IntBuffer upload = this.refs.clear().put(refs, 0, count * 2).flip();
        this.program.bind();
        try {
            NameTagShader shader = this.program.getInterface();
            shader.fog().setup();
            shader.lightmapped().setInt(GlStateManager.TEXTURES[1].state.enabled ? 1 : 0);
            shader.texelSizes().set(this.texelSizes);
            shader.alphaRef().setFloat(alphaRef);
            this.geometry.upload(commandList, upload);
            this.geometry.draw(commandList, 4, count);
        } finally {
            this.program.unbind();
        }
    }

    void end() {
        this.program = null;
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + GlStateManager.texture);
        this.tagCount = 0;
    }

    void close(CommandList commandList) {
        if (this.geometry != null) {
            this.geometry.delete(commandList);
            this.geometry = null;
        }
        this.programs.values().forEach(GlProgram::delete);
        this.programs.clear();
        if (this.quadTexture != 0) GL11.glDeleteTextures(this.quadTexture);
        if (this.tagTexture != 0) GL11.glDeleteTextures(this.tagTexture);
        this.quadTexture = 0;
        this.tagTexture = 0;
        this.initialized = false;
        this.supported = false;
        this.generation++;
        this.quadRow = 0;
        this.quadColumn = 0;
        this.tagCount = 0;
    }

    private GlProgram<NameTagShader> program() {
        if (this.programs.isEmpty()) {
            for (ChunkFogMode fogMode : ChunkFogMode.values()) {
                GlProgram<NameTagShader> program = createProgram(fogMode);
                this.programs.put(fogMode, program);
                program.bind();
                try {
                    NameTagShader shader = program.getInterface();
                    shader.texture().setInt(0);
                    shader.lightmap().setInt(1);
                    shader.quads().setInt(2);
                    shader.tags().setInt(3);
                } finally {
                    program.unbind();
                }
            }
        }
        return this.programs.get(ArgentumFogService.INSTANCE.getFogMode());
    }

    private static GlProgram<NameTagShader> createProgram(ChunkShaderComponent.Factory<?> fogFactory) {
        ShaderConstants constants = ShaderConstants.builder().addAll(fogFactory.getDefines()).build();
        List<GlShader> shaders = List.of(
                ShaderLoader.loadShader(ShaderType.VERTEX, "argentum:name_tag.vert", constants),
                ShaderLoader.loadShader(ShaderType.FRAGMENT, "argentum:name_tag.frag", constants)
        );
        try {
            GlProgram.Builder builder = GlProgram.builder("argentum:name_tag");
            shaders.forEach(builder::attachShader);
            return builder
                    .bindAttributes(VERTEX_FORMAT, 0)
                    .bindAttributes(INSTANCE_FORMAT, VERTEX_FORMAT.getAttributes().size())
                    .link(ctx -> new NameTagShader(ctx, fogFactory));
        } finally {
            shaders.forEach(GlShader::delete);
        }
    }

    private static int createTexture(int width, int height) {
        int previous = GlStateManager.TEXTURES[GlStateManager.texture].texture;
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, ARBTextureFloat.GL_RGBA32F_ARB, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer)null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        return texture;
    }

    private static void upload(int texture, int x, int y, int width, int height, FloatBuffer data) {
        int previous = GlStateManager.TEXTURES[GlStateManager.texture].texture;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height, GL11.GL_RGBA, GL11.GL_FLOAT, data);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
    }

    private record NameTagShader(GlUniformInt texture, GlUniformInt lightmap, GlUniformInt quads, GlUniformInt tags,
            GlUniformInt lightmapped, GlUniformFloat alphaRef, GlUniformFloat4v texelSizes, ChunkShaderComponent fog) {
        NameTagShader(ShaderBindingContext ctx, ChunkShaderComponent.Factory<?> fogFactory) {
            this(ctx.bindUniform("uTexture", GlUniformInt::new),
                    ctx.bindUniform("uLightmap", GlUniformInt::new),
                    ctx.bindUniform("uQuads", GlUniformInt::new),
                    ctx.bindUniform("uTags", GlUniformInt::new),
                    ctx.bindUniform("uLightmapped", GlUniformInt::new),
                    ctx.bindUniform("uAlphaRef", GlUniformFloat::new),
                    ctx.bindUniform("uTexelSizes", GlUniformFloat4v::new),
                    fogFactory.create(ctx, ArgentumFogService.ENVIRONMENT));
        }
    }
}
