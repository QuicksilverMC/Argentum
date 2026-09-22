package dev.rdh.argentum.impl.render.entity;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformInt;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderComponent;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderFogComponent;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import dev.rdh.argentum.impl.render.instancing.InstancedGeometryBuffer;
import dev.rdh.argentum.impl.render.instancing.InstanceDataBuffer;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;

public final class EntityShadowBatch {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Identifier SHADOW_TEXTURE = new Identifier("textures/misc/shadow.png");
    private static final int INSTANCE_FLOATS = 10;
    private static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(2 * Float.BYTES)
            .addElement("aCorner", 0, GlVertexAttributeFormat.FLOAT, 2, false, false)
            .build();
    private static final GlVertexFormat INSTANCE_FORMAT = GlVertexFormat.builder(INSTANCE_FLOATS * Float.BYTES)
            .addElement("aBounds0", 0, GlVertexAttributeFormat.FLOAT, 4, false, false)
            .addElement("aBounds1", 4 * Float.BYTES, GlVertexAttributeFormat.FLOAT, 4, false, false)
            .addElement("aShadow", 8 * Float.BYTES, GlVertexAttributeFormat.FLOAT, 2, false, false)
            .build();
    private final Long2ReferenceOpenHashMap<Block> blocks = new Long2ReferenceOpenHashMap<>();
    private final Long2LongOpenHashMap light = new Long2LongOpenHashMap();
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private final BlockPos.Mutable below = new BlockPos.Mutable();
    private final Map<ChunkShaderComponent.Factory<?>, GlProgram<ShadowShader>> programs = new Object2ObjectOpenHashMap<>();
    private final ShadowInstances instances = new ShadowInstances();

    private boolean initialized;
    private boolean supported;
    private boolean active;
    private InstancedGeometryBuffer geometry;
    private GlProgram<ShadowShader> program;

    public EntityShadowBatch() {
        light.defaultReturnValue(Long.MIN_VALUE);
    }

    public void beginFrame() {
        blocks.clear();
        light.clear();
        instances.clear();
        active = initialize();
    }

