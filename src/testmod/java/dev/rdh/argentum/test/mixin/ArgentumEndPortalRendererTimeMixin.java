package dev.rdh.argentum.test.mixin;

import dev.rdh.argentum.test.PortalBenchmark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = dev.rdh.argentum.impl.render.environment.EndPortalRenderer.class, remap = false)
abstract class ArgentumEndPortalRendererTimeMixin {
    @Shadow private int replayCount;

    @Inject(method = "flush", at = @At("RETURN"))
    private void portalBenchmark$recordShaderDraw(CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ()) PortalBenchmark.recordShaderDraw(this.replayCount);
    }
}
