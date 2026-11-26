package dev.rdh.argentum.mixin.features.cloud;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import dev.rdh.argentum.impl.ext.WorldRendererExtension;
import dev.rdh.argentum.impl.render.environment.ArgentumCloudRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.render.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldRenderer.class, priority = 100)
public abstract class WorldRendererMixin implements WorldRendererExtension {
    @Unique
    private int celeritas$firstCloudCell;

    @Unique
    private int celeritas$lastCloudCell;

    @ModifyExpressionValue(method = "renderFancyClouds", at = @At(value = "CONSTANT", args = "intValue=-3", ordinal = 0))
    private int celeritas$captureFirstCloudCell(int value) {
        this.celeritas$firstCloudCell = value;
        return value;
    }

    @ModifyExpressionValue(method = "renderFancyClouds", at = @At(value = "CONSTANT", args = "intValue=4", ordinal = 1))
    private int celeritas$captureLastCloudCell(int value) {
        this.celeritas$lastCloudCell = value;
        return value;
    }

    @ModifyExpressionValue(method = "renderClouds(FI)V", at = @At(value = "CONSTANT", args = "intValue=-256", ordinal = 0))
    private int celeritas$captureFastCloudStart(int value) {
        this.celeritas$firstCloudCell = value;
        return value;
    }

    @ModifyExpressionValue(method = "renderClouds(FI)V", at = @At(value = "CONSTANT", args = "intValue=256", ordinal = 0))
    private int celeritas$captureFastCloudEnd(int value) {
        this.celeritas$lastCloudCell = value;
        return value;
    }

    @Inject(method = "renderClouds(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;vertex(DDD)Lnet/minecraft/client/render/vertex/BufferBuilder;", ordinal = 0), cancellable = true)
    private void celeritas$renderFastClouds(float tickDelta, int pass, CallbackInfo ci,
            @Local(ordinal = 1) double x, @Local(ordinal = 2) double z,
            @Local(ordinal = 3) int wrapX, @Local(ordinal = 4) int wrapZ,
            @Local(ordinal = 6) float height, @Local(ordinal = 2) float red,
            @Local(ordinal = 3) float green, @Local(ordinal = 4) float blue,
            @Local BufferBuilder buffer) {
        if (ArgentumCloudRenderer.isEnabled()) {
            if (this.celeritas$clouds().render(x + wrapX * 2048.0, z + wrapZ * 2048.0, height, red, green, blue, false,
                    Math.max(-this.celeritas$firstCloudCell, this.celeritas$lastCloudCell))) {
                buffer.end();
                celeritas$finishClouds(ci);
            } else {
                GlStateManager.enableBlend();
                GlStateManager.disableCull();
            }
        }
    }

    @Inject(method = "renderFancyClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;begin(ILnet/minecraft/client/render/vertex/VertexFormat;)V"), cancellable = true)
    private void celeritas$renderFancyClouds(float tickDelta, int pass, CallbackInfo ci,
            @Local(ordinal = 1) double x, @Local(ordinal = 2) double z,
            @Local(ordinal = 4) float height, @Local(ordinal = 5) float red,
            @Local(ordinal = 6) float green, @Local(ordinal = 7) float blue,
            @Local(ordinal = 5) int cloudPass) {
        if (cloudPass != 0 || !ArgentumCloudRenderer.isEnabled()) {
            return;
        }
        float range = Math.max(-this.celeritas$firstCloudCell, this.celeritas$lastCloudCell + 1) * 96.0F;
        GlStateManager.colorMask(pass != 0, pass != 1, pass != 1, true);

        if (this.celeritas$clouds().render(x * 12.0, z * 12.0 - 3.96, height, red, green, blue, true, range)) {
            celeritas$finishClouds(ci);
        } else {
            GlStateManager.colorMask(false, false, false, false);
            GlStateManager.enableBlend();
            GlStateManager.disableCull();
        }
    }

    @Unique
    private ArgentumCloudRenderer celeritas$clouds() {
        return this.argentum$getWorldRenderer().getCloudRenderer();
    }

    @Unique
    private static void celeritas$finishClouds(CallbackInfo ci) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        ci.cancel();
    }

    @Inject(method = {"reload()V", "reload(Lnet/minecraft/client/resource/manager/ResourceManager;)V", "releaseGlLists"}, at = @At("HEAD"))
    private void celeritas$deleteCloudBuffers(CallbackInfo ci) {
        this.celeritas$clouds().delete();
    }
}
