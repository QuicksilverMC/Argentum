package dev.rdh.argentum.mixin.features.hud;

import dev.rdh.argentum.impl.render.gui.hud.GradientBatch;
import dev.rdh.argentum.impl.render.gui.hud.ScreenBatch;

import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiElement.class)
public class GuiElementMixin {
    @Unique
    private static final float ARGENTUM$UV_SCALE = 0.00390625F;

    @Shadow
    protected float drawOffset;

    @Inject(method = "fillGradient", at = @At("HEAD"), cancellable = true)
    private void argentum$batchGradient(int left, int top, int right, int bottom, int colorTop, int colorBottom, CallbackInfo ci) {
        if (GradientBatch.record(left, top, right, bottom, colorTop, colorBottom, this.drawOffset)) {
            ci.cancel();
        }
    }

    @Inject(method = "fill", at = @At("HEAD"), cancellable = true)
    private static void argentum$batchFill(int left, int top, int right, int bottom, int color, CallbackInfo ci) {
        if (ScreenBatch.fill(left, top, right, bottom, color)) ci.cancel();
    }

    @Inject(method = "drawTexture(IIIIII)V", at = @At("HEAD"), cancellable = true)
    private void argentum$batchTexture(int x, int y, int u, int v, int width, int height, CallbackInfo ci) {
        if (ScreenBatch.texture(x, y, x + width, y + height,
                u * ARGENTUM$UV_SCALE, v * ARGENTUM$UV_SCALE, (u + width) * ARGENTUM$UV_SCALE, (v + height) * ARGENTUM$UV_SCALE,
                this.drawOffset)) {
            ci.cancel();
        }
    }

    @Inject(method = "drawTexture(FFIIII)V", at = @At("HEAD"), cancellable = true)
    private void argentum$batchTexture(float x, float y, int u, int v, int width, int height, CallbackInfo ci) {
        if (ScreenBatch.texture(x, y, x + width, y + height,
                u * ARGENTUM$UV_SCALE, v * ARGENTUM$UV_SCALE, (u + width) * ARGENTUM$UV_SCALE, (v + height) * ARGENTUM$UV_SCALE,
                this.drawOffset)) {
            ci.cancel();
        }
    }

    @Inject(method = "drawSprite", at = @At("HEAD"), cancellable = true)
    private void argentum$batchSprite(int x, int y, TextureAtlasSprite sprite, int width, int height, CallbackInfo ci) {
        if (ScreenBatch.texture(x, y, x + width, y + height,
                sprite.getUMin(), sprite.getVMin(), sprite.getUMax(), sprite.getVMax(), this.drawOffset)) {
            ci.cancel();
        }
    }

    @Inject(method = "drawTexture(IIFFIIFF)V", at = @At("HEAD"), cancellable = true)
    private static void argentum$batchTexture(int x, int y, float u, float v, int width, int height, float textureWidth, float textureHeight, CallbackInfo ci) {
        float scaleU = 1.0F / textureWidth;
        float scaleV = 1.0F / textureHeight;
        if (ScreenBatch.texture(x, y, x + width, y + height,
                u * scaleU, v * scaleV, (u + width) * scaleU, (v + height) * scaleV, 0.0F)) {
            ci.cancel();
        }
    }

    @Inject(method = "drawTexture(IIFFIIIIFF)V", at = @At("HEAD"), cancellable = true)
    private static void argentum$batchTexture(int x, int y, float u, float v, int sourceWidth, int sourceHeight, int width, int height,
            float textureWidth, float textureHeight, CallbackInfo ci) {
        float scaleU = 1.0F / textureWidth;
        float scaleV = 1.0F / textureHeight;
        if (ScreenBatch.texture(x, y, x + width, y + height,
                u * scaleU, v * scaleV, (u + sourceWidth) * scaleU, (v + sourceHeight) * scaleV, 0.0F)) {
            ci.cancel();
        }
    }
}
