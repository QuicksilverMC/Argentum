package dev.rdh.argentum.mixin.features.model.instancing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/resource/model/BakedModel;ILnet/minecraft/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void argentum$instanceHeldItem(BakedModel model, int color, ItemStack item, CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null && capture.recordItem(model, item, color)) {
            ci.cancel();
        }
    }

    @WrapMethod(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resource/model/BakedModel;)V")
    private void argentum$captureItemRender(ItemStack item, BakedModel model, Operation<Void> original) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null) {
            capture.beginItemRender();
        }
        original.call(item, model);
    }

    @WrapMethod(method = "renderEnchantmentGlint")
    private void argentum$captureGlint(BakedModel model, Operation<Void> original) {
        EntityCapture capture = EntityCapture.current();
        if (capture == null) {
            original.call(model);
            return;
        }

        capture.beginGlint();
        try {
            original.call(model);
        } finally {
            capture.endGlint();
        }
    }
}
