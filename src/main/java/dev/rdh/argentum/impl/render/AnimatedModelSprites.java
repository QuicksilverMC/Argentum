package dev.rdh.argentum.impl.render;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;

import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.util.math.Direction;

import org.embeddedt.embeddium.impl.model.quad.BakedQuadView;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class AnimatedModelSprites {
    private static final TextureAtlasSprite[] NONE = new TextureAtlasSprite[0];
    private static final Map<BakedModel, TextureAtlasSprite[]> SPRITES = new Reference2ObjectOpenHashMap<>();

    private AnimatedModelSprites() {
    }

    public static TextureAtlasSprite[] of(BakedModel model) {
        TextureAtlasSprite[] sprites = SPRITES.get(model);
        if (sprites == null) SPRITES.put(model, sprites = scan(model));
        return sprites;
    }

    public static void clear() {
        SPRITES.clear();
    }

    private static TextureAtlasSprite[] scan(BakedModel model) {
        var found = new ReferenceLinkedOpenHashSet<TextureAtlasSprite>();
        collect(model.getQuads(), found);
        for (Direction face : Direction.values()) {
            collect(model.getQuads(face), found);
        }

        TextureAtlasSprite particle = model.getParticleIcon();
        if (particle != null && particle.isAnimated()) found.add(particle);
        return found.isEmpty() ? NONE : found.toArray(NONE);
    }

    private static void collect(List<BakedQuad> quads, Collection<TextureAtlasSprite> found) {
        if (quads == null) return;
        for (BakedQuad quad : quads) {
            if (BakedQuadView.of(quad).celeritas$getSprite() instanceof TextureAtlasSprite sprite && sprite.isAnimated()) {
                found.add(sprite);
            }
        }
    }
}
