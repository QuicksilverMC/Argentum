package dev.rdh.argentum.mixin.features.cloud;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.world.WorldRenderer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.environment.CloudRenderer;

@Mixin(value = WorldRenderer.class, priority = 100)
public abstract class WorldRendererMixin {
    @Unique
    private final CloudRenderer celeritas$cloudRenderer = new CloudRenderer();

    @Unique
    private int celeritas$firstCloudCell;

    @Unique
    private int celeritas$lastCloudCell;

    @ModifyExpressionValue(method = "renderFancyClouds", at = @At(value = "CONSTANT", args = "intValue=-3", ordinal = 0))
    private int celeritas$recordFirstCloudCell(int firstCell) {
        this.celeritas$firstCloudCell = firstCell;
        return firstCell;
    }

    @ModifyExpressionValue(method = "renderFancyClouds", at = @At(value = "CONSTANT", args = "intValue=4", ordinal = 1))
    private int celeritas$recordLastCloudCell(int lastCell) {
        this.celeritas$lastCloudCell = lastCell;
        return lastCell;
    }

    // vanilla has worked out everything about the clouds and entered its cell loop by here, so hooks on any of that still apply
    @Inject(method = "renderFancyClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;begin(ILnet/minecraft/client/render/vertex/VertexFormat;)V"), cancellable = true)
    private void celeritas$renderFancyClouds(float tickDelta, int pass, CallbackInfo ci,
            @Local(ordinal = 1) double cloudX, @Local(ordinal = 2) double cloudZ, @Local(ordinal = 4) float cloudY,
            @Local(ordinal = 5) float red, @Local(ordinal = 6) float green, @Local(ordinal = 7) float blue) {
        if (!Argentum.CONFIG.fasterClouds
                || !this.celeritas$cloudRenderer.render(cloudX, cloudZ, cloudY, red, green, blue,
                        this.celeritas$firstCloudCell, this.celeritas$lastCloudCell, pass)) {
            return;
        }

        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        ci.cancel();
    }

    @Inject(method = {"reload()V", "releaseGlLists"}, at = @At("HEAD"))
    private void celeritas$deleteCloudBuffers(CallbackInfo ci) {
        this.celeritas$cloudRenderer.delete();
    }
}
