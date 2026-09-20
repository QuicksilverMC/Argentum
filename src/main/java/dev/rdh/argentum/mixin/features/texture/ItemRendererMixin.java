package dev.rdh.argentum.mixin.features.texture;

import dev.rdh.argentum.impl.Argentum;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;

import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;

import org.embeddedt.embeddium.impl.model.quad.BakedQuadView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Unique
    private static final TextureAtlasSprite[] argentum$NONE = new TextureAtlasSprite[0];

    @Unique
    private static final Map<BakedModel, TextureAtlasSprite[]> argentum$modelSprites = new Reference2ObjectOpenHashMap<>();

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void argentum$markAnimatedSprites(ItemStack item, BakedModel model, CallbackInfo ci) {
        if (!Argentum.CONFIG.animateOnlyVisibleTextures || model == null) return;

        TextureAtlasSprite[] sprites = argentum$modelSprites.get(model);
        if (sprites == null) {
            argentum$modelSprites.put(model, sprites = argentum$animatedSprites(model));
        }
        for (TextureAtlasSprite sprite : sprites) {
            sprite.argentum$markActive();
        }
    }

    @Inject(method = "reload", at = @At("RETURN"))
    private void argentum$forgetModels(ResourceManager resourceManager, CallbackInfo ci) {
        argentum$modelSprites.clear();
    }

    @Unique
    private static TextureAtlasSprite[] argentum$animatedSprites(BakedModel model) {
        var found = new ReferenceLinkedOpenHashSet<TextureAtlasSprite>();
        argentum$collect(model.getQuads(), found);
        for (Direction face : Direction.values()) {
            argentum$collect(model.getQuads(face), found);
        }

        TextureAtlasSprite particle = model.getParticleIcon();
        if (particle != null && particle.isAnimated()) found.add(particle);
        return found.isEmpty() ? argentum$NONE : found.toArray(argentum$NONE);
    }

    @Unique
    private static void argentum$collect(List<BakedQuad> quads, Collection<TextureAtlasSprite> found) {
        if (quads == null) return;
		for(BakedQuad quad : quads) {
			if(BakedQuadView.of(quad).celeritas$getSprite() instanceof TextureAtlasSprite sprite && sprite.isAnimated()) {
				found.add(sprite);
			}
		}
    }
}
