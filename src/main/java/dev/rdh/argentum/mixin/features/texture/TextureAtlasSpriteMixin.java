package dev.rdh.argentum.mixin.features.texture;

import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.metadata.AnimationMetadata;
import org.embeddedt.embeddium.api.util.ColorARGB;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.util.color.ColorSRGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.rdh.argentum.impl.ext.TextureAtlasSpriteExtension;

import java.awt.image.BufferedImage;
import java.util.List;

@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteMixin implements TextureAtlasSpriteExtension {
    @Shadow
    protected List<int[][]> frames;

    @Shadow @Final
    private String name;

    private boolean celeritas$active;
    private SpriteTransparencyLevel celeritas$transparency = SpriteTransparencyLevel.TRANSLUCENT;

    @Inject(method = "load", at = @At("RETURN"))
    private void celeritas$analyzeTransparency(BufferedImage[] images, AnimationMetadata animation, CallbackInfo ci) {
        boolean fillTransparentPixels = argentum$shouldFillTransparentPixels(this.name);
        SpriteTransparencyLevel level = SpriteTransparencyLevel.OPAQUE;
        for (int[][] frame : this.frames) {
            if (frame == null || frame.length == 0 || frame[0] == null) continue;
            if (fillTransparentPixels) argentum$fillTransparentPixels(frame[0]);
            for (int color : frame[0]) {
                int alpha = color >>> 24;
                if (alpha != 255) {
                    level = level.chooseNextLevel(alpha == 0 ? SpriteTransparencyLevel.TRANSPARENT : SpriteTransparencyLevel.TRANSLUCENT);
                    if (level == SpriteTransparencyLevel.TRANSLUCENT) break;
                }
            }
            if (level == SpriteTransparencyLevel.TRANSLUCENT) break;
        }
        this.celeritas$transparency = level;
    }

    @Unique
    private static boolean argentum$shouldFillTransparentPixels(String name) {
        String path = name.substring(name.indexOf(':') + 1);
        return path.startsWith("blocks/") && !path.contains("leaves");
    }

    @Unique
    private static void argentum$fillTransparentPixels(int[] pixels) {
        float r = 0.0f, g = 0.0f, b = 0.0f, totalWeight = 0.0f;
        for (int color : pixels) {
            int alpha = ColorARGB.unpackAlpha(color);
            if (alpha == 0) continue;
            r += ColorSRGB.srgbToLinear(ColorARGB.unpackRed(color)) * alpha;
            g += ColorSRGB.srgbToLinear(ColorARGB.unpackGreen(color)) * alpha;
            b += ColorSRGB.srgbToLinear(ColorARGB.unpackBlue(color)) * alpha;
            totalWeight += alpha;
        }
        if (totalWeight == 0.0f) return;

        int average = ColorSRGB.linearToSrgb(r / totalWeight, g / totalWeight, b / totalWeight, 0);
        for (int i = 0; i < pixels.length; i++) {
            if (ColorARGB.unpackAlpha(pixels[i]) == 0) pixels[i] = average;
        }
    }

    @Inject(method = {"getUMin", "getU"}, at = @At("RETURN"))
    private void celeritas$markUsed(CallbackInfoReturnable<Float> cir) {
        this.celeritas$active = true;
    }

    @Override
    public void argentum$markActive() {
        this.celeritas$active = true;
    }

    @Override
    public boolean argentum$shouldUpdate() {
        boolean active = this.celeritas$active;
        this.celeritas$active = false;
        return active;
    }

    @Override
    public SpriteTransparencyLevel embeddium$getTransparencyLevel() {
        return this.celeritas$transparency;
    }
}
