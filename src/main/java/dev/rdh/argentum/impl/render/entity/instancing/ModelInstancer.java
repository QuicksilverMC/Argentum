package dev.rdh.argentum.impl.render.entity.instancing;

import net.minecraft.client.render.model.ModelPart;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.instancing.BoxTemplate;
import dev.rdh.argentum.impl.render.instancing.TextureArrayManager;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderParser;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderComponent;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderFogComponent;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.util.regex.Pattern;

public final class ModelInstancer {
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("^#version.*$", Pattern.MULTILINE);
    private static final Pattern IN_PARAM = Pattern.compile("^in ", Pattern.MULTILINE);
    private static final Pattern OUT_PARAM = Pattern.compile("^out ", Pattern.MULTILINE);

    private final InstanceBatcher batcher = new InstanceBatcher();
    private final BakedItemGeometryCache itemGeometry = new BakedItemGeometryCache();
    private final TextureArrayManager textureArrays = new TextureArrayManager();
    private final Reference2ReferenceOpenHashMap<ChunkShaderComponent.Factory<?>, GlProgram<InstanceShader>> programs =
            new Reference2ReferenceOpenHashMap<>();

    private boolean initialized;
    private boolean supported;
    private boolean textureArraysSupported;
    private boolean batchActive;
    private int frame;
    private int selectedFrame = -1;
    private int selectedTextureLayer;
    private int instanceCount;
    private Identifier selectedTexture;
    private InstanceRenderPass selectedPass;
    private InstanceBatcher.TextureBatch selectedTextureBatch;
    private ArrowGeometry arrowGeometry;

    public boolean beginBatch() {
        if (!this.initialize()) {
            return false;
        }

        this.instanceCount = 0;
        this.frame++;
        this.batchActive = true;
        return true;
    }

    public boolean isBatchActive() {
        return this.batchActive;
    }

    public ModelGeometry model(Model model) {
        return this.batcher.model(model);
    }

    public boolean isLayeredItem(BakedModel model) {
        return this.itemGeometry.isLayered(model);
    }

    public void captureItemGlintMatrix(int pass, Matrix4fc matrix) {
        this.batcher.captureItemGlintMatrix(pass, matrix);
    }

    public boolean supportsItem(BakedModel model, ItemStack item) {
        return this.itemGeometry.supportsItem(model, item);
    }

    public InstanceGeometry item(BakedModel model, ItemStack item) {
        return this.itemGeometry.getItem(model, item);
    }

    public InstanceGeometry fixedItem(BakedModel model, int color) {
        return this.itemGeometry.getFixed(model, color);
    }

    public InstanceGeometry block(BakedModel model, float brightness, float red, float green, float blue) {
        return this.itemGeometry.getBlock(model, brightness, red, green, blue);
    }

    public InstanceGeometry arrow() {
        if (this.arrowGeometry == null) {
            this.arrowGeometry = new ArrowGeometry();
        }
        return this.arrowGeometry;
    }

    private final BoxGeometry[] boxGeometries = new BoxGeometry[2];
    private final Reference2ReferenceOpenHashMap<ModelPart, BoxTemplate[]> boxTemplates = new Reference2ReferenceOpenHashMap<>();
    private static final BoxTemplate[] NOT_BOXES = new BoxTemplate[0];

    public InstanceGeometry boxGeometry(boolean mirrored) {
        int index = mirrored ? 1 : 0;
        if (this.boxGeometries[index] == null) {
            this.boxGeometries[index] = new BoxGeometry(mirrored);
        }
        return this.boxGeometries[index];
    }

    /** {@return the part's boxes reduced to templates, or null if any of them is not a plain cuboid} */
    public BoxTemplate[] boxTemplates(ModelPart part) {
        BoxTemplate[] cached = this.boxTemplates.get(part);
        if (cached == null) {
            cached = NOT_BOXES;
            BoxTemplate[] templates = new BoxTemplate[part.boxes.size()];
            for (int i = 0; i < templates.length; i++) {
                templates[i] = BoxTemplate.of(part, part.boxes.get(i));
                if (templates[i] == null) {
                    templates = null;
                    break;
                }
            }
            if (templates != null) {
                cached = templates;
            }
            this.boxTemplates.put(part, cached);
        }
        return cached == NOT_BOXES ? null : cached;
    }

