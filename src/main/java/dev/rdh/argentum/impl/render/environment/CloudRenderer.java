package dev.rdh.argentum.impl.render.environment;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.util.math.MathHelper;

import it.unimi.dsi.fastutil.ints.IntArrayList;
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
import java.util.Arrays;
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
    private static final int TEXTURE_SIZE = 256;
    // the least alpha that survives vanilla's 0.1 alpha test at the 0.8 cloud alpha
    private static final int MIN_ALPHA = 32;

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
    private int meshTexelX;
    private int meshTexelZ;
    private boolean[] cloudTexels;
    private ByteBuffer meshBytes;
    private FloatBuffer meshVertices;

    public boolean render(double cloudX, double cloudZ, float cloudY, float red, float green, float blue, int firstCell, int lastCell, int pass) {
        if (this.initialized && !this.supported) {
            return false;
        }
        RenderDevice.enterManagedCode();
        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            if (!this.initialize()) {
                return false;
            }
            if (this.cloudTexels == null) {
                // vanilla has just bound the cloud texture
                this.cloudTexels = readCloudTexels();
                if (this.cloudTexels == null) {
                    this.supported = false;
                    Argentum.LOGGER.warn("Faster clouds disabled: the cloud texture is not {}x{}", TEXTURE_SIZE, TEXTURE_SIZE);
                    return false;
                }
            }
            int texelX = MathHelper.floor(cloudX);
            int texelZ = MathHelper.floor(cloudZ);
            if (this.vertexBuffer == null || firstCell != this.meshFirstCell || lastCell != this.meshLastCell
                    || texelX != this.meshTexelX || texelZ != this.meshTexelZ) {
                try {
                    this.createMesh(commandList, texelX, texelZ, firstCell, lastCell);
                } catch (RuntimeException exception) {
                    this.supported = false;
                    Argentum.LOGGER.error("Faster clouds failed to build their mesh", exception);
                    return false;
                }
            }

            GlProgram<CloudShader> program;
            try {
                program = this.program();
            } catch (RuntimeException exception) {
                this.supported = false;
                Argentum.LOGGER.error("Faster clouds failed to initialize", exception);
                return false;
            }

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
        this.cloudTexels = null;
        this.meshBytes = null;
        this.meshVertices = null;
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

    private static boolean[] readCloudTexels() {
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        // vanilla scales cloud uvs for 256 texels
        if (width != TEXTURE_SIZE || height != TEXTURE_SIZE) {
            return null;
        }
        ByteBuffer pixels = BufferUtils.createByteBuffer(TEXTURE_SIZE * TEXTURE_SIZE * 4);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        boolean[] texels = new boolean[TEXTURE_SIZE * TEXTURE_SIZE];
        for (int i = 0; i < texels.length; i++) {
            texels[i] = (pixels.get(i * 4 + 3) & 0xFF) >= MIN_ALPHA;
        }
        return texels;
    }

    private void createMesh(CommandList commandList, int texelX, int texelZ, int firstCell, int lastCell) {
        int origin = firstCell * 8;
        int size = (lastCell - firstCell + 1) * 8;
        boolean[] solid = new boolean[size * size];
        for (int z = 0; z < size; z++) {
            int row = Math.floorMod(texelZ + origin + z, TEXTURE_SIZE) * TEXTURE_SIZE;
            for (int x = 0; x < size; x++) {
                solid[z * size + x] = this.cloudTexels[row + Math.floorMod(texelX + origin + x, TEXTURE_SIZE)];
            }
        }
        IntArrayList plates = plates(solid, size);

        if (this.meshBytes == null) {
            this.meshBytes = BufferUtils.createByteBuffer(4096 * 4 * VERTEX_FLOATS * Float.BYTES);
            this.meshVertices = this.meshBytes.asFloatBuffer();
        }
        this.meshVertices.clear();
        for (int i = 0; i < plates.size(); i += 4) {
            this.horizontal(origin + plates.getInt(i), origin + plates.getInt(i + 1), plates.getInt(i + 2), plates.getInt(i + 3), 0.0F, 0.7F);
        }

        this.sidesStart = this.meshVertices.position() / VERTEX_FLOATS;
        this.walls(solid, size, origin, true, -1);
        this.walls(solid, size, origin, true, 1);
        this.walls(solid, size, origin, false, -1);
        this.walls(solid, size, origin, false, 1);

        this.topStart = this.meshVertices.position() / VERTEX_FLOATS;
        for (int i = 0; i < plates.size(); i += 4) {
            this.horizontal(origin + plates.getInt(i), origin + plates.getInt(i + 1), plates.getInt(i + 2), plates.getInt(i + 3), 4.0F - INSET, 1.0F);
        }
        this.vertexCount = this.meshVertices.position() / VERTEX_FLOATS;
        this.meshFirstCell = firstCell;
        this.meshLastCell = lastCell;
        this.meshTexelX = texelX;
        this.meshTexelZ = texelZ;

        if (this.vertexBuffer == null) {
            this.vertexBuffer = commandList.createMutableBuffer();
            this.tessellation = new GlVertexArrayTessellation(new GlVertexArray(), new TessellationBinding[]{
                    TessellationBinding.forVertexBuffer(this.vertexBuffer, VERTEX_FORMAT)
            });
            this.tessellation.init(commandList);
        }
        this.meshBytes.clear().limit(this.meshVertices.position() * Float.BYTES);
        commandList.uploadData(this.vertexBuffer, this.meshBytes, GlBufferUsage.STATIC_DRAW);
    }

    private static IntArrayList plates(boolean[] solid, int size) {
        boolean[] open = solid.clone();
        IntArrayList rects = new IntArrayList();
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                if (!open[z * size + x]) {
                    continue;
                }
                int width = 1;
                while (x + width < size && open[z * size + x + width]) {
                    width++;
                }
                int depth = 1;
                grow:
                while (z + depth < size) {
                    for (int dx = 0; dx < width; dx++) {
                        if (!open[(z + depth) * size + x + dx]) {
                            break grow;
                        }
                    }
                    depth++;
                }
                for (int dz = 0; dz < depth; dz++) {
                    Arrays.fill(open, (z + dz) * size + x, (z + dz) * size + x + width, false);
                }
                rects.add(x);
                rects.add(z);
                rects.add(width);
                rects.add(depth);
            }
        }
        return rects;
    }

    private void walls(boolean[] solid, int size, int origin, boolean facingX, int step) {
        for (int a = 0; a < size; a++) {
            int edge = origin + a;
            if (step < 0 ? edge < 0 : edge >= 16) {
                continue;
            }
            float plane = edge + (step < 0 ? 0.0F : 1.0F - INSET);
            float texel = edge + 0.5F;
            int b = 0;
            while (b < size) {
                int start = b;
                while (b < size && exposed(solid, size, origin, facingX ? a : b, facingX ? b : a, facingX ? step : 0, facingX ? 0 : step)) {
                    b++;
                }
                if (b == start) {
                    b++;
                    continue;
                }
                float from = origin + start;
                float to = origin + b;
                if (facingX) {
                    this.quad(0.9F,
                            plane, 0.0F, to, texel, to,
                            plane, 4.0F, to, texel, to,
                            plane, 4.0F, from, texel, from,
                            plane, 0.0F, from, texel, from
                    );
                } else {
                    this.quad(0.8F,
                            from, 4.0F, plane, from, texel,
                            to, 4.0F, plane, to, texel,
                            to, 0.0F, plane, to, texel,
                            from, 0.0F, plane, from, texel
                    );
                }
            }
        }
    }

    private static boolean exposed(boolean[] solid, int size, int origin, int x, int z, int dx, int dz) {
        if (!solid[z * size + x]) {
            return false;
        }
        if (Math.abs(x + origin) <= 1 && Math.abs(z + origin) <= 1) {
            return true;
        }
        int neighborX = x + dx;
        int neighborZ = z + dz;
        return neighborX < 0 || neighborZ < 0 || neighborX >= size || neighborZ >= size || !solid[neighborZ * size + neighborX];
    }

    private void horizontal(float x, float z, float width, float depth, float y, float shade) {
        this.quad(shade,
                x, y, z + depth, x, z + depth,
                x + width, y, z + depth, x + width, z + depth,
                x + width, y, z, x + width, z,
                x, y, z, x, z);
    }

    private void quad(float shade,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3
    ) {
        FloatBuffer vertices = this.meshVertices;
        if (vertices.remaining() < 4 * VERTEX_FLOATS) {
            this.meshBytes = BufferUtils.createByteBuffer(this.meshBytes.capacity() * 2);
            this.meshVertices = this.meshBytes.asFloatBuffer().put(vertices.flip());
            vertices = this.meshVertices;
        }
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
