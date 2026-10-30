package dev.rdh.argentum.impl.render.environment;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.ext.BufferBuilderExtension;
import dev.rdh.argentum.impl.render.GlStateMirror;
import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EndPortalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import net.minecraft.client.render.vertex.DefaultVertexFormat;

import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniform;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat4v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformInt;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderComponent;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderEnvironment;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

public final class EndPortalRenderer {
    private static final int MAX_LAYERS = 16;
    private static final int IDLE = 0;
    private static final int COLLECT = 1;
    private static final int RECORD = 2;

    private final Int2ObjectMap<GlProgram<PortalShader>> programs = new Int2ObjectOpenHashMap<>();
    private final Int2BooleanMap verdicts = new Int2BooleanOpenHashMap();
    private final ReferenceOpenHashSet<BlockEntity> handled = new ReferenceOpenHashSet<>();
    private final List<BlockEntity> group = new ObjectArrayList<>();
    private final Recording recorded = new Recording();
    private final Recording checked = new Recording();
    private final FloatBuffer scratch = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer s = BufferUtils.createFloatBuffer(MAX_LAYERS * 4);
    private final FloatBuffer t = BufferUtils.createFloatBuffer(MAX_LAYERS * 4);
    private final FloatBuffer q = BufferUtils.createFloatBuffer(MAX_LAYERS * 4);
    private final FloatBuffer colors = BufferUtils.createFloatBuffer(MAX_LAYERS * 4);
    private final Matrix4f modelView = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f nextModelView = new Matrix4f();
    private final Matrix4f nextProjection = new Matrix4f();
    private final Matrix4f transform = new Matrix4f();
    private final Matrix4f generation = new Matrix4f();
    private final Matrix4f lightTransform = new Matrix4f();
    private final Vector4f plane = new Vector4f();
    private final BufferBuilder vertices = new BufferBuilder(4096);
    private final BufferUploader uploader = new BufferUploader();
    private final float[] settings = new float[4];
    private final CapturedFog fog = new CapturedFog();
    private final CapturedFog nextFog = new CapturedFog();
    private final ChunkShaderEnvironment environment = new ChunkShaderEnvironment(this.fog);
    private final float[][] objectPlanes = new float[4][];
    private final int[] texGenModes = new int[4];
    private int objectPlaneMask;
    private List<BlockEntity> list;

    private BlockEntity[] targets = new BlockEntity[16];
    private double[] targetX = new double[16];
    private double[] targetY = new double[16];
    private double[] targetZ = new double[16];
    private float[] targetU = new float[16];
    private float[] targetV = new float[16];
    private int targetCount;
    private int[] replay = new int[16];
    private int replayCount;
    private int source;
    private Recording recording;
    private float lightU;
    private float lightV;
    private int rotation;

    private int[] geometry = new int[0];
    private int vertexCount;
    private int layerLimit = -1;
    private int layers;
    private int texture;
    private int addAlpha;
    private int nextAddAlpha;
    private int mode = IDLE;
    private boolean merging;
    private boolean lightChanged;
    private boolean drawing;
    private boolean failed;

    public static EndPortalRenderer active() {
        ArgentumWorldRenderer renderer = ArgentumWorldRenderer.instanceNullable();
        return renderer == null || renderer.getEndPortalRenderer().mode == IDLE ? null : renderer.getEndPortalRenderer();
    }

    public static EndPortalRenderer capturing() {
        EndPortalRenderer renderer = active();
        return renderer != null && renderer.mode == RECORD && !renderer.drawing ? renderer : null;
    }

    public void begin(List<BlockEntity> list) {
        this.list = list;
        this.handled.clear();
    }

    public void end() {
        this.list = null;
        this.handled.clear();
    }

    public boolean render(BlockEntity entity, float tickDelta) {
        if (!(entity instanceof EndPortalBlockEntity) || this.list == null || this.mode != IDLE) return false;
        if (this.handled.contains(entity)) return true;
        if (!Argentum.CONFIG.fasterEndPortals || this.failed) return false;
        int y = entity.getPos().getY();
        this.group.clear();
        for (BlockEntity other : this.list) {
            if (other instanceof EndPortalBlockEntity && other.getPos().getY() == y && this.handled.add(other)) this.group.add(other);
        }
        this.renderGroup(y, tickDelta);
        return true;
    }

