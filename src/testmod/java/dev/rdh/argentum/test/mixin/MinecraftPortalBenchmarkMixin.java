package dev.rdh.argentum.test.mixin;

import dev.rdh.argentum.test.PortalBenchmark;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftPortalBenchmarkMixin {
    @Inject(method = "runGame", at = @At("HEAD"))
    private void portalBenchmark$startFrame(CallbackInfo ci) {
        PortalBenchmark.frameStart();
    }

    @Inject(method = "runGame", at = @At("RETURN"))
    private void portalBenchmark$endFrame(CallbackInfo ci) {
        PortalBenchmark.frameEnd();
    }
}
