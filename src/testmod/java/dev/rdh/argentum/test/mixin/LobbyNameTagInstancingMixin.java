package dev.rdh.argentum.test.mixin;

import dev.rdh.argentum.test.LobbyBenchmark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.rdh.argentum.impl.render.entity.NameTagInstancing", remap = false)
abstract class LobbyNameTagInstancingMixin {
    @Inject(method = "available", at = @At("HEAD"), cancellable = true)
    private void lobbyBenchmark$cpuTags(CallbackInfoReturnable<Boolean> cir) {
        if (LobbyBenchmark.variant("cpuTags")) cir.setReturnValue(false);
    }
}
