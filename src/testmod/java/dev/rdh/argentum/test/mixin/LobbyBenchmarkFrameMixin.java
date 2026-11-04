package dev.rdh.argentum.test.mixin;

import dev.rdh.argentum.test.LobbyBenchmark;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class LobbyBenchmarkFrameMixin {
    @Inject(method = "runGame", at = @At("HEAD"))
    private void lobbyBenchmark$startFrame(CallbackInfo ci) {
        LobbyBenchmark.frameStart();
    }

    @Inject(method = "runGame", at = @At("RETURN"))
    private void lobbyBenchmark$endFrame(CallbackInfo ci) {
        LobbyBenchmark.frameEnd();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void lobbyBenchmark$tick(CallbackInfo ci) {
        LobbyBenchmark.tick();
    }
}