    public boolean submit(InstanceGeometry geometry, Identifier texture, InstanceRenderPass pass, Matrix4f matrix,
                          int packedLight, Vector4fc color, float effectTime, Vector4fc overlayColor,
                          BoxTemplate box) {
        if (!this.batchActive || geometry == null || texture == null) {
            return false;
        }

        InstanceBatcher.TextureBatch textureBatch = this.selectTexture(texture, pass);
        textureBatch.add(geometry, matrix, packedLight & 0xFFFF, packedLight >>> 16,
                this.selectedTextureLayer, color, effectTime, overlayColor, box
        );
        this.instanceCount++;

        return true;
    }

    public BatchStats flush(CommandList commandList) {
        if (!this.batchActive) {
            return BatchStats.EMPTY;
        }

        this.batchActive = false;
        if (this.instanceCount == 0) {
            this.batcher.clear();
            return BatchStats.EMPTY;
        }

        GlProgram<InstanceShader> program;
        try {
            program = this.getProgram();
        } catch (RuntimeException exception) {
            this.supported = false;
            Argentum.LOGGER.error("Model instancing disabled after a shader failure", exception);
            this.batcher.clear();
            return BatchStats.EMPTY;
        }
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        GlStateManager.enableCull();
        GlStateManager.cullFace(GL11.GL_BACK);
        program.bind();
        program.getInterface().setUniforms();
        int previousArray = this.textureArraysSupported ? this.textureArrays.bindFallback() : 0;
        boolean failed = false;
        InstanceBatcher.Stats stats;
        try {
            stats = this.batcher.render(commandList, program);
        } catch (RuntimeException exception) {
            failed = true;
            this.supported = false;
            Argentum.LOGGER.error("Model instancing disabled after a geometry failure", exception);
            stats = new InstanceBatcher.Stats(0, 0);
        } finally {
            if (this.textureArraysSupported) {
                this.textureArrays.restore(previousArray);
            }
            if (failed) {
                GlStateManager.depthMask(true);
                GlStateManager.depthFunc(GL11.GL_LEQUAL);
                GlStateManager.disableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
            program.unbind();
            GlStateManager.enableCull();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.batcher.clear();
        }
        return new BatchStats(this.instanceCount, stats.draws(), stats.textures());
    }

    public void discardBatch() {
        this.batchActive = false;
        this.batcher.clear();
    }

    public void reload(CommandList commandList) {
        this.close(commandList);
    }

    public void close(CommandList commandList) {
        this.discardBatch();
        this.batcher.delete(commandList);
        this.itemGeometry.delete(commandList);
        for (int i = 0; i < this.boxGeometries.length; i++) {
            if (this.boxGeometries[i] != null) {
                this.boxGeometries[i].delete(commandList);
                this.boxGeometries[i] = null;
            }
        }
        this.boxTemplates.clear();
        if (this.arrowGeometry != null) {
            this.arrowGeometry.delete(commandList);
            this.arrowGeometry = null;
        }
        this.programs.values().forEach(GlProgram::delete);
        this.programs.clear();
        this.textureArrays.delete();
        this.initialized = false;
        this.supported = false;
        this.textureArraysSupported = false;
        this.selectedFrame = -1;
        this.selectedTexture = null;
        this.selectedPass = null;
        this.selectedTextureBatch = null;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    private InstanceBatcher.TextureBatch selectTexture(Identifier texture, InstanceRenderPass pass) {
        if (this.selectedFrame == this.frame && this.selectedPass == pass && texture.equals(this.selectedTexture)) {
            return this.selectedTextureBatch;
        }

        TextureArrayManager.Selection selection = null;
        if (this.textureArraysSupported && !TextureAtlas.BLOCKS_LOCATION.equals(texture)) {
            try {
                selection = this.textureArrays.select(texture, this.frame);
            } catch (RuntimeException exception) {
                this.textureArraysSupported = false;
                Argentum.LOGGER.warn("Texture arrays disabled after a copy failure", exception);
            }
        }
        if (selection != null) {
            this.selectedTextureLayer = selection.layer();
            this.selectedTextureBatch = this.batcher.texture(selection.pool(), pass);
        } else {
            this.selectedTextureLayer = 0;
            this.selectedTextureBatch = this.batcher.texture(texture, pass);
        }
        this.selectedFrame = this.frame;
        this.selectedTexture = texture;
        this.selectedPass = pass;
        return this.selectedTextureBatch;
    }

    private boolean initialize() {
        if (this.initialized) {
            return this.supported;
        }

        this.initialized = true;
        var capabilities = GL.getCapabilities();
        this.supported = capabilities.GL_ARB_draw_instanced
                && capabilities.GL_ARB_instanced_arrays
                && capabilities.OpenGL20;
        if (!this.supported) {
            Argentum.LOGGER.warn("Model instancing disabled: required OpenGL extensions are missing");
            return false;
        }

        try {
            try {
                this.textureArraysSupported = this.textureArrays.initialize();
            } catch (RuntimeException exception) {
                Argentum.LOGGER.warn("Texture arrays unavailable", exception);
            }
            try {
                this.getProgram();
            } catch (RuntimeException exception) {
                if (!this.textureArraysSupported) {
                    throw exception;
                }
                Argentum.LOGGER.warn("Texture-array shader unavailable", exception);
                this.programs.values().forEach(GlProgram::delete);
                this.programs.clear();
                this.textureArrays.delete();
                this.textureArraysSupported = false;
                this.getProgram();
            }
            Argentum.LOGGER.info("Model instancing enabled");
        } catch (RuntimeException exception) {
            this.supported = false;
            this.programs.values().forEach(GlProgram::delete);
            this.programs.clear();
            this.textureArrays.delete();
            Argentum.LOGGER.error("Model instancing failed to initialize", exception);
        }
        return this.supported;
    }

    private GlProgram<InstanceShader> getProgram() {
        if (this.programs.isEmpty()) {
            for (ChunkFogMode fogMode : ChunkFogMode.values()) {
                GlProgram<InstanceShader> program = this.createProgram(fogMode);
                program.bind();
                try {
                    program.getInterface().initialize();
                } catch (RuntimeException exception) {
                    program.delete();
                    throw exception;
                } finally {
                    program.unbind();
                }
                this.programs.put(fogMode, program);
            }
        }
        return this.programs.get(ChunkShaderFogComponent.FOG_SERVICE.getFogMode());
    }

    private GlProgram<InstanceShader> createProgram(ChunkShaderComponent.Factory<?> fogFactory) {
        ShaderConstants.Builder constants = ShaderConstants.builder().addAll(fogFactory.getDefines());
        if (this.textureArraysSupported) {
            constants.add("TEXTURE_ARRAY");
        }
        ShaderConstants shaderConstants = constants.build();
        GlShader[] shaders = {
                this.loadShader(ShaderType.VERTEX, "argentum:entity_instancing.vert", shaderConstants),
                this.loadShader(ShaderType.FRAGMENT, "argentum:entity_instancing.frag", shaderConstants)
        };
        try {
            GlProgram.Builder builder = GlProgram.builder("argentum:model_instancing");
            for (GlShader shader : shaders) {
                builder.attachShader(shader);
            }
            return builder
                    .bindAttributes(InstancedVertexFormats.ENTITY_VERTEX, 0)
                    .bindAttributes(InstancedVertexFormats.ENTITY_INSTANCE, InstancedVertexFormats.ENTITY_VERTEX.getAttributes().size())
                    .link(context -> new InstanceShader(context, this.textureArraysSupported, fogFactory));
        } finally {
            for (GlShader shader : shaders) {
                shader.delete();
            }
        }
    }

    private GlShader loadShader(ShaderType type, String path, ShaderConstants constants) {
        String source = ShaderParser.parseShader(ShaderLoader.getShaderSource(path), ShaderLoader::getShaderSource, constants);
        if (!this.textureArrays.usesCoreApi()) {
            String preamble = "#version 120";
            if (this.textureArraysSupported) {
                preamble += "\n#extension GL_EXT_texture_array : require";
            }
            preamble += "\n#define LEGACY\n#define texture texture2D";
            source = VERSION_DIRECTIVE.matcher(source).replaceFirst(preamble);
            source = IN_PARAM.matcher(source).replaceAll(type == ShaderType.VERTEX ? "attribute " : "varying ");
            source = OUT_PARAM.matcher(source).replaceAll("varying ");
        }
        return new GlShader(type, path, source);
    }

    public record BatchStats(int instances, int draws, int textures) {
        private static final BatchStats EMPTY = new BatchStats(0, 0, 0);
    }
}
