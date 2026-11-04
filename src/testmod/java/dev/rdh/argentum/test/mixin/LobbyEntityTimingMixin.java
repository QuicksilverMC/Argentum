package dev.rdh.argentum.test.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.argentum.test.LobbyBenchmark;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WorldRenderer.class)
abstract class LobbyEntityTimingMixin {
    @WrapMethod(method = "renderEntities")
    private void lobbyBenchmark$timeEntities(Entity camera, Culler culler, float tickDelta, Operation<Void> original) {
        long start = System.nanoTime();
        original.call(camera, culler, tickDelta);
        LobbyBenchmark.recordEntities(System.nanoTime() - start);
    }
}
