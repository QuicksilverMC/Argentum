package dev.rdh.argentum.mixin.features.texture;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.AnimatedModelSprites;

import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void argentum$markAnimatedSprites(ItemStack item, BakedModel model, CallbackInfo ci) {
        if (!Argentum.CONFIG.animateOnlyVisibleTextures || model == null) return;

        for (TextureAtlasSprite sprite : AnimatedModelSprites.of(model)) {
            sprite.argentum$markActive();
        }
    }

    @Inject(method = "reload", at = @At("RETURN"))
    private void argentum$forgetModels(ResourceManager resourceManager, CallbackInfo ci) {
        AnimatedModelSprites.clear();
    }
}