    private void renderGroup(int y, float tickDelta) {
        this.targetCount = 0;
        this.mode = COLLECT;
        try {
            for (BlockEntity entity : this.group) BlockEntityRenderDispatcher.INSTANCE.render(entity, tickDelta, -1);
        } finally {
            this.mode = IDLE;
        }
        if (this.targetCount == 0) return;
        if (this.targetCount == 1) {
            this.vanilla(0, tickDelta);
            return;
        }

        int check = 1 + this.rotation++ % (this.targetCount - 1);
        boolean checkable = this.targetY[check] == this.targetY[0];
        boolean replaying = checkable && this.verdicts.get(y);
        this.replayCount = 0;
        for (int i = 0; i < this.targetCount; i++) {
            if (this.targetY[i] != this.targetY[0]) {
                this.vanilla(i, tickDelta);
            } else if (i == 0 || replaying && i != check) {
                this.addReplay(i);
            }
        }
        this.record(0, this.recorded, tickDelta);
        boolean uniform = false;
        if (checkable) {
            this.replayCount = 0;
            this.addReplay(check);
            this.record(check, this.checked, tickDelta);
            uniform = this.recorded.replayable() && this.checked.replayable() && this.recorded.matches(this.checked);
        }
        if (!replaying) {
            for (int i = 1; i < this.targetCount; i++) {
                if (i != check && this.targetY[i] == this.targetY[0]) this.vanilla(i, tickDelta);
            }
        }
        this.verdicts.put(y, uniform);
    }

    public boolean intercept(EndPortalBlockEntity entity, double x, double y, double z) {
        if (this.mode != COLLECT) return false;
        if (this.targetCount == this.targets.length) {
            int size = this.targetCount * 2;
            this.targets = Arrays.copyOf(this.targets, size);
            this.targetX = Arrays.copyOf(this.targetX, size);
            this.targetY = Arrays.copyOf(this.targetY, size);
            this.targetZ = Arrays.copyOf(this.targetZ, size);
            this.targetU = Arrays.copyOf(this.targetU, size);
            this.targetV = Arrays.copyOf(this.targetV, size);
            this.replay = Arrays.copyOf(this.replay, size);
        }
        int i = this.targetCount++;
        this.targets[i] = entity;
        this.targetX[i] = x;
        this.targetY[i] = y;
        this.targetZ[i] = z;
        this.targetU[i] = this.lightU;
        this.targetV[i] = this.lightV;
        return true;
    }

    public void lightmap(int unit, float u, float v) {
        if (unit != GLX.GL_TEXTURE1 || this.drawing) return;
        if (this.mode == COLLECT) {
            this.lightU = u;
            this.lightV = v;
        } else if (this.mode == RECORD && (u != this.targetU[this.source] || v != this.targetV[this.source])) {
            this.flush();
            this.lightChanged = true;
        }
    }

    private void addReplay(int target) {
        this.replay[this.replayCount++] = target;
    }

    private void vanilla(int target, float tickDelta) {
        BlockEntityRenderDispatcher.INSTANCE.render(this.targets[target], tickDelta, -1);
    }

    private void record(int target, Recording recording, float tickDelta) {
        Arrays.fill(this.texGenModes, 0);
        this.objectPlaneMask = 0;
        this.source = target;
        this.recording = recording;
        this.lightChanged = false;
        recording.reset();
        this.mode = RECORD;
        try {
            BlockEntityRenderDispatcher.INSTANCE.render(this.targets[target], tickDelta, -1);
        } finally {
            try {
                this.flush();
            } finally {
                this.mode = IDLE;
                this.recording = null;
            }
        }
    }

    public void texGenMode(GlStateManager.TexGenMode coordinate, int mode) {
        if (GlStateManager.texture == 0) this.texGenModes[coordinate.ordinal()] = mode;
    }

    public void texGenParam(GlStateManager.TexGenMode coordinate, int parameter, FloatBuffer values) {
        if (GlStateManager.texture != 0 || parameter != GL11.GL_OBJECT_PLANE) return;
        int index = coordinate.ordinal();
        this.objectPlaneMask |= 1 << index;
        if (this.objectPlanes[index] == null) this.objectPlanes[index] = new float[4];
        for (int i = 0; i < 4; i++) this.objectPlanes[index][i] = values.get(values.position() + i);
    }

