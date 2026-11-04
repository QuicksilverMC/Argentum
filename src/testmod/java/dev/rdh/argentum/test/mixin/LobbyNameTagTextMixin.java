package dev.rdh.argentum.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.argentum.test.LobbyBenchmark;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
abstract class LobbyNameTagTextMixin {
    @WrapOperation(method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I"))
    private int lobbyBenchmark$skipText(TextRenderer textRenderer, String text, int x, int y, int color, Operation<Integer> original) {
        return LobbyBenchmark.variant("noTagText") ? 0 : original.call(textRenderer, text, x, y, color);
    }
}
