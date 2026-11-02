package dev.rdh.argentum.impl.render.entity.instancing;

import java.util.Arrays;
import dev.rdh.argentum.api.render.instancing.BoxTemplate;
import dev.rdh.argentum.api.render.instancing.InstanceDrawTarget;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import dev.rdh.argentum.api.render.instancing.TextureArrayManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.resource.Identifier;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;
import org.lwjgl.opengl.GL11;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class InstanceBatcher {
    private final Map<Model, ModelGeometry> models = new Reference2ReferenceOpenHashMap<>();
    private final Matrix4f[] itemGlintMatrices = { new Matrix4f(), new Matrix4f() };
    private final boolean[] itemGlintCaptured = new boolean[2];
    private final EnumMap<InstanceRenderPass, Map<BatchKey, TextureBatch>> textures = new EnumMap<>(InstanceRenderPass.class);
    private final EnumMap<InstanceRenderPass, Map<TextureArrayManager.Pool, TextureBatch>> arrayTextures = new EnumMap<>(InstanceRenderPass.class);
    private final EnumMap<InstanceRenderPass, List<TextureBatch>> queued = new EnumMap<>(InstanceRenderPass.class);
    private final IntArrayList standardOffsets = new IntArrayList();
    private int standardDraws;
    private int standardTextures;

    InstanceBatcher() {
        for (InstanceRenderPass pass : InstanceRenderPass.values()) {
            this.textures.put(pass, new Object2ObjectLinkedOpenHashMap<>());
            this.arrayTextures.put(pass, new Reference2ReferenceOpenHashMap<>());
            this.queued.put(pass, new ObjectArrayList<>());
        }
    }

    void clear() {
        this.textures.values().forEach(map -> map.values().removeIf(TextureBatch::expire));
        this.arrayTextures.values().forEach(map -> map.values().forEach(TextureBatch::clear));
        this.queued.values().forEach(List::clear);
        Arrays.fill(this.itemGlintCaptured, false);
    }

    void captureItemGlintMatrix(int pass, Matrix4fc matrix) {
        if (pass < 0 || pass >= this.itemGlintCaptured.length || this.itemGlintCaptured[pass]) {
            return;
        }
        this.itemGlintMatrices[pass].set(matrix);
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
        this.queued.values().forEach(List::clear);
    }

    TextureBatch texture(Identifier texture, InstanceKind kind, int id, InstanceRenderPass pass) {
        return this.queue(this.textures.get(pass).computeIfAbsent(new BatchKey(texture, kind, id), key -> new TextureBatch(texture, null, kind, id)), pass);
    }

    TextureBatch texture(TextureArrayManager.Pool pool, InstanceRenderPass pass) {
        return this.queue(this.arrayTextures.get(pass).computeIfAbsent(pool, key -> new TextureBatch(null, key, InstanceKind.ENTITY, 0)), pass);
    }

    private TextureBatch queue(TextureBatch batch, InstanceRenderPass pass) {
        if (!batch.queued) {
            batch.queued = true;
            this.queued.get(pass).add(batch);
        }
        return batch;
    }

    Stats render(CommandList commandList, GlProgram<InstanceShader> program) {
        int draws = 0;
        int textureCount = 0;
        GlStateManager.disableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();
        Stats normal = this.renderPass(commandList, program, InstanceRenderPass.NORMAL);
        draws += normal.draws;
        textureCount += normal.textures;
        if (this.has(InstanceRenderPass.NO_CULL)) {
            Stats unculled = this.renderPass(commandList, program, InstanceRenderPass.NO_CULL);
            draws += unculled.draws;
            textureCount += unculled.textures;
        }
        GlStateManager.enableCull();
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
            GlStateManager.enablePolygonOffset();
            GlStateManager.polygonOffset(-1.0F, -1.0F);
            Stats replace = this.renderPass(commandList, program, InstanceRenderPass.EMISSIVE_REPLACE);
            GlStateManager.disablePolygonOffset();
            draws += replace.draws;
            textureCount += replace.textures;
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
        }
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        boolean hasTranslucent = this.has(InstanceRenderPass.TRANSLUCENT);
        if (hasTranslucent) {
            program.getInterface().setAlphaPass(InstanceShader.ALPHA_OPAQUE);
        }
        Stats items = this.renderItems(commandList, program, InstanceRenderPass.ITEM);
        draws += items.draws;
        textureCount += items.textures;
        GlStateManager.disableCull();
        items = this.renderItems(commandList, program, InstanceRenderPass.ITEM_NO_CULL);
        draws += items.draws;
        textureCount += items.textures;
        if (hasTranslucent) {
            GlStateManager.disableBlend();
            program.getInterface().setAlphaPass(InstanceShader.ALPHA_OPAQUE);
            Stats opaque = this.renderPass(commandList, program, InstanceRenderPass.TRANSLUCENT);
            draws += opaque.draws;
            textureCount += opaque.textures;

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            program.getInterface().setAlphaPass(InstanceShader.ALPHA_TRANSLUCENT);
            Stats translucent = this.renderPass(commandList, program, InstanceRenderPass.TRANSLUCENT);
            draws += translucent.draws;
            textureCount += translucent.textures;
            GlStateManager.enableCull();
            items = this.renderItems(commandList, program, InstanceRenderPass.ITEM);
            draws += items.draws;
            textureCount += items.textures;
            GlStateManager.disableCull();
            items = this.renderItems(commandList, program, InstanceRenderPass.ITEM_NO_CULL);
            draws += items.draws;
            textureCount += items.textures;
            program.getInterface().setAlphaPass(InstanceShader.ALPHA_ALL);
        }
        GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
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
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (this.has(InstanceRenderPass.GLINT) || this.has(InstanceRenderPass.ITEM_GLINT)) {
            GlStateManager.enableBlend();
            GlStateManager.depthFunc(GL11.GL_EQUAL);
            GlStateManager.depthMask(false);
            GlStateManager.blendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
            program.getInterface().setGlintPass(0);
            Stats glint = this.renderPass(commandList, program, InstanceRenderPass.GLINT);
            draws += glint.draws;
            textureCount += glint.textures;
            program.getInterface().setGlintPass(-1);
            program.getInterface().setItemGlintPass(this.itemGlintCaptured[1] ? 1 : 0, this.itemGlintMatrices);
            glint = this.renderPass(commandList, program, InstanceRenderPass.ITEM_GLINT);
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

    Stats renderStandard(CommandList commandList, InstanceDrawTarget target, StandardVertexStream vertices) {
        this.standardDraws = 0;
        this.standardTextures = 0;
        vertices.begin();
        GlStateManager.disableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();
        this.renderStandardPass(commandList, target, vertices, InstanceRenderPass.NORMAL);
        this.renderStandardPass(commandList, target, vertices, InstanceRenderPass.NO_CULL);
        GlStateManager.enableCull();
        if (this.has(InstanceRenderPass.CULL_FRONT)) {
            GlStateManager.cullFace(GL11.GL_FRONT);
            this.renderStandardPass(commandList, target, vertices, InstanceRenderPass.CULL_FRONT);
            GlStateManager.cullFace(GL11.GL_BACK);
        }
        this.renderStandardPass(commandList, target, vertices, InstanceRenderPass.CULL_BACK);
        if (this.has(InstanceRenderPass.EMISSIVE_REPLACE)) {
            GlStateManager.enableBlend();
            GlStateManager.depthMask(false);
            GlStateManager.enablePolygonOffset();
            GlStateManager.polygonOffset(-1.0F, -1.0F);
            this.renderStandardPass(commandList, target, vertices, InstanceRenderPass.EMISSIVE_REPLACE);
            GlStateManager.disablePolygonOffset();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
        }
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        this.renderStandardItems(commandList, target, vertices, InstanceRenderPass.ITEM);
        GlStateManager.disableCull();
        this.renderStandardItems(commandList, target, vertices, InstanceRenderPass.ITEM_NO_CULL);
        this.renderStandardPass(commandList, target, vertices, InstanceRenderPass.TRANSLUCENT);
        GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GlStateManager.depthMask(false);
        this.renderStandardPass(commandList, target, vertices, InstanceRenderPass.EMISSIVE);
        GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GlStateManager.depthMask(true);
        this.renderStandardPass(commandList, target, vertices, InstanceRenderPass.CREEPER_CHARGE);
        this.renderStandardPass(commandList, target, vertices, InstanceRenderPass.WITHER_CHARGE);
        GlStateManager.disableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (this.has(InstanceRenderPass.GLINT) || this.has(InstanceRenderPass.ITEM_GLINT)) {
            GlStateManager.enableBlend();
            GlStateManager.depthFunc(GL11.GL_EQUAL);
            GlStateManager.depthMask(false);
            GlStateManager.blendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
            this.renderStandardPass(commandList, target, vertices, InstanceRenderPass.GLINT);
            this.renderStandardPass(commandList, target, vertices, InstanceRenderPass.ITEM_GLINT);
            GlStateManager.depthMask(true);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.disableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        return new Stats(this.standardDraws, this.standardTextures);
    }

    private void renderStandardItems(CommandList commandList, InstanceDrawTarget target, StandardVertexStream vertices, InstanceRenderPass pass) {
        if (!this.has(pass)) return;
        var textureManager = Minecraft.getInstance().getTextureManager();
        textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
        var blockAtlas = textureManager.get(TextureAtlas.BLOCKS_LOCATION);
        blockAtlas.pushFilter(false, false);
        try {
            this.renderStandardPass(commandList, target, vertices, pass);
        } finally {
            textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
            blockAtlas.popFilter();
        }
    }

    private void renderStandardPass(CommandList commandList, InstanceDrawTarget target, StandardVertexStream vertices, InstanceRenderPass pass) {
        if (!this.has(pass)) return;

        List<TextureBatch> batches = this.queued.get(pass);
        int layers = pass == InstanceRenderPass.GLINT ? 2
                : pass == InstanceRenderPass.ITEM_GLINT && this.itemGlintCaptured[1] ? 2 : 1;
        vertices.reset();
        this.standardOffsets.clear();
        for (int layer = 0; layer < layers; layer++) {
            Matrix4fc glintMatrix = pass == InstanceRenderPass.ITEM_GLINT ? this.itemGlintMatrices[layer] : null;
            for (TextureBatch batch : batches) {
                if (batch.count == 0) continue;
                this.standardOffsets.add(vertices.count());
                batch.putStandard(vertices, pass, layer, glintMatrix);
            }
        }
        this.standardOffsets.add(vertices.count());
        vertices.upload(commandList);

        var textureManager = Minecraft.getInstance().getTextureManager();
        target.beginPass(pass);
        try {
            int draw = 0;
            for (int layer = 0; layer < layers; layer++) {
                for (TextureBatch batch : batches) {
                    if (batch.count == 0) continue;
                    target.beginDraw(batch.kind, batch.id);
                    textureManager.bind(batch.texture);
                    int first = this.standardOffsets.getInt(draw++);
                    vertices.draw(first, this.standardOffsets.getInt(draw) - first);
                    this.standardDraws++;
                }
            }
        } finally {
            target.endPass(pass);
        }
        this.standardTextures += batches.size();
    }

    private Stats renderItems(CommandList commandList, GlProgram<InstanceShader> program, InstanceRenderPass pass) {
        if (!this.has(pass)) return Stats.EMPTY;
        var textureManager = Minecraft.getInstance().getTextureManager();
        textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
        var blockAtlas = textureManager.get(TextureAtlas.BLOCKS_LOCATION);
        blockAtlas.pushFilter(false, false);
        try {
            return this.renderPass(commandList, program, pass);
        } finally {
            textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
            blockAtlas.popFilter();
        }
    }

    private boolean has(InstanceRenderPass pass) {
        for (TextureBatch batch : this.queued.get(pass)) {
            if (batch.count != 0) return true;
        }
        return false;
    }

    private Stats renderPass(CommandList commandList, GlProgram<InstanceShader> program, InstanceRenderPass pass) {
        if (!this.has(pass)) return Stats.EMPTY;

        int draws = 0;
        int textureCount = 0;
        program.getInterface().setLighting(pass.isBase() || pass == InstanceRenderPass.EMISSIVE || pass == InstanceRenderPass.EMISSIVE_REPLACE,
                true, pass == InstanceRenderPass.EMISSIVE, pass == InstanceRenderPass.EMISSIVE_REPLACE);
        program.getInterface().setChargePass(pass.chargePass);
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (TextureBatch batch : this.queued.get(pass)) {
            if (batch.count == 0) continue;
            if (batch.pool == null) {
                textureManager.bind(batch.texture);
                program.getInterface().setTextureArray(false);
                draws += batch.render(commandList, program, pass == InstanceRenderPass.TRANSLUCENT);
            } else {
                int previous = batch.pool.bind();
                program.getInterface().setTextureArray(true);
                draws += batch.render(commandList, program, pass == InstanceRenderPass.TRANSLUCENT);
                batch.pool.restore(previous);
            }
            textureCount++;
        }
        return new Stats(draws, textureCount);
    }

    private record BatchKey(Identifier texture, InstanceKind kind, int id) {
    }

    record Stats(int draws, int textures) {
        static final Stats EMPTY = new Stats(0, 0);
    }

    static final class TextureBatch {
        private final Identifier texture;
        private final TextureArrayManager.Pool pool;
        private final InstanceKind kind;
        private final int id;
        private final Reference2ObjectLinkedOpenHashMap<InstanceGeometry, Instances> parts = new Reference2ObjectLinkedOpenHashMap<>();
        private int count;
        private boolean queued;
        private int idleFlushes;

        private TextureBatch(Identifier texture, TextureArrayManager.Pool pool, InstanceKind kind, int id) {
            this.texture = texture;
            this.pool = pool;
            this.kind = kind;
            this.id = id;
        }

        private void clear() {
            this.parts.values().forEach(Instances::clear);
            this.count = 0;
            this.queued = false;
        }

        private boolean expire() {
            this.idleFlushes = this.queued ? 0 : this.idleFlushes + 1;
            this.clear();
            return this.idleFlushes > 600;
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

        private void putStandard(StandardVertexStream vertices, InstanceRenderPass pass, int layer, Matrix4fc glintMatrix) {
            for (var entry : this.parts.reference2ObjectEntrySet()) {
                if (entry.getValue().count() != 0) {
                    if (pass == InstanceRenderPass.TRANSLUCENT) {
                        entry.getValue().sortBackToFront();
                    }
                    vertices.put(entry.getKey(), entry.getValue(), pass, layer, glintMatrix);
                }
            }
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
