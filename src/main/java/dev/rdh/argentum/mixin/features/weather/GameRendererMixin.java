package dev.rdh.argentum.mixin.features.weather;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    private Minecraft minecraft;

    @Shadow
    private int ticks;

    @Shadow
    private float[] rainSizeX;

    @Shadow
    private float[] rainSizeZ;

    @Shadow
    public abstract void disableLightMap();

    @Inject(method = "renderSnowAndRain", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;color4f(FFFF)V", shift = At.Shift.AFTER), cancellable = true)
    private void argentum$renderInstancedWeather(float tickDelta, CallbackInfo ci,
                                                 @Local(ordinal = 1) float strength, @Local(ordinal = 4) int radius,
                                                 @Local BufferBuilder bufferBuilder) {
        if (!Argentum.CONFIG.fasterWeather) {
            return;
        }

        ArgentumWorldRenderer renderer = ArgentumWorldRenderer.instanceNullable();
        if (renderer == null || !renderer.renderWeather(this.minecraft.getCamera(), this.ticks, tickDelta, strength, radius, this.rainSizeX, this.rainSizeZ)) {
            return;
        }

        bufferBuilder.offset(0.0, 0.0, 0.0);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.alphaFunc(516, 0.1F);
        this.disableLightMap();
        ci.cancel();
    }
}
