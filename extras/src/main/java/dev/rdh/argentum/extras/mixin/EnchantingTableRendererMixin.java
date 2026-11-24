package dev.rdh.argentum.extras.mixin;

import dev.rdh.argentum.extras.ArgentumExtras;
import net.minecraft.block.entity.EnchantingTableBlockEntity;
import net.minecraft.client.render.block.entity.EnchantingTableRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantingTableRenderer.class)
public class EnchantingTableRendererMixin {
    @Inject(method = "render(Lnet/minecraft/block/entity/EnchantingTableBlockEntity;DDDFI)V", at = @At("HEAD"), cancellable = true)
    private void argentumExtras$hideEnchantingTableBook(EnchantingTableBlockEntity table, double x, double y, double z, float tickDelta, int breakProgress, CallbackInfo ci) {
        if (!ArgentumExtras.CONFIG.enchantingTableBook) {
            ci.cancel();
        }
    }
}
