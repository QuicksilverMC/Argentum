package dev.rdh.argentum.extras.mixin;

import dev.rdh.argentum.extras.ArgentumExtras;
import net.minecraft.client.render.entity.PaintingRenderer;
import net.minecraft.entity.decoration.PaintingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PaintingRenderer.class)
public class PaintingRendererMixin {
    @Inject(method = "render(Lnet/minecraft/entity/decoration/PaintingEntity;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void argentumExtras$hidePainting(PaintingEntity painting, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
        if (!ArgentumExtras.CONFIG.paintings) {
            ci.cancel();
        }
    }
}