    public boolean capture(BufferBuilder buffer) {
        if (this.failed || this.lightChanged || !this.emulatable(buffer)) return this.decline();
        IntBuffer data = ((BufferBuilderExtension)buffer).argentum$rawIntBuffer();
        int count = buffer.getVertexCount();
        int color = data.get(3);
        for (int i = 1; i < count; i++) {
            if (data.get(i * 4 + 3) != color) return this.decline();
        }
        this.nextModelView.set(GlStateMirror.matrix(GL11.GL_MODELVIEW_MATRIX));
        this.nextProjection.set(GlStateMirror.matrix(GL11.GL_PROJECTION_MATRIX));
        if (!this.readProjection()) return this.decline();
        this.nextFog.capture();
        if (!this.nextFog.supported) return this.decline();
        boolean merge = this.mergeable();
        if (this.layers != 0 && (!merge || !this.merging || !this.sameGeometry(data, count)
                || !this.modelView.equals(this.nextModelView) || !this.projection.equals(this.nextProjection)
                || this.texture != GlStateManager.TEXTURES[0].texture || this.addAlpha != this.nextAddAlpha
                || !this.fog.same(this.nextFog) || this.layers == this.layerLimit)) {
            this.flush();
        }
        if (this.layers == 0) {
            this.settings[0] = GlStateManager.ALPHA_TEST.state.enabled ? GlStateManager.ALPHA_TEST.ref : -1.0F;
            this.settings[1] = GlStateManager.TEXTURES[1].state.enabled ? 1.0F : 0.0F;
            if (!this.readLight()) return this.decline();
            if (this.geometry.length < count * 4) this.geometry = new int[count * 4];
            for (int i = 0; i < count * 4; i++) this.geometry[i] = data.get(i);
            this.vertexCount = count;
            this.modelView.set(this.nextModelView);
            this.projection.set(this.nextProjection);
            this.texture = GlStateManager.TEXTURES[0].texture;
            this.fog.set(this.nextFog);
            this.addAlpha = merge ? this.nextAddAlpha : 0;
            this.merging = merge;
            if (!this.withinFootprint()) this.recording.dirty = true;
        }
        try {
            this.program(this.layers + 1);
        } catch (RuntimeException exception) {
            this.failed = true;
            Argentum.LOGGER.error("Faster end portals failed to initialize", exception);
            return this.decline();
        }
        this.transform.getRow(0, this.plane).get(this.s.position(), this.s);
        this.s.position(this.s.position() + 4);
        this.transform.getRow(1, this.plane).get(this.t.position(), this.t);
        this.t.position(this.t.position() + 4);
        this.transform.getRow(3, this.plane).get(this.q.position(), this.q);
        this.q.position(this.q.position() + 4);
        boolean little = java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN;
        int red = little ? color & 255 : color >>> 24;
        int green = little ? color >>> 8 & 255 : color >>> 16 & 255;
        int blue = little ? color >>> 16 & 255 : color >>> 8 & 255;
        int alpha = little ? color >>> 24 : color & 255;
        this.colors.put(red / 255.0F).put(green / 255.0F).put(blue / 255.0F).put(alpha / 255.0F);
        this.layers++;
        buffer.end();
        buffer.clear();
        GlStateManager.clearColor();
        if (!merge) this.flush();
        return true;
    }

    private boolean decline() {
        this.flush();
        if (this.recording != null) this.recording.dirty = true;
        return false;
    }

    private boolean emulatable(BufferBuilder buffer) {
        if (this.layerLimit < 0) this.layerLimit = Math.min(MAX_LAYERS, (GL11.glGetInteger(GL20.GL_MAX_FRAGMENT_UNIFORM_COMPONENTS) - 64) / 16);
        if (this.layerLimit < 1) return false;
        for (int i = 2; i < GlStateManager.TEXTURES.length; i++) {
            if (GlStateManager.TEXTURES[i].state.enabled) return false;
        }
        return buffer.getDrawMode() == GL11.GL_QUADS && buffer.getVertexCount() > 0
                && buffer.getVertexCount() % 4 == 0 && DefaultVertexFormat.POSITION_COLOR.equals(buffer.getFormat())
                && GlStateManager.texture == 0 && GlStateManager.TEXTURES[0].state.enabled
                && !GlStateManager.LIGHTING.enabled
                && (!GlStateManager.ALPHA_TEST.state.enabled || GlStateManager.ALPHA_TEST.func == GL11.GL_GREATER)
                && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == 0
                && modulates(GL13.GL_PRIMARY_COLOR);
    }

