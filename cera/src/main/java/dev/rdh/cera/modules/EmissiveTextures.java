package dev.rdh.cera.modules;

import dev.rdh.cera.Cera;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.resource.Identifier;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

public final class EmissiveTextures {
    // used for a multi-texture entity that only has emissives for some of its textures (this is used for the rest)
    private static final Identifier EMPTY = new Identifier("cera", "textures/empty.png");
    private static final Identifier NONE = new Identifier("cera", "none");
    private static final Map<BakedQuad, BakedQuad> RESPRITED = Collections.synchronizedMap(new WeakHashMap<>());

    private volatile String suffix;
    private volatile Map<TextureAtlasSprite, TextureAtlasSprite> spriteMap = Map.of();

    private final Map<Identifier, Identifier> boundCache = new Object2ObjectOpenHashMap<>();
    private boolean rendering;
    private boolean renderingEmissive;
    private boolean hasEmissive;

    private static float lastBrightnessX;
    private static float lastBrightnessY;
    private float savedBrightnessX;
    private float savedBrightnessY;

    public boolean active() {
        return suffix != null && Cera.CONFIG.emissiveTextures;
    }

    public String suffix() {
        return suffix;
    }

    public void reload(ResourceManager resources, TextureAtlas atlas, Map<String, TextureAtlasSprite> sourcedSprites) {
        this.suffix = load(resources);
        this.spriteMap = Map.of();
        this.boundCache.clear();
        RESPRITED.clear();
        if (suffix == null) return;
        Map<TextureAtlasSprite, TextureAtlasSprite> spriteMap = new Reference2ReferenceOpenHashMap<>();
        for (var entry : new ArrayList<>(sourcedSprites.entrySet())) {
            Identifier id = new Identifier(entry.getKey());
            if (id.getPath().endsWith(suffix)) continue;
            Identifier em = new Identifier(id.getNamespace(), id.getPath() + suffix);
            if (!resources.hasResource(new Identifier(id.getNamespace(), "textures/" + em.getPath() + ".png"))) continue;
            TextureAtlasSprite sprite = sourcedSprites.get(em.toString());
            if (sprite == null) sprite = atlas.registerSprite(em);
            spriteMap.put(entry.getValue(), sprite);
        }
        this.spriteMap = spriteMap;
    }

    public TextureAtlasSprite emissiveSprite(TextureAtlasSprite base) {
        return active() ? spriteMap.get(base) : null;
    }

    public static BakedQuad resprite(BakedQuad quad, TextureAtlasSprite from, TextureAtlasSprite to) {
        BakedQuad cached = RESPRITED.get(quad);
        if (cached != null) return cached;
        int[] v = quad.getVertices().clone();
        int stride = v.length / 4;
        for (int i = 0; i < 4; i++) {
            int o = i * stride;
            v[o + 4] = Float.floatToRawIntBits(remap(Float.intBitsToFloat(v[o + 4]), from.getUMin(), from.getUMax(), to.getUMin(), to.getUMax()));
            v[o + 5] = Float.floatToRawIntBits(remap(Float.intBitsToFloat(v[o + 5]), from.getVMin(), from.getVMax(), to.getVMin(), to.getVMax()));
        }
        BakedQuad resprited = new BakedQuad(v, quad.getTintIndex(), quad.getFace());
        RESPRITED.put(quad, resprited);
        return resprited;
    }

    private static float remap(float x, float a0, float a1, float b0, float b1) {
        return b0 + (x - a0) / (a1 - a0) * (b1 - b0);
    }

    public void beginRender() {
        this.rendering = true;
        this.hasEmissive = false;
    }

    public boolean hasEmissive() {
        return this.hasEmissive;
    }

    public void beginRenderEmissive() {
        this.renderingEmissive = true;
        forceFullbright();
    }

    public void endRenderEmissive() {
        this.renderingEmissive = false;
        restoreBrightness();
    }

    public void forceFullbright() {
        this.savedBrightnessX = lastBrightnessX;
        this.savedBrightnessY = lastBrightnessY;
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, 240.0F, this.savedBrightnessY);
    }

    public void restoreBrightness() {
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, this.savedBrightnessX, this.savedBrightnessY);
    }

    public static void captureBrightness(float x, float y) {
        lastBrightnessX = x;
        lastBrightnessY = y;
    }

    public void endRender() {
        this.rendering = false;
    }

    public Identifier resolveBound(Identifier loc) {
        if (!this.rendering) return loc;
        Identifier emissive = emissiveLocation(loc);
        if (!this.renderingEmissive) {
            if (emissive != null) this.hasEmissive = true;
            return loc;
        }
        return emissive != null ? emissive : EMPTY;
    }

    public Identifier emissiveTexture(Identifier loc) {
        return active() ? emissiveLocation(loc) : null;
    }

    private Identifier emissiveLocation(Identifier loc) {
        Identifier cached = this.boundCache.get(loc);
        if (cached == null) {
            cached = compute(loc);
            this.boundCache.put(loc, cached == null ? NONE : cached);
        }
        return cached == NONE ? null : cached;
    }

    private Identifier compute(Identifier loc) {
        String path = loc.getPath();
        if (!path.endsWith(".png")) return null;
        String base = path.substring(0, path.length() - ".png".length());
        if (base.endsWith(suffix)) return null;
        Identifier emissive = new Identifier(loc.getNamespace(), base + suffix + ".png");
        return ResourceManager.client().hasResource(emissive) ? emissive : null;
    }

    private static String load(ResourceManager resources) {
        Resource resource = resources.getResource(new Identifier("optifine/emissive.properties")).orElse(null);
        if (resource == null) return null;
        Properties props = new Properties();
        try (resource; InputStream in = resource.open()) {
            props.load(in);
        } catch (IOException e) {
            Cera.LOGGER.warn("[EmissiveTextures] Failed to read emissive.properties", e);
            return null;
        }
        return props.getProperty("suffix.emissive");
    }
}
