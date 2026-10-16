package dev.rdh.argentum.mixin.features.portal;

import dev.rdh.argentum.impl.render.environment.EndPortalRenderer;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.Tesselator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Tesselator.class, priority = 100)
public abstract class TesselatorMixin {
    @Shadow private BufferBuilder bufferBuilder;

    @Inject(method = "end", at = @At("HEAD"), cancellable = true)
    private void argentum$capturePortal(CallbackInfo ci) {
        EndPortalRenderer capture = EndPortalRenderer.capturing();
        if (capture != null && capture.capture(this.bufferBuilder)) ci.cancel();
    }
}
