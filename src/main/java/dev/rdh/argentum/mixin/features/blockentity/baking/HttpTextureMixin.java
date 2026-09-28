package dev.rdh.argentum.mixin.features.blockentity.baking;

import dev.rdh.argentum.impl.ext.HttpTextureExtension;
import dev.rdh.argentum.impl.render.blockentity.BakedBlockEntities;

import net.minecraft.client.render.texture.HttpTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.image.BufferedImage;

@Mixin(HttpTexture.class)
public abstract class HttpTextureMixin implements HttpTextureExtension {
    @Unique
    private volatile int[] argentum$headPixels;

    @Inject(method = "setImage", at = @At("HEAD"))
    private void argentum$keepHeadPixels(BufferedImage image, CallbackInfo ci) {
        int[] pixels = BakedBlockEntities.headPixels(image);
        this.argentum$headPixels = pixels != null ? pixels : new int[0];
    }

    @Override
    public int[] argentum$getHeadPixels() {
        return this.argentum$headPixels;
    }
}
