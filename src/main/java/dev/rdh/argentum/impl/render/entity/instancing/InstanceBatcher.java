package dev.rdh.argentum.impl.render.entity.instancing;

import java.util.Arrays;
import dev.rdh.argentum.impl.render.instancing.BoxTemplate;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import dev.rdh.argentum.impl.render.instancing.TextureArrayManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.resource.Identifier;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.joml.Matrix4f;
import org.joml.Vector4fc;
import org.lwjgl.opengl.GL11;

import java.util.EnumMap;
import java.util.Map;

final class InstanceBatcher {
    private final Map<Model, ModelGeometry> models = new Reference2ReferenceOpenHashMap<>();
    private final Matrix4f[] itemGlintMatrices = { new Matrix4f(), new Matrix4f() };
    private final boolean[] itemGlintCaptured = new boolean[2];
    private final float[] matrixValues = new float[16];
    private final EnumMap<InstanceRenderPass, Map<Identifier, TextureBatch>> textures = new EnumMap<>(InstanceRenderPass.class);
    private final EnumMap<InstanceRenderPass, Map<TextureArrayManager.Pool, TextureBatch>> arrayTextures = new EnumMap<>(InstanceRenderPass.class);

    InstanceBatcher() {
        for (InstanceRenderPass pass : InstanceRenderPass.values()) {
            this.textures.put(pass, new Object2ObjectLinkedOpenHashMap<>());
            this.arrayTextures.put(pass, new Reference2ReferenceOpenHashMap<>());
        }
    }

    void clear() {
        this.textures.values().forEach(map -> map.values().forEach(TextureBatch::clear));
        this.arrayTextures.values().forEach(map -> map.values().forEach(TextureBatch::clear));
        Arrays.fill(this.itemGlintCaptured, false);
    }

    void captureItemGlintMatrix(int pass) {
        if (pass < 0 || pass >= this.itemGlintCaptured.length || this.itemGlintCaptured[pass]) {
            return;
        }
        GL11.glGetFloatv(GL11.GL_TEXTURE_MATRIX, this.matrixValues);
        this.itemGlintMatrices[pass].set(this.matrixValues);
        this.itemGlintCaptured[pass] = true;
    }

    ModelGeometry model(Model model) {
        return this.models.computeIfAbsent(model, ignored -> new ModelGeometry());
    }

    void delete(CommandList commandList) {
        this.models.values().forEach(model -> model.delete(commandList));
        this.models.clear();
        this.textures.values().forEach(Map::clear);
        this.arrayTextures.values().forEach(Map::clear);
    }

    TextureBatch texture(Identifier texture, InstanceRenderPass pass) {
        return this.textures.get(pass).computeIfAbsent(texture, TextureBatch::new);
    }

    TextureBatch texture(TextureArrayManager.Pool pool, InstanceRenderPass pass) {
        return this.arrayTextures.get(pass).computeIfAbsent(pool, ignored -> new TextureBatch(null));
    }

    Stats render(CommandList commandList, GlProgram<InstanceShader> program) {
        int draws = 0;
        int textureCount = 0;
        GlStateManager.disableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Stats normal = this.renderPass(commandList, program, InstanceRenderPass.NORMAL);
        draws += normal.draws;
        textureCount += normal.textures;
        if (this.has(InstanceRenderPass.NO_CULL)) {
            GlStateManager.disableCull();
            Stats unculled = this.renderPass(commandList, program, InstanceRenderPass.NO_CULL);
            draws += unculled.draws;
            textureCount += unculled.textures;
            GlStateManager.enableCull();
        }
        if (this.has(InstanceRenderPass.CULL_FRONT)) {
            GlStateManager.cullFace(GL11.GL_FRONT);
            Stats culled = this.renderPass(commandList, program, InstanceRenderPass.CULL_FRONT);
            draws += culled.draws;
            textureCount += culled.textures;
            GlStateManager.cullFace(GL11.GL_BACK);
        }
        if (this.has(InstanceRenderPass.CULL_BACK)) {
            Stats culled = this.renderPass(commandList, program, InstanceRenderPass.CULL_BACK);
            draws += culled.draws;
            textureCount += culled.textures;
        }
        if (this.has(InstanceRenderPass.EMISSIVE_REPLACE)) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.depthMask(false);
            Stats replace = this.renderPass(commandList, program, InstanceRenderPass.EMISSIVE_REPLACE);
            draws += replace.draws;
            textureCount += replace.textures;
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
        }
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (this.has(InstanceRenderPass.ITEM)) {
            var textureManager = Minecraft.getInstance().getTextureManager();
            textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
            var blockAtlas = textureManager.get(TextureAtlas.BLOCKS_LOCATION);
            blockAtlas.pushFilter(false, false);
            try {
                Stats items = this.renderPass(commandList, program, InstanceRenderPass.ITEM);
                draws += items.draws;
                textureCount += items.textures;
            } finally {
                textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
                blockAtlas.popFilter();
            }
        }
        if (this.has(InstanceRenderPass.TRANSLUCENT)) {
            GlStateManager.disableBlend();
            program.getInterface().setAlphaPass(InstanceShader.ALPHA_OPAQUE);
            Stats opaque = this.renderPass(commandList, program, InstanceRenderPass.TRANSLUCENT);
            draws += opaque.draws;
            textureCount += opaque.textures;

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.depthMask(false);
            program.getInterface().setAlphaPass(InstanceShader.ALPHA_TRANSLUCENT);
            Stats translucent = this.renderPass(commandList, program, InstanceRenderPass.TRANSLUCENT);
            draws += translucent.draws;
            textureCount += translucent.textures;
            GlStateManager.depthMask(true);
            program.getInterface().setAlphaPass(InstanceShader.ALPHA_ALL);
        }
        GlStateManager.blendFunc(1, 1);
        // Additive emissive overlays sit on already-drawn geometry; writing depth only makes their
        // (uncensored, non-culled) coplanar faces z-fight, so test against the base depth without writing.
        GlStateManager.depthMask(false);
        Stats emissive = this.renderPass(commandList, program, InstanceRenderPass.EMISSIVE);
        GlStateManager.depthMask(true);
        draws += emissive.draws;
        textureCount += emissive.textures;
        Stats charge = this.renderPass(commandList, program, InstanceRenderPass.CREEPER_CHARGE);
        draws += charge.draws;
        textureCount += charge.textures;
        charge = this.renderPass(commandList, program, InstanceRenderPass.WITHER_CHARGE);
        draws += charge.draws;
        textureCount += charge.textures;
        GlStateManager.disableBlend();
        GlStateManager.blendFunc(770, 771);
        if (this.has(InstanceRenderPass.GLINT)
                || this.has(InstanceRenderPass.ITEM_GLINT_0)
                || this.has(InstanceRenderPass.ITEM_GLINT_1)) {
            GlStateManager.enableBlend();
            GlStateManager.depthFunc(GL11.GL_EQUAL);
            GlStateManager.depthMask(false);
            GlStateManager.blendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
            program.getInterface().setGlintPass(0);
            Stats glint = this.renderPass(commandList, program, InstanceRenderPass.GLINT);
            draws += glint.draws;
            textureCount += glint.textures;
            program.getInterface().setGlintPass(1);
            glint = this.renderPass(commandList, program, InstanceRenderPass.GLINT);
            draws += glint.draws;
            textureCount += glint.textures;
            program.getInterface().setGlintPass(-1);
            program.getInterface().setItemGlintPass(0, this.itemGlintMatrices[0]);
            glint = this.renderPass(commandList, program, InstanceRenderPass.ITEM_GLINT_0);
            draws += glint.draws;
            textureCount += glint.textures;
            program.getInterface().setItemGlintPass(1, this.itemGlintMatrices[1]);
            glint = this.renderPass(commandList, program, InstanceRenderPass.ITEM_GLINT_1);
            draws += glint.draws;
            textureCount += glint.textures;
            program.getInterface().setItemGlintPass(-1);
            GlStateManager.depthMask(true);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.disableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        return new Stats(draws, textureCount);
    }