    private boolean mergeable() {
        GlStateManager.Blend blend = GlStateManager.BLEND;
        return this.layerLimit >= 2 && blend.state.enabled
                && blend.sfactorRGB == GL11.GL_ONE && blend.dfactorRGB == GL11.GL_ONE
                && (!GlStateManager.DEPTH.state.enabled || !GlStateManager.DEPTH.mask
                        || GlStateManager.DEPTH.func != GL11.GL_LESS && GlStateManager.DEPTH.func != GL11.GL_GREATER
                        && GlStateManager.DEPTH.func != GL11.GL_NOTEQUAL)
                && !GL11.glIsEnabled(GL11.GL_STENCIL_TEST) && !GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP)
                && !GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE) && !GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_ONE)
                && GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB) == GL14.GL_FUNC_ADD
                && GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA) == GL14.GL_FUNC_ADD
                && this.additiveBlend();
    }

    private boolean additiveBlend() {
        GlStateManager.Blend blend = GlStateManager.BLEND;
        int destinationAlpha = blend.dfactorAlpha;
        this.nextAddAlpha = destinationAlpha == GL11.GL_ONE ? 1 : 0;
        return blend.sfactorRGB == GL11.GL_ONE
                && blend.dfactorRGB == GL11.GL_ONE
                && blend.sfactorAlpha == GL11.GL_ONE
                && (destinationAlpha == GL11.GL_ONE || destinationAlpha == GL11.GL_ZERO);
    }

    private boolean withinFootprint() {
        // Replayed portals draw run by run, so each one must stay inside its own cell to keep them from overlapping.
        double x = this.targetX[this.source];
        double z = this.targetZ[this.source];
        for (int i = 0; i < this.vertexCount; i++) {
            double dx = Float.intBitsToFloat(this.geometry[i * 4]) - x;
            double dz = Float.intBitsToFloat(this.geometry[i * 4 + 2]) - z;
            if (dx < -1.0E-3 || dx > 1.001 || dz < -1.0E-3 || dz > 1.001) return false;
        }
        return true;
    }

    private boolean readProjection() {
        for (int i = 0; i < 4; i++) {
            int coordinate = GL11.GL_S + i;
            if (!GL11.glIsEnabled(GL11.GL_TEXTURE_GEN_S + i)) return false;
            int mode = this.texGenModes[i] != 0 ? this.texGenModes[i] : GL11.glGetTexGeni(coordinate, GL11.GL_TEXTURE_GEN_MODE);
            if (mode != GL11.GL_OBJECT_LINEAR && mode != GL11.GL_EYE_LINEAR) return false;
            if (mode == GL11.GL_OBJECT_LINEAR && (this.objectPlaneMask & 1 << i) != 0) {
                this.plane.set(this.objectPlanes[i]);
            } else {
                this.scratch.clear();
                GL11.glGetTexGenfv(coordinate, mode == GL11.GL_OBJECT_LINEAR ? GL11.GL_OBJECT_PLANE : GL11.GL_EYE_PLANE, this.scratch);
                this.plane.set(this.scratch);
            }
            if (mode == GL11.GL_EYE_LINEAR) this.plane.mulTranspose(this.nextModelView);
            this.generation.setRow(i, this.plane);
        }
        this.readMatrix(GL11.GL_TEXTURE_MATRIX, this.transform);
        this.transform.mul(this.generation);
        return true;
    }

    private boolean readLight() {
        if (this.settings[1] == 0.0F) return true;
        GlStateManager.activeTexture(GL13.GL_TEXTURE1);
        try {
            if (!modulates(GL13.GL_PREVIOUS)) return false;
            for (int i = 0; i < 4; i++) if (GL11.glIsEnabled(GL11.GL_TEXTURE_GEN_S + i)) return false;
            this.readMatrix(GL11.GL_TEXTURE_MATRIX, this.lightTransform);
            return true;
        } finally {
            GlStateManager.activeTexture(GL13.GL_TEXTURE0);
        }
    }

    private static boolean modulates(int previous) {
        int mode = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE);
        if (mode == GL11.GL_MODULATE) return true;
        return mode == GL13.GL_COMBINE
                && GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB) == GL11.GL_MODULATE
                && GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA) == GL11.GL_MODULATE
                && sources(GL13.GL_SOURCE0_RGB, GL13.GL_SOURCE1_RGB, previous)
                && sources(GL13.GL_SOURCE0_ALPHA, GL13.GL_SOURCE1_ALPHA, previous)
                && GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB) == GL11.GL_SRC_COLOR
                && GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB) == GL11.GL_SRC_COLOR
                && GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA) == GL11.GL_SRC_ALPHA
                && GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA) == GL11.GL_SRC_ALPHA
                && GL11.glGetTexEnvf(GL11.GL_TEXTURE_ENV, GL13.GL_RGB_SCALE) == 1.0F
                && GL11.glGetTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_ALPHA_SCALE) == 1.0F;
    }

    private static boolean sources(int first, int second, int previous) {
        int a = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, first);
        int b = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, second);
        return a == GL11.GL_TEXTURE && b == previous || b == GL11.GL_TEXTURE && a == previous;
    }

    private boolean sameGeometry(IntBuffer data, int count) {
        if (count != this.vertexCount) return false;
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < 3; j++) if (data.get(i * 4 + j) != this.geometry[i * 4 + j]) return false;
        }
        return true;
    }

    private void readMatrix(int parameter, Matrix4f matrix) {
        this.scratch.clear();
        GL11.glGetFloatv(parameter, this.scratch);
        matrix.set(this.scratch);
    }

    public boolean flush() {
        if (this.layers == 0 || this.drawing) return false;
        this.drawing = true;
        this.recording.add(this);
        int previousUnit = GlStateManager.texture;
        GlStateManager.activeTexture(GL13.GL_TEXTURE0);
        int previousTexture = GlStateManager.TEXTURES[0].texture;
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousMode = GlStateMirror.matrixMode();
        int sourceRgb = 0, destinationRgb = 0, sourceAlpha = 0, destinationAlpha = 0, equationRgb = 0, equationAlpha = 0;
        if (this.merging) {
            GlStateManager.Blend blend = GlStateManager.BLEND;
            sourceRgb = blend.sfactorRGB;
            destinationRgb = blend.dfactorRGB;
            sourceAlpha = blend.sfactorAlpha;
            destinationAlpha = blend.dfactorAlpha;
            equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
            equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, this.addAlpha != 0 ? GL11.GL_ONE : GL11.GL_ZERO);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        }
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        this.modelView.get(this.scratch.clear());
        GL11.glLoadMatrixf(this.scratch);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        this.projection.get(this.scratch.clear());
        GL11.glLoadMatrixf(this.scratch);
        try {
            GlProgram<PortalShader> program = this.program(this.layers);
            program.bind();
            PortalShader shader = program.getInterface();
            shader.s().set(this.s.flip());
            shader.t().set(this.t.flip());
            shader.q().set(this.q.flip());
            shader.colors().set(this.colors.flip());
            shader.settings().set(this.settings);
            shader.addAlpha().setInt(this.addAlpha);
            shader.fog().setup();
            GlStateManager.bindTexture(this.texture);
            this.buildReplay();
            this.uploader.end(this.vertices);
            this.vertices.clear();
        } finally {
            if (this.merging) {
                GL14.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
                GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            }
            GL20.glUseProgram(previousProgram);
            GlStateManager.bindTexture(previousTexture);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(previousMode);
            GlStateManager.activeTexture(GL13.GL_TEXTURE0 + previousUnit);
            this.layers = 0;
            this.s.clear();
            this.t.clear();
            this.q.clear();
            this.colors.clear();
            this.drawing = false;
        }
        return true;
    }

    private void buildReplay() {
        double sourceX = this.targetX[this.source];
        double sourceY = this.targetY[this.source];
        double sourceZ = this.targetZ[this.source];
        this.vertices.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int r = 0; r < this.replayCount; r++) {
            int target = this.replay[r];
            double dx = this.targetX[target] - sourceX;
            double dy = this.targetY[target] - sourceY;
            double dz = this.targetZ[target] - sourceZ;
            this.plane.set(this.targetU[target], this.targetV[target], 0.0F, 1.0F).mul(this.lightTransform);
            double u = this.plane.x / this.plane.w;
            double v = this.plane.y / this.plane.w;
            for (int i = 0; i < this.vertexCount; i++) {
                this.vertices.vertex(Float.intBitsToFloat(this.geometry[i * 4]) + dx,
                        Float.intBitsToFloat(this.geometry[i * 4 + 1]) + dy,
                        Float.intBitsToFloat(this.geometry[i * 4 + 2]) + dz).texture(u, v).nextVertex();
            }
        }
        this.vertices.end();
    }

    private GlProgram<PortalShader> program(int count) {
        ChunkFogMode mode = this.fog.getFogMode();
        return this.programs.computeIfAbsent(mode.ordinal() * MAX_LAYERS + count - 1, key -> this.createProgram(mode, count));
    }

    private GlProgram<PortalShader> createProgram(ChunkFogMode fogMode, int count) {
        ShaderConstants.Builder defines = ShaderConstants.builder().addAll(fogMode.getDefines()).add("LAYER_COUNT", Integer.toString(count));
        List<GlShader> shaders = new ObjectArrayList<>(2);
        try {
            shaders.add(ShaderLoader.loadShader(ShaderType.VERTEX, "argentum:end_portal.vert", defines.build()));
            shaders.add(ShaderLoader.loadShader(ShaderType.FRAGMENT, "argentum:end_portal.frag", defines.build()));
            GlProgram.Builder builder = GlProgram.builder("argentum:end_portal");
            shaders.forEach(builder::attachShader);
            GlProgram<PortalShader> program = builder.link(ctx -> new PortalShader(ctx, fogMode, this.environment));
            program.bind();
            try {
                program.getInterface().texture().setInt(0);
                program.getInterface().lightmap().setInt(1);
            } finally {
                program.unbind();
            }
            return program;
        } finally {
            shaders.forEach(GlShader::delete);
        }
    }

    public boolean isInitialized() {
        return !this.programs.isEmpty() || this.failed;
    }

    public void close() {
        this.programs.values().forEach(GlProgram::delete);
        this.programs.clear();
        this.verdicts.clear();
        this.failed = false;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) <= 1.0E-4F * Math.max(1.0F, Math.max(Math.abs(a), Math.abs(b)));
    }

    private static boolean near(float[] a, float[] b, int length) {
        for (int i = 0; i < length; i++) if (!near(a[i], b[i])) return false;
        return true;
    }

    private static final class Run {
        private final CapturedFog fog = new CapturedFog();
        private final float[] settings = new float[2];
        private float[] layers = new float[0];
        private float[] geometry = new float[0];
        private int layerCount;
        private int vertexCount;
        private int texture;
        private int addAlpha;
        private boolean merging;

        private void set(EndPortalRenderer renderer) {
            this.layerCount = renderer.layers;
            this.vertexCount = renderer.vertexCount;
            this.texture = renderer.texture;
            this.addAlpha = renderer.addAlpha;
            this.merging = renderer.merging;
            this.settings[0] = renderer.settings[0];
            this.settings[1] = renderer.settings[1];
            this.fog.set(renderer.fog);
            int size = this.layerCount * 16;
            if (this.layers.length < size) this.layers = new float[size];
            for (int i = 0; i < this.layerCount * 4; i++) {
                this.layers[i] = renderer.s.get(i);
                this.layers[size / 4 + i] = renderer.t.get(i);
                this.layers[size / 2 + i] = renderer.q.get(i);
                this.layers[size * 3 / 4 + i] = renderer.colors.get(i);
            }
            if (this.geometry.length < this.vertexCount * 3) this.geometry = new float[this.vertexCount * 3];
            double[] origin = {renderer.targetX[renderer.source], renderer.targetY[renderer.source], renderer.targetZ[renderer.source]};
            for (int i = 0; i < this.vertexCount; i++) {
                for (int j = 0; j < 3; j++) {
                    this.geometry[i * 3 + j] = (float)(Float.intBitsToFloat(renderer.geometry[i * 4 + j]) - origin[j]);
                }
            }
        }

        private boolean matches(Run other) {
            return this.layerCount == other.layerCount && this.vertexCount == other.vertexCount && this.texture == other.texture
                    && this.addAlpha == other.addAlpha && this.merging == other.merging
                    && this.settings[0] == other.settings[0] && this.settings[1] == other.settings[1] && this.fog.same(other.fog)
                    && near(this.layers, other.layers, this.layerCount * 16) && near(this.geometry, other.geometry, this.vertexCount * 3);
        }
    }

    private static final class Recording {
        private final List<Run> runs = new ObjectArrayList<>();
        private int size;
        private boolean dirty;

        private void reset() {
            this.size = 0;
            this.dirty = false;
        }

        private void add(EndPortalRenderer renderer) {
            if (this.size == this.runs.size()) this.runs.add(new Run());
            this.runs.get(this.size++).set(renderer);
        }

        private boolean replayable() {
            return !this.dirty && this.size > 0;
        }

        private boolean matches(Recording other) {
            if (this.size != other.size) return false;
            for (int i = 0; i < this.size; i++) if (!this.runs.get(i).matches(other.runs.get(i))) return false;
            return true;
        }
    }

    private static final class CapturedFog implements FogService {
        private final float[] color = new float[4];
        private int mode;
        private float start;
        private float end;
        private float density;
        private int shape = FOG_SHAPE_PLANAR;
        private boolean supported = true;

        private void capture() {
            this.supported = true;
            GlStateManager.Fog fog = GlStateManager.FOG;
            this.mode = fog.state.enabled ? fog.mode : 0;
            if (this.mode == 0) return;
            this.shape = Argentum.CONFIG.fogShape.index;
            this.start = fog.start;
            this.end = fog.end;
            this.density = fog.density;
            GameRenderer renderer = Minecraft.getInstance().gameRenderer;
            this.color[0] = renderer.fogRed;
            this.color[1] = renderer.fogGreen;
            this.color[2] = renderer.fogBlue;
            this.color[3] = 1.0F;
        }

        private boolean same(CapturedFog other) {
            return this.mode == other.mode && this.start == other.start && this.end == other.end
                    && this.density == other.density && this.shape == other.shape && Arrays.equals(this.color, other.color);
        }

        private void set(CapturedFog other) {
            this.mode = other.mode;
            this.shape = other.shape;
            this.supported = other.supported;
            this.start = other.start;
            this.end = other.end;
            this.density = other.density;
            System.arraycopy(other.color, 0, this.color, 0, 4);
        }

        public float getFogEnd() { return this.end; }
        public float getFogStart() { return this.start; }
        public float getFogDensity() { return this.density; }
        public int getFogShapeIndex() { return this.shape; }
        public float getFogCutoff() { return this.end; }
        public float[] getFogColor() { return this.color; }
        public ChunkFogMode getFogMode() { return ChunkFogMode.fromGLMode(this.mode); }
    }

    private static final class UniformVectors extends GlUniform<FloatBuffer> {
        private UniformVectors(int index) { super(index); }
        @Override
        public void set(FloatBuffer value) { GL20.glUniform4fv(this.index, value); }
    }

    private record PortalShader(GlUniformInt texture, GlUniformInt lightmap, UniformVectors s, UniformVectors t,
            UniformVectors q, UniformVectors colors, GlUniformFloat4v settings, GlUniformInt addAlpha, ChunkShaderComponent fog) {
        PortalShader(ShaderBindingContext ctx, ChunkFogMode fog, ChunkShaderEnvironment environment) {
            this(ctx.bindUniform("uTexture", GlUniformInt::new), ctx.bindUniform("uLightmap", GlUniformInt::new),
                    ctx.bindUniform("uS[0]", UniformVectors::new), ctx.bindUniform("uT[0]", UniformVectors::new),
                    ctx.bindUniform("uQ[0]", UniformVectors::new), ctx.bindUniform("uColors[0]", UniformVectors::new),
                    ctx.bindUniform("uSettings", GlUniformFloat4v::new), ctx.bindUniform("uAddAlpha", GlUniformInt::new), fog.create(ctx, environment));
        }
    }
}
