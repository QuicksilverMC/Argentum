package dev.rdh.argentum.impl.render.weather;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat4v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformInt;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderComponent;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderFogComponent;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import dev.rdh.argentum.impl.render.instancing.InstanceDataBuffer;
import dev.rdh.argentum.impl.render.instancing.InstancedGeometryBuffer;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class WeatherRenderer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Identifier RAIN_TEXTURE = new Identifier("textures/environment/rain.png");
    private static final Identifier SNOW_TEXTURE = new Identifier("textures/environment/snow.png");
    private static final int INSTANCE_FLOATS = 12;
    private static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(2 * Float.BYTES)
            .addElement("aCorner", 0, GlVertexAttributeFormat.FLOAT, 2, false, false)
            .build();
    private static final GlVertexFormat INSTANCE_FORMAT = GlVertexFormat.builder(INSTANCE_FLOATS * Float.BYTES)
            .addElement("aColumn", 0, GlVertexAttributeFormat.FLOAT, 4, false, false)
            .addElement("aSpan", 4 * Float.BYTES, GlVertexAttributeFormat.FLOAT, 4, false, false)
            .addElement("aJitter", 8 * Float.BYTES, GlVertexAttributeFormat.FLOAT, 4, false, false)
            .build();

    private final Map<ChunkShaderComponent.Factory<?>, GlProgram<WeatherShader>> programs = new Object2ObjectOpenHashMap<>();
    private final WeatherInstances rain = new WeatherInstances();
    private final WeatherInstances snow = new WeatherInstances();
    private final Random random = new Random();
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private final float[] frame0 = new float[4];
    private final float[] frame1 = new float[4];
    private final float[] frame2 = new float[4];

    private boolean initialized;
    private boolean supported;
    private InstancedGeometryBuffer rainGeometry;
    private InstancedGeometryBuffer snowGeometry;

    private boolean built;
    private int builtTicks;
    private int builtX;
    private int builtY;
    private int builtZ;
    private int builtCameraY;
    private int builtRadius;

    public boolean render(CommandList commandList, World world, Entity camera, int ticks, float tickDelta,
            float strength, int radius, float[] sizeX, float[] sizeZ) {
        if (!this.initialize(commandList)) {
            return false;
        }

        int originX = MathHelper.floor(camera.x);
        int originY = MathHelper.floor(camera.y);
        int originZ = MathHelper.floor(camera.z);
        double cameraX = camera.prevX + (camera.x - camera.prevX) * tickDelta;
        double cameraY = camera.prevY + (camera.y - camera.prevY) * tickDelta;
        double cameraZ = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
        int cameraBlockY = MathHelper.floor(cameraY);

        if (!this.built || ticks != this.builtTicks || originX != this.builtX || originY != this.builtY
                || originZ != this.builtZ || cameraBlockY != this.builtCameraY || radius != this.builtRadius) {
            this.rebuild(world, originX, originY, originZ, cameraBlockY, radius, sizeX, sizeZ);
            if (this.rain.count() > 0) {
                this.rainGeometry.upload(commandList, this.rain.upload());
            }
            if (this.snow.count() > 0) {
                this.snowGeometry.upload(commandList, this.snow.upload());
            }
            this.built = true;
            this.builtTicks = ticks;
            this.builtX = originX;
            this.builtY = originY;
            this.builtZ = originZ;
            this.builtCameraY = cameraBlockY;
            this.builtRadius = radius;
        }

        GlProgram<WeatherShader> program;
        try {
            program = this.program();
        } catch (RuntimeException exception) {
            this.supported = false;
            LOGGER.error("Instanced weather failed to initialize", exception);
            return false;
        }

        this.frame0[0] = (float)(originX - cameraX);
        this.frame0[1] = (float)-cameraY;
        this.frame0[2] = (float)(originZ - cameraZ);
        this.frame0[3] = 1.0F / radius;
        this.frame1[0] = (float)(camera.x - originX);
        this.frame1[1] = (float)(camera.z - originZ);
        this.frame1[2] = strength;
        this.frame1[3] = tickDelta;
        this.frame2[0] = ticks + tickDelta;
        this.frame2[1] = ticks & 31;
        this.frame2[2] = ticks & 511;

        program.bind();
        try {
            WeatherShader shader = program.getInterface();
            shader.fog().setup();
            shader.frame0().set(this.frame0);
            shader.frame1().set(this.frame1);
            this.draw(commandList, shader, this.rainGeometry, this.rain, RAIN_TEXTURE, false);
            this.draw(commandList, shader, this.snowGeometry, this.snow, SNOW_TEXTURE, true);
        } catch (RuntimeException exception) {
            this.supported = false;
            LOGGER.error("Instanced weather disabled after a draw failure", exception);
        } finally {
            program.unbind();
        }
        return true;
    }

    private void draw(CommandList commandList, WeatherShader shader, InstancedGeometryBuffer geometry, WeatherInstances instances, Identifier texture, boolean isSnow) {
        if (instances.count() == 0) {
            return;
        }
        this.frame2[3] = isSnow ? 1.0F : 0.0F;
        shader.frame2().set(this.frame2);
        Minecraft.getInstance().getTextureManager().bind(texture);
        geometry.draw(commandList, 4, instances.count());
    }

    private void rebuild(World world, int originX, int originY, int originZ, int cameraBlockY, int radius, float[] sizeX, float[] sizeZ) {
        this.rain.clear();
        this.snow.clear();
        for (int z = originZ - radius; z <= originZ + radius; z++) {
            for (int x = originX - radius; x <= originX + radius; x++) {
                this.pos.set(x, 0, z);
                Biome biome = world.getBiome(this.pos);
                if (!biome.isRainy() && !biome.isSnowy()) {
                    continue;
                }

                int precipitationY = world.getPrecipitationHeight(this.pos).getY();
                int bottom = Math.max(originY - radius, precipitationY);
                int top = Math.max(originY + radius, precipitationY);
                if (bottom == top) {
                    continue;
                }

                int sizeIndex = (z - originZ + 16) * 32 + x - originX + 16;
                float halfX = sizeX[sizeIndex] * 0.5F;
                float halfZ = sizeZ[sizeIndex] * 0.5F;
                this.random.setSeed(hashX(x) ^ hashZ(z));
                this.pos.set(x, bottom, z);
                boolean isRain = world.getBiomeSource().adjustTemperatureForHeight(biome.getTemperature(this.pos), precipitationY) >= 0.15F;
                this.pos.set(x, Math.max(precipitationY, cameraBlockY), z);
                int light = world.getLightColor(this.pos, 0);

                if (isRain) {
                    float speed = (float) this.random.nextDouble();
                    int scrollOffset = hashX(x) + hashZ(z) & 31;
                    this.rain.add(x - originX, z - originZ, halfX, halfZ, bottom, top, light,
                            speed, scrollOffset, 0.0F, 0.0F);
                } else {
                    float u = (float) this.random.nextDouble();
                    float uDrift = (float) this.random.nextGaussian();
                    float v = (float) this.random.nextDouble();
                    float vDrift = (float) this.random.nextGaussian();
                    this.snow.add(x - originX, z - originZ, halfX, halfZ, bottom, top, (light * 3 + 15728880) / 4, u, uDrift, v, vDrift);
                }
            }
        }
    }

    private boolean initialize(CommandList commandList) {
        if (!this.initialized) {
            this.initialized = true;
            var capabilities = GL.getCapabilities();
            this.supported = capabilities.GL_ARB_draw_instanced
                    && capabilities.GL_ARB_instanced_arrays
                    && capabilities.OpenGL20;
            if (!this.supported) {
                LOGGER.warn("Instanced weather disabled: required OpenGL extensions are missing");
                return false;
            }

            try {
                this.rainGeometry = createGeometry(commandList);
                this.snowGeometry = createGeometry(commandList);
                LOGGER.info("Instanced weather enabled");
            } catch (RuntimeException exception) {
                this.supported = false;
                LOGGER.error("Instanced weather geometry failed to initialize", exception);
            }
        }
        return this.supported;
    }

    private GlProgram<WeatherShader> program() {
        ChunkShaderComponent.Factory<?> fogFactory = ChunkShaderFogComponent.FOG_SERVICE.getFogMode();
        GlProgram<WeatherShader> program = this.programs.get(fogFactory);
        if (program == null) {
            program = createProgram(fogFactory);
            this.programs.put(fogFactory, program);
            program.bind();
            try {
                program.getInterface().texture().setInt(0);
                program.getInterface().lightmap().setInt(1);
            } finally {
                program.unbind();
            }
        }
        return program;
    }

    public void close(CommandList commandList) {
        if (this.rainGeometry != null) {
            this.rainGeometry.delete(commandList);
            this.rainGeometry = null;
        }
        if (this.snowGeometry != null) {
            this.snowGeometry.delete(commandList);
            this.snowGeometry = null;
        }
        this.programs.values().forEach(GlProgram::delete);
        this.programs.clear();
        this.initialized = false;
        this.supported = false;
        this.built = false;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    private static int hashX(int x) {
        return x * x * 3121 + x * 45238971;
    }

    private static int hashZ(int z) {
        return z * z * 418711 + z * 13761;
    }

    private static InstancedGeometryBuffer createGeometry(CommandList commandList) {
        FloatBuffer corners = BufferUtils.createFloatBuffer(8);
        corners.put(new float[]{0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F}).flip();
        InstancedGeometryBuffer geometry = new InstancedGeometryBuffer(corners, VERTEX_FORMAT, INSTANCE_FORMAT);
        geometry.initialize(commandList);
        return geometry;
    }

    private static GlProgram<WeatherShader> createProgram(ChunkShaderComponent.Factory<?> fogFactory) {
        ShaderConstants constants = ShaderConstants.builder().addAll(fogFactory.getDefines()).build();
        List<GlShader> shaders = List.of(
                ShaderLoader.loadShader(ShaderType.VERTEX, "argentum:weather.vert", constants),
                ShaderLoader.loadShader(ShaderType.FRAGMENT, "argentum:weather.frag", constants)
        );
        try {
            GlProgram.Builder builder = GlProgram.builder("argentum:weather");
            shaders.forEach(builder::attachShader);
            return builder
                    .bindAttributes(VERTEX_FORMAT, 0)
                    .bindAttributes(INSTANCE_FORMAT, VERTEX_FORMAT.getAttributes().size())
                    .link(ctx -> new WeatherShader(ctx, fogFactory));
        } finally {
            shaders.forEach(GlShader::delete);
        }
    }

    private record WeatherShader(GlUniformInt texture, GlUniformInt lightmap, GlUniformFloat4v frame0,
            GlUniformFloat4v frame1, GlUniformFloat4v frame2, ChunkShaderComponent fog) {
        WeatherShader(ShaderBindingContext ctx, ChunkShaderComponent.Factory<?> fogFactory) {
            this(ctx.bindUniform("uTexture", GlUniformInt::new),
                    ctx.bindUniform("uLightmap", GlUniformInt::new),
                    ctx.bindUniform("uFrame0", GlUniformFloat4v::new),
                    ctx.bindUniform("uFrame1", GlUniformFloat4v::new),
                    ctx.bindUniform("uFrame2", GlUniformFloat4v::new),
                    fogFactory.create(ctx));
        }
    }

    private static final class WeatherInstances extends InstanceDataBuffer {
        private WeatherInstances() {
            super(INSTANCE_FLOATS, 0, 1024);
        }

        private void add(int dx, int dz, float halfX, float halfZ, int bottom, int top, int light,
                float jitter0, float jitter1, float jitter2, float jitter3) {
            int i = this.appendOffset();
            int[] data = this.data();
            data[i++] = Float.floatToRawIntBits(dx);
            data[i++] = Float.floatToRawIntBits(dz);
            data[i++] = Float.floatToRawIntBits(halfX);
            data[i++] = Float.floatToRawIntBits(halfZ);
            data[i++] = Float.floatToRawIntBits(bottom);
            data[i++] = Float.floatToRawIntBits(top);
            data[i++] = Float.floatToRawIntBits(light & 0xFFFF);
            data[i++] = Float.floatToRawIntBits(light >> 16 & 0xFFFF);
            data[i++] = Float.floatToRawIntBits(jitter0);
            data[i++] = Float.floatToRawIntBits(jitter1);
            data[i++] = Float.floatToRawIntBits(jitter2);
            data[i] = Float.floatToRawIntBits(jitter3);
            this.finishInstance();
        }
    }
}