    private boolean has(InstanceRenderPass pass) {
        for (TextureBatch batch : this.textures.get(pass).values()) {
            if (batch.count != 0) return true;
        }
        for (TextureBatch batch : this.arrayTextures.get(pass).values()) {
            if (batch.count != 0) return true;
        }
        return false;
    }

    private Stats renderPass(CommandList commandList, GlProgram<InstanceShader> program, InstanceRenderPass pass) {
        if (!this.has(pass)) return Stats.EMPTY;

        int draws = 0;
        int textureCount = 0;
        program.getInterface().setLighting(pass.isBase(),
                pass != InstanceRenderPass.EMISSIVE && pass != InstanceRenderPass.EMISSIVE_REPLACE);
        program.getInterface().setChargePass(pass.chargePass);
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (TextureBatch texture : this.textures.get(pass).values()) {
            if (texture.count == 0) continue;
            textureManager.bind(texture.texture);
            program.getInterface().setTextureArray(false);
            draws += texture.render(commandList, program, pass == InstanceRenderPass.TRANSLUCENT);
            textureCount++;
        }
        for (var entry : this.arrayTextures.get(pass).entrySet()) {
            if (entry.getValue().count == 0) continue;
            int previous = entry.getKey().bind();
            program.getInterface().setTextureArray(true);
            draws += entry.getValue().render(commandList, program, pass == InstanceRenderPass.TRANSLUCENT);
            entry.getKey().restore(previous);
            textureCount++;
        }
        return new Stats(draws, textureCount);
    }

    record Stats(int draws, int textures) {
        static final Stats EMPTY = new Stats(0, 0);
    }

    static final class TextureBatch {
        private final Identifier texture;
        private final Reference2ObjectLinkedOpenHashMap<InstanceGeometry, Instances> parts = new Reference2ObjectLinkedOpenHashMap<>();
        private int count;

        private TextureBatch(Identifier texture) {
            this.texture = texture;
        }

        private void clear() {
            this.parts.values().forEach(Instances::clear);
            this.count = 0;
        }

        void add(InstanceGeometry geometry, Matrix4f matrix, float u, float v, int layer,
                Vector4fc color, float effectTime, Vector4fc overlayColor, BoxTemplate box) {
            geometry.instances(this).add(matrix, u, v, layer, color, effectTime, overlayColor, box);
            this.count++;
        }

        Instances instances(InstanceGeometry geometry) {
            Instances instances = this.parts.get(geometry);
            if (instances == null) {
                instances = new Instances();
                this.parts.put(geometry, instances);
            }
            return instances;
        }

        private int render(CommandList commandList, GlProgram<InstanceShader> program, boolean sort) {
            int draws = 0;
            for (var entry : this.parts.reference2ObjectEntrySet()) {
                if (entry.getValue().count() != 0) {
                    if (sort) {
                        entry.getValue().sortBackToFront();
                    }
                    program.getInterface().setBoxInstancing(entry.getKey().usesBoxInstancing());
                    entry.getKey().render(commandList, entry.getValue());
                    draws++;
                }
            }
            return draws;
        }
    }
}
