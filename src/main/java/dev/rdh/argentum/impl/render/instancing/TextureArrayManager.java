package dev.rdh.argentum.impl.render.instancing;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.AbstractTexture;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL;

import java.util.Map;

public final class TextureArrayManager {
    private static final int MAX_LAYERS = 256;
    private static final int MAX_POOL_BYTES = 8 * 1024 * 1024;

    private final Map<PoolKey, Pool> pools = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<Texture, CachedTexture> textures = new Reference2ObjectOpenHashMap<>();
    private boolean core;
    private int framebuffer;
    private int fallbackTexture;
    private int maxLayers;
    private int boundArray;

    public boolean initialize() {
        var capabilities = GL.getCapabilities();
        this.core = capabilities.OpenGL30;
        if (!this.core && !(capabilities.GL_EXT_texture_array
                && capabilities.GL_EXT_framebuffer_object
                && capabilities.GL_EXT_gpu_shader4)) {
            return false;
        }
        this.maxLayers = Math.min(GL11.glGetInteger(GL30C.GL_MAX_ARRAY_TEXTURE_LAYERS), MAX_LAYERS);
        if (this.maxLayers < 2) {
            return false;
        }
        int activeTexture = GL11.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GlStateManager.activeTexture(GLX.GL_TEXTURE2);
        int previous = this.boundArray;
        try {
            this.fallbackTexture = GL11.glGenTextures();
            this.bindArray(this.fallbackTexture);
            GL11.glTexParameteri(GL30C.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL30C.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL30C.GL_TEXTURE_2D_ARRAY, GL12C.GL_TEXTURE_MAX_LEVEL, 0);
            GL12C.glTexImage3D(GL30C.GL_TEXTURE_2D_ARRAY, 0, GL11.GL_RGBA8, 1, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        } finally {
            this.bindArray(previous);
            GlStateManager.activeTexture(activeTexture);
        }
        this.framebuffer = this.core ? GL30C.glGenFramebuffers() : EXTFramebufferObject.glGenFramebuffersEXT();
        return true;
    }

    public boolean usesCoreApi() {
        return this.core;
    }

    public int bindFallback() {
        GlStateManager.activeTexture(GLX.GL_TEXTURE2);
        int previous = this.boundArray;
        this.bindArray(this.fallbackTexture);
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        return previous;
    }

    public void restore(int texture) {
        GlStateManager.activeTexture(GLX.GL_TEXTURE2);
        this.bindArray(texture);
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
    }

    private void bindArray(int texture) {
        GL11.glBindTexture(GL30C.GL_TEXTURE_2D_ARRAY, texture);
        this.boundArray = texture;
    }

    public Selection select(Identifier location, int frame) {
        Minecraft mc = Minecraft.getInstance();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        Texture source = mc.getTextureManager().get(location);
        if (source == null) {
            mc.getTextureManager().bind(location);
            source = mc.getTextureManager().get(location);
        }

        int sourceId = source.getGlId();
        int generation = source instanceof AbstractTexture texture
                ? texture.argentum$getGeneration() : 0;
        CachedTexture cached = this.textures.get(source);
        if (cached != null && cached.sourceId == sourceId && cached.generation == generation) {
            Layer layer = cached.pool.getLayer(location, source, sourceId, generation, frame);
            return layer != null ? layer.selection : null;
        }

        GlStateManager.bindTexture(sourceId);
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        int minFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        if (width <= 0 || height <= 0 || minFilter >= GL11.GL_NEAREST_MIPMAP_NEAREST) {
            return null;
        }

        PoolKey key = new PoolKey(width, height, minFilter,
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER),
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S),
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T)
        );
        Pool pool = this.pools.get(key);
        if (pool == null) {
            int capacity = Math.clamp(MAX_POOL_BYTES / Math.max(1L, (long)width * height * 4), 2, this.maxLayers);
            pool = new Pool(key, capacity);
            this.pools.put(key, pool);
        }
        this.textures.put(source, new CachedTexture(sourceId, generation, pool));
        Layer layer = pool.getLayer(location, source, sourceId, generation, frame);
        return layer != null ? layer.selection : null;
    }

    public void delete() {
        for (Pool pool : this.pools.values()) {
            GL11.glDeleteTextures(pool.texture);
        }
        this.pools.clear();
        this.textures.clear();
        this.boundArray = 0;
        if (this.fallbackTexture != 0) {
            GL11.glDeleteTextures(this.fallbackTexture);
            this.fallbackTexture = 0;
        }
        if (this.framebuffer != 0) {
            if (this.core) {
                GL30C.glDeleteFramebuffers(this.framebuffer);
            } else {
                EXTFramebufferObject.glDeleteFramebuffersEXT(this.framebuffer);
            }
            this.framebuffer = 0;
        }
        this.maxLayers = 0;
    }

    public record Selection(Pool pool, int layer) {
    }

    public final class Pool {
        private final Object2ObjectLinkedOpenHashMap<Identifier, Layer> layers = new Object2ObjectLinkedOpenHashMap<>();
        private final PoolKey key;
        private final int texture;
        private final int capacity;

        private Pool(PoolKey key, int capacity) {
            this.key = key;
            this.capacity = capacity;
            int activeTexture = GL11.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            GlStateManager.activeTexture(GLX.GL_TEXTURE2);
            int previous = boundArray;
            try {
                this.texture = GL11.glGenTextures();
                bindArray(this.texture);
                GL11.glTexParameteri(GL30C.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, key.minFilter);
                GL11.glTexParameteri(GL30C.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, key.magFilter);
                GL11.glTexParameteri(GL30C.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, key.wrapS);
                GL11.glTexParameteri(GL30C.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, key.wrapT);
                GL11.glTexParameteri(GL30C.GL_TEXTURE_2D_ARRAY, GL12C.GL_TEXTURE_MAX_LEVEL, 0);
                GL12C.glTexImage3D(GL30C.GL_TEXTURE_2D_ARRAY, 0, GL11.GL_RGBA8,
                        key.width, key.height, capacity, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L
                );
            } finally {
                bindArray(previous);
                GlStateManager.activeTexture(activeTexture);
            }
        }

        public int bind() {
            GlStateManager.activeTexture(GLX.GL_TEXTURE2);
            int previous = boundArray;
            bindArray(this.texture);
            GlStateManager.activeTexture(GLX.GL_TEXTURE0);
            return previous;
        }

        public void restore(int texture) {
            GlStateManager.activeTexture(GLX.GL_TEXTURE2);
            bindArray(texture);
            GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        }

        private Layer getLayer(Identifier location, Texture source, int sourceId, int generation, int frame) {
            Layer layer = this.layers.getAndMoveToLast(location);
            if (layer != null && layer.source == source && layer.sourceId == sourceId && layer.generation == generation) {
                layer.frame = frame;
                return layer;
            }
            if (layer == null) {
                int index = this.layers.size();
                if (index == this.capacity) {
                    ObjectIterator<Layer> iterator = this.layers.values().iterator();
                    while (iterator.hasNext()) {
                        Layer candidate = iterator.next();
                        if (candidate.frame != frame) {
                            index = candidate.index;
                            iterator.remove();
                            break;
                        }
                    }
                    if (index == this.capacity) {
                        return null;
                    }
                }
                layer = new Layer(index, new Selection(this, index));
                this.layers.put(location, layer);
            }
            if (!copy(sourceId, layer.index)) {
                return null;
            }
            layer.source = source;
            layer.sourceId = sourceId;
            layer.generation = generation;
            layer.frame = frame;
            return layer;
        }

        private boolean copy(int sourceTexture, int layer) {
            int previousFramebuffer = GL11.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
            int activeTexture = GL11.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            GlStateManager.activeTexture(GLX.GL_TEXTURE2);
            int previousArray = boundArray;
            try {
                bindFramebuffer(framebuffer);
                attachTexture(sourceTexture);
                if (!isFramebufferComplete()) {
                    return false;
                }
                bindArray(this.texture);
                GL12C.glCopyTexSubImage3D(GL30C.GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, 0, 0, this.key.width, this.key.height);
                return true;
            } finally {
                bindArray(previousArray);
                GlStateManager.activeTexture(activeTexture);
                bindFramebuffer(previousFramebuffer);
            }
        }

    }

    private void bindFramebuffer(int framebuffer) {
        if (this.core) {
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
        } else {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, framebuffer);
        }
    }

    private void attachTexture(int texture) {
        if (this.core) {
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        } else {
            EXTFramebufferObject.glFramebufferTexture2DEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT, GL11.GL_TEXTURE_2D, texture, 0);
        }
    }

    private boolean isFramebufferComplete() {
        return this.core
                ? GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) == GL30C.GL_FRAMEBUFFER_COMPLETE
                : EXTFramebufferObject.glCheckFramebufferStatusEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT)
                == EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT;
    }

    private record PoolKey(int width, int height, int minFilter, int magFilter, int wrapS, int wrapT) {
    }

    private record CachedTexture(int sourceId, int generation, Pool pool) {
    }

    private static final class Layer {
        private final int index;
        private final Selection selection;
        private Texture source;
        private int sourceId = -1;
        private int generation = -1;
        private int frame;

        private Layer(int index, Selection selection) {
            this.index = index;
            this.selection = selection;
        }
    }
}
