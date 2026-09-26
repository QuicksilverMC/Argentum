package dev.rdh.argentum.mixin.features.blockentity.baking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.argentum.impl.render.blockentity.BakedBlockEntities;

import net.minecraft.client.render.block.entity.BannerRenderer;
import net.minecraft.client.render.model.block.entity.BannerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BannerRenderer.class)
public abstract class BannerRendererMixin {
    @WrapOperation(
            method = "render(Lnet/minecraft/block/entity/BannerBlockEntity;DDDFI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/block/entity/BannerModel;render()V")
    )
    private void argentum$skipBakedPole(BannerModel model, Operation<Void> original) {
        if (!BakedBlockEntities.bakesBanners()) {
            original.call(model);
            return;
        }
        boolean pole = model.pole.visible;
        model.pole.visible = false;
        model.bar.visible = false;
        try {
            original.call(model);
        } finally {
            model.pole.visible = pole;
            model.bar.visible = true;
        }
    }
}
