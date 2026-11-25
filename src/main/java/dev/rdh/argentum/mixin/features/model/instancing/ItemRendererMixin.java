package dev.rdh.argentum.mixin.features.model.instancing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;
import dev.rdh.argentum.impl.render.entity.instancing.HeldItemFilter;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/resource/model/BakedModel;ILnet/minecraft/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void argentum$instanceHeldItem(BakedModel model, int color, ItemStack item, CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null && capture.recordItem(model, item, color)) {
            ci.cancel();
        } else if (item != null) {
            HeldItemFilter.flush(); // drawn immediately, so it needs the deferred atlas filter
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

    private static final String RENDER_IN_HAND = "renderItemInHand(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resource/model/BakedModel;Lnet/minecraft/client/render/model/block/ModelTransformations$Type;)V";

    @WrapOperation(method = RENDER_IN_HAND, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/Texture;pushFilter(ZZ)V"))
    private void argentum$deferHeldItemFilter(Texture texture, boolean blur, boolean mipmap, Operation<Void> original) {
        if (EntityCapture.current() != null) {
            HeldItemFilter.defer(texture, blur, mipmap);
            return;
        }
        original.call(texture, blur, mipmap);
    }

    @WrapOperation(method = RENDER_IN_HAND, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/Texture;popFilter()V"))
    private void argentum$popHeldItemFilter(Texture texture, Operation<Void> original) {
        if (!HeldItemFilter.pop(texture)) original.call(texture);
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
