package dev.rdh.argentum.extras.mixin;

import dev.rdh.argentum.extras.ArgentumExtras;
import net.minecraft.client.render.entity.ItemFrameRenderer;
import net.minecraft.entity.decoration.ItemFrameEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrameRenderer.class)
public class ItemFrameRendererMixin {
    @Inject(method = "render(Lnet/minecraft/entity/decoration/ItemFrameEntity;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void argentumExtras$hideItemFrame(ItemFrameEntity frame, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
        if (!ArgentumExtras.CONFIG.itemFrames) {
            ci.cancel();
        }
    }
}
