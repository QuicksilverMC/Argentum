package dev.rdh.argentum.test.mixin;

import dev.rdh.argentum.test.PortalBenchmark;
import net.minecraft.client.render.block.entity.EndPortalRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndPortalRenderer.class)
abstract class EndPortalRendererTimeMixin {
    @Inject(method = "render(Lnet/minecraft/block/entity/EndPortalBlockEntity;DDDFI)V", at = @At("HEAD"))
    private void portalBenchmark$recordCall(CallbackInfo ci) {
        PortalBenchmark.recordPortalRendererCall();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getTime()J"))
    private long portalBenchmark$freezeTime() {
        return PortalBenchmark.portalTime();
    }
}
