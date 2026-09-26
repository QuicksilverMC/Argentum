package dev.rdh.argentum.impl.render.environment;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.util.math.MathHelper;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.embeddedt.embeddium.impl.gl.array.GlVertexArray;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat4v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformInt;
import org.embeddedt.embeddium.impl.gl.tessellation.GlVertexArrayTessellation;
import org.embeddedt.embeddium.impl.gl.tessellation.TessellationBinding;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderComponent;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.terrain.fog.ArgentumFogService;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;

public final class CloudRenderer {
    private static final int VERTEX_FLOATS = 6;
    private static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(VERTEX_FLOATS * Float.BYTES)
            .addElement("aPosition", 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement("aTexCoord", 3 * Float.BYTES, GlVertexAttributeFormat.FLOAT, 2, false, false)
            .addElement("aShade", 5 * Float.BYTES, GlVertexAttributeFormat.FLOAT, 1, false, false)
            .build();
    private static final float INSET = 1.0F / 1024.0F;

    private final Map<ChunkShaderComponent.Factory<?>, GlProgram<CloudShader>> programs = new Object2ObjectOpenHashMap<>();
    private final float[] frame0 = new float[4];
    private final float[] frame1 = new float[4];

    private boolean initialized;
    private boolean supported;
    private GlMutableBuffer vertexBuffer;
    private GlVertexArrayTessellation tessellation;
    private int meshFirstCell;
    private int meshLastCell;
    private int sidesStart;
    private int topStart;
    private int vertexCount;

    public boolean render(double cloudX, double cloudZ, float cloudY, float red, float green, float blue, int firstCell, int lastCell, int pass) {
        RenderDevice.enterManagedCode();
        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            if (!this.initialize()) {
                return false;
            }
            if (this.vertexBuffer == null || firstCell != this.meshFirstCell || lastCell != this.meshLastCell) {
                this.createMesh(commandList, firstCell, lastCell);
            }

            GlProgram<CloudShader> program;
            try {
                program = this.program();
            } catch (RuntimeException exception) {
                this.supported = false;
                Argentum.LOGGER.error("Faster clouds failed to initialize", exception);
                return false;
            }

            int texelX = MathHelper.floor(cloudX);
            int texelZ = MathHelper.floor(cloudZ);
            this.frame0[0] = texelX;
            this.frame0[1] = texelZ;
            this.frame0[2] = (float)(cloudX - texelX);
            this.frame0[3] = (float)(cloudZ - texelZ);
            this.frame1[0] = red;
            this.frame1[1] = green;
            this.frame1[2] = blue;
            this.frame1[3] = cloudY;
            int first = cloudY > -5.0F ? 0 : this.sidesStart;
            int last = cloudY <= 5.0F ? this.vertexCount : this.topStart;

            program.bind();
            try {
                CloudShader shader = program.getInterface();
                shader.fog().setup();
                shader.frame0().set(this.frame0);
                shader.frame1().set(this.frame1);
                this.tessellation.bind(commandList);
                try {
                    GlStateManager.colorMask(false, false, false, false);
                    GL11.glDrawArrays(GL11.GL_QUADS, first, last - first);
                    switch (pass) {
                        case 0 -> GlStateManager.colorMask(false, true, true, true);
                        case 1 -> GlStateManager.colorMask(true, false, false, true);
                        case 2 -> GlStateManager.colorMask(true, true, true, true);
                    }
                    GL11.glDrawArrays(GL11.GL_QUADS, first, last - first);
                } finally {
                    this.tessellation.unbind(commandList);
                }
            } catch (RuntimeException exception) {
                this.supported = false;
                Argentum.LOGGER.error("Faster clouds disabled after a draw failure", exception);
            } finally {
                program.unbind();
            }
            return true;
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    public void delete() {
        if (!this.initialized) {
            return;
        }
        RenderDevice.enterManagedCode();
        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.delete(commandList);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    private void delete(CommandList commandList) {
        if (this.tessellation != null) {
            commandList.deleteTessellation(this.tessellation);
            this.tessellation = null;
        }
        if (this.vertexBuffer != null) {
            commandList.deleteBuffer(this.vertexBuffer);
            this.vertexBuffer = null;
        }
        this.programs.values().forEach(GlProgram::delete);
        this.programs.clear();
        this.initialized = false;
        this.supported = false;
    }

    private boolean initialize() {
        if (!this.initialized) {
            this.initialized = true;
            this.supported = GL.getCapabilities().OpenGL20;
            if (!this.supported) {
                Argentum.LOGGER.warn("Faster clouds disabled: OpenGL 2.0 is unavailable");
            }
        }
        return this.supported;
    }

    private GlProgram<CloudShader> program() {
        if (this.programs.isEmpty()) {
            for (ChunkFogMode fogMode : ChunkFogMode.values()) {
                GlProgram<CloudShader> program = createProgram(fogMode);
                this.programs.put(fogMode, program);
                program.bind();
                try {
                    program.getInterface().texture().setInt(0);
                } finally {
                    program.unbind();
                }
            }
        }
        return this.programs.get(ArgentumFogService.INSTANCE.getFogMode());
    }

    private void createMesh(CommandList commandList, int firstCell, int lastCell) {
        int cells = lastCell - firstCell + 1;
        // 32 edge strips + 2 caps per cell
        ByteBuffer bytes = BufferUtils.createByteBuffer(cells * cells * 34 * 4 * VERTEX_FLOATS * Float.BYTES);
        FloatBuffer vertices = bytes.asFloatBuffer();
        for (int cellX = firstCell; cellX <= lastCell; cellX++) {
            for (int cellZ = firstCell; cellZ <= lastCell; cellZ++) {
                horizontal(vertices, cellX * 8, cellZ * 8, 0.0F, 0.7F);
            }
        }

        this.sidesStart = vertices.position() / VERTEX_FLOATS;
        for (int cellX = firstCell; cellX <= lastCell; cellX++) {
            for (int cellZ = firstCell; cellZ <= lastCell; cellZ++) {
                float x = cellX * 8;
                float z = cellZ * 8;
                for (int strip = 0; strip < 8; strip++) {
                    float u = x + strip + 0.5F;
                    float v = z + strip + 0.5F;
                    if (cellX > -1) {
                        quad(vertices, 0.9F,
                                x + strip, 0.0F, z + 8.0F, u, z + 8.0F,
                                x + strip, 4.0F, z + 8.0F, u, z + 8.0F,
                                x + strip, 4.0F, z, u, z,
                                x + strip, 0.0F, z, u, z
                        );
                    }
                    if (cellX <= 1) {
                        float edgeX = x + strip + 1.0F - INSET;
                        quad(vertices, 0.9F,
                                edgeX, 0.0F, z + 8.0F, u, z + 8.0F,
                                edgeX, 4.0F, z + 8.0F, u, z + 8.0F,
                                edgeX, 4.0F, z, u, z,
                                edgeX, 0.0F, z, u, z
                        );
                    }
                    if (cellZ > -1) {
                        quad(vertices, 0.8F,
                                x, 4.0F, z + strip, x, v,
                                x + 8.0F, 4.0F, z + strip, x + 8.0F, v,
                                x + 8.0F, 0.0F, z + strip, x + 8.0F, v,
                                x, 0.0F, z + strip, x, v
                        );
                    }
                    if (cellZ <= 1) {
                        float edgeZ = z + strip + 1.0F - INSET;
                        quad(vertices, 0.8F,
                                x, 4.0F, edgeZ, x, v,
                                x + 8.0F, 4.0F, edgeZ, x + 8.0F, v,
                                x + 8.0F, 0.0F, edgeZ, x + 8.0F, v,
                                x, 0.0F, edgeZ, x, v
                        );
                    }
                }
            }
        }

        this.topStart = vertices.position() / VERTEX_FLOATS;
        for (int cellX = firstCell; cellX <= lastCell; cellX++) {
            for (int cellZ = firstCell; cellZ <= lastCell; cellZ++) {
                horizontal(vertices, cellX * 8, cellZ * 8, 4.0F - INSET, 1.0F);
            }
        }
        this.vertexCount = vertices.position() / VERTEX_FLOATS;
        this.meshFirstCell = firstCell;
        this.meshLastCell = lastCell;

        if (this.vertexBuffer == null) {
            this.vertexBuffer = commandList.createMutableBuffer();
            this.tessellation = new GlVertexArrayTessellation(new GlVertexArray(), new TessellationBinding[]{
                    TessellationBinding.forVertexBuffer(this.vertexBuffer, VERTEX_FORMAT)
            });
            this.tessellation.init(commandList);
        }
        bytes.limit(vertices.position() * Float.BYTES);
        commandList.uploadData(this.vertexBuffer, bytes, GlBufferUsage.STATIC_DRAW);
    }

    private static void horizontal(FloatBuffer vertices, float x, float z, float y, float shade) {
        quad(vertices, shade,
                x, y, z + 8.0F, x, z + 8.0F,
                x + 8.0F, y, z + 8.0F, x + 8.0F, z + 8.0F,
                x + 8.0F, y, z, x + 8.0F, z,
                x, y, z, x, z);
    }

    private static void quad(FloatBuffer vertices, float shade,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3
    ) {
        vertices.put(x0).put(y0).put(z0).put(u0).put(v0).put(shade);
        vertices.put(x1).put(y1).put(z1).put(u1).put(v1).put(shade);
        vertices.put(x2).put(y2).put(z2).put(u2).put(v2).put(shade);
        vertices.put(x3).put(y3).put(z3).put(u3).put(v3).put(shade);
    }

    private static GlProgram<CloudShader> createProgram(ChunkShaderComponent.Factory<?> fogFactory) {
        ShaderConstants constants = ShaderConstants.builder().addAll(fogFactory.getDefines()).build();
        List<GlShader> shaders = List.of(
                ShaderLoader.loadShader(ShaderType.VERTEX, "argentum:clouds.vert", constants),
                ShaderLoader.loadShader(ShaderType.FRAGMENT, "argentum:clouds.frag", constants)
        );
        try {
            GlProgram.Builder builder = GlProgram.builder("argentum:clouds");
            shaders.forEach(builder::attachShader);
            return builder
                    .bindAttributes(VERTEX_FORMAT, 0)
                    .link(ctx -> new CloudShader(ctx, fogFactory));
        } finally {
            shaders.forEach(GlShader::delete);
        }
    }

    private record CloudShader(GlUniformInt texture, GlUniformFloat4v frame0, GlUniformFloat4v frame1,
            ChunkShaderComponent fog) {
        CloudShader(ShaderBindingContext ctx, ChunkShaderComponent.Factory<?> fogFactory) {
            this(ctx.bindUniform("uTexture", GlUniformInt::new),
                    ctx.bindUniform("uFrame0", GlUniformFloat4v::new),
                    ctx.bindUniform("uFrame1", GlUniformFloat4v::new),
                    fogFactory.create(ctx, ArgentumFogService.ENVIRONMENT)
            );
        }
    }
}