    public boolean record(World world, Entity entity, double dx, double dy, double dz, float opacity, float tickDelta, float shadowSize) {
        if (!active) {
            return false;
        }

        float scaledSize = shadowSize;
        if (entity instanceof MobEntity mob) {
            scaledSize *= mob.getShadowScale();
            if (mob.isBaby()) {
                scaledSize *= 0.5F;
            }
        }

        double entityX = entity.prevX + (entity.x - entity.prevX) * tickDelta;
        double entityY = entity.prevY + (entity.y - entity.prevY) * tickDelta;
        double entityZ = entity.prevZ + (entity.z - entity.prevZ) * tickDelta;
        int minX = MathHelper.floor(entityX - scaledSize);
        int maxX = MathHelper.floor(entityX + scaledSize);
        int minY = MathHelper.floor(entityY - scaledSize);
        int maxY = MathHelper.floor(entityY);
        int minZ = MathHelper.floor(entityZ - scaledSize);
        int maxZ = MathHelper.floor(entityZ + scaledSize);
        double offsetX = dx - entityX;
        double offsetY = dy - entityY;
        double offsetZ = dz - entityZ;

        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    recordBlock(world, x, y, z, dx, dy, dz, opacity, scaledSize, offsetX, offsetY, offsetZ);
                }
            }
        }
        return true;
    }

    public void flush(CommandList commandList) {
        if (!active) {
            return;
        }
        active = false;
        if (instances.count() == 0) {
            return;
        }
        if (!initializeGeometry(commandList)) {
            return;
        }

        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        Minecraft.getInstance().getTextureManager().bind(SHADOW_TEXTURE);
        GlStateManager.depthMask(false);
        program.bind();
        program.getInterface().fog().setup();

        try {
            geometry.draw(commandList, instances.upload(), 4, instances.count());
        } catch (RuntimeException exception) {
            supported = false;
            LOGGER.error("Instanced entity shadows disabled after a draw failure", exception);
        } finally {
            program.unbind();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
        }
    }

    private void recordBlock(World world, int x, int y, int z, double dx, double dy, double dz, float opacity,
            float shadowSize, double offsetX, double offsetY, double offsetZ) {
        pos.set(x, y, z);
        long key = pos.toLong();
        Block block = blocks.get(key);
        if (block == null) {
            below.set(x, y - 1, z);
            block = world.getBlockState(below).getBlock();
            blocks.put(key, block);
        }

        long light = this.light.get(key);
        if (light == Long.MIN_VALUE) {
            int raw = world.getRawBrightness(pos);
            float brightness = world.dimension.getBrightnessTable()[raw];
            light = (long)raw << 32 | Float.floatToRawIntBits(brightness) & 0xffffffffL;
            this.light.put(key, light);
        }
        if (block.getRenderType() == -1 || (int)(light >>> 32) <= 3 || !block.isCube()) {
            return;
        }

        double alpha = (opacity - (dy - (y + offsetY)) / 2.0) * 0.5
                * Float.intBitsToFloat((int)light);
        if (alpha < 0.0) {
            return;
        }
        alpha = Math.min(alpha, 1.0);

        double minX = x + block.getMinX() + offsetX;
        double maxX = x + block.getMaxX() + offsetX;
        double surfaceY = y + block.getMinY() + offsetY + 0.015625;
        double minZ = z + block.getMinZ() + offsetZ;
        double maxZ = z + block.getMaxZ() + offsetZ;
        float minU = (float)((dx - minX) / 2.0 / shadowSize + 0.5);
        float maxU = (float)((dx - maxX) / 2.0 / shadowSize + 0.5);
        float minV = (float)((dz - minZ) / 2.0 / shadowSize + 0.5);
        float maxV = (float)((dz - maxZ) / 2.0 / shadowSize + 0.5);

        instances.add(minX, surfaceY, minZ, maxX, maxZ, minU, minV, maxU, maxV, alpha);
    }

    private boolean initialize() {
        if (!initialized) {
            initialized = true;
            var capabilities = GL.getCapabilities();
            supported = capabilities.GL_ARB_draw_instanced
                    && capabilities.GL_ARB_instanced_arrays
                    && capabilities.OpenGL20;
            if (!supported) {
                LOGGER.warn("Instanced entity shadows disabled: required OpenGL extensions are missing");
                return false;
            }

            FloatBuffer corners = BufferUtils.createFloatBuffer(8);
            corners.put(new float[]{0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F}).flip();
            geometry = new InstancedGeometryBuffer(corners, VERTEX_FORMAT, INSTANCE_FORMAT);
        }

        if (!supported) {
            return false;
        }

        try {
            ChunkShaderComponent.Factory<?> fogFactory = ChunkShaderFogComponent.FOG_SERVICE.getFogMode();
            program = programs.get(fogFactory);
            if (program == null) {
                boolean firstProgram = programs.isEmpty();
                program = createProgram(fogFactory);
                programs.put(fogFactory, program);
                program.bind();
                try {
                    program.getInterface().texture().setInt(0);
                } finally {
                    program.unbind();
                }
                if (firstProgram) {
                    LOGGER.info("Instanced entity shadows enabled");
                }
            }
        } catch (RuntimeException exception) {
            supported = false;
            LOGGER.error("Instanced entity shadows failed to initialize", exception);
        }
        return supported;
    }

    private boolean initializeGeometry(CommandList commandList) {
        try {
            geometry.initialize(commandList);
            return true;
        } catch (RuntimeException exception) {
            geometry.delete(commandList);
            supported = false;
            LOGGER.error("Instanced entity shadow geometry failed to initialize", exception);
            return false;
        }
    }

    public void close(CommandList commandList) {
        active = false;
        if (geometry != null) {
            geometry.delete(commandList);
            geometry = null;
        }
        programs.values().forEach(GlProgram::delete);
        programs.clear();
        program = null;
        initialized = false;
        supported = false;
    }

    public boolean isInitialized() {
        return initialized;
    }

    private static GlProgram<ShadowShader> createProgram(ChunkShaderComponent.Factory<?> fogFactory) {
        ShaderConstants constants = ShaderConstants.builder().addAll(fogFactory.getDefines()).build();
        List<GlShader> shaders = List.of(
                ShaderLoader.loadShader(ShaderType.VERTEX, "argentum:entity_shadow.vert", constants),
                ShaderLoader.loadShader(ShaderType.FRAGMENT, "argentum:entity_shadow.frag", constants)
        );
        try {
            GlProgram.Builder builder = GlProgram.builder("argentum:entity_shadow");
            shaders.forEach(builder::attachShader);
            return builder
                    .bindAttributes(VERTEX_FORMAT, 0)
                    .bindAttributes(INSTANCE_FORMAT, VERTEX_FORMAT.getAttributes().size())
                    .link(ctx -> new ShadowShader(ctx, fogFactory));
        } finally {
            shaders.forEach(GlShader::delete);
        }
    }

    private record ShadowShader(GlUniformInt texture, ChunkShaderComponent fog) {
        ShadowShader(ShaderBindingContext ctx, ChunkShaderComponent.Factory<?> fogFactory) {
            this(ctx.bindUniform("uTexture", GlUniformInt::new), fogFactory.create(ctx));
        }
    }

    private static final class ShadowInstances extends InstanceDataBuffer {
        private ShadowInstances() {
            super(INSTANCE_FLOATS, 0, 4096);
        }

        private void add(double minX, double y, double minZ, double maxX, double maxZ,
                float minU, float minV, float maxU, float maxV, double alpha) {
            int i = appendOffset();
            int[] data = data();
            data[i++] = Float.floatToRawIntBits((float)minX);
            data[i++] = Float.floatToRawIntBits((float)y);
            data[i++] = Float.floatToRawIntBits((float)minZ);
            data[i++] = Float.floatToRawIntBits((float)maxX);
            data[i++] = Float.floatToRawIntBits((float)maxZ);
            data[i++] = Float.floatToRawIntBits(minU);
            data[i++] = Float.floatToRawIntBits(minV);
            data[i++] = Float.floatToRawIntBits(maxU);
            data[i++] = Float.floatToRawIntBits(maxV);
            data[i] = Float.floatToRawIntBits((float)alpha);
            finishInstance();
        }
    }
}
