package dev.rdh.argentum.extras.mixin;

import dev.rdh.argentum.extras.ArgentumExtras;
import net.minecraft.block.entity.MovingBlockEntity;
import net.minecraft.client.render.block.entity.MovingBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MovingBlockRenderer.class)
public class MovingBlockRendererMixin {
    @Inject(method = "render(Lnet/minecraft/block/entity/MovingBlockEntity;DDDFI)V", at = @At("HEAD"), cancellable = true)
    private void argentumExtras$hideMovingBlock(MovingBlockEntity movingBlock, double x, double y, double z, float tickDelta, int breakProgress, CallbackInfo ci) {
        if (!ArgentumExtras.CONFIG.pistons) {
            ci.cancel();
        }
    }
}
