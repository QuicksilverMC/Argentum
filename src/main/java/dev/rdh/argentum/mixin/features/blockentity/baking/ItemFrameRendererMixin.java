package dev.rdh.argentum.mixin.features.blockentity.baking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.rdh.argentum.impl.render.blockentity.BakedItemFrames;

import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.entity.ItemFrameRenderer;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.entity.decoration.ItemFrameEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRendererMixin {
    @WrapOperation(
            method = "render(Lnet/minecraft/entity/decoration/ItemFrameEntity;DDDFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockModelRenderer;render(Lnet/minecraft/client/resource/model/BakedModel;FFFF)V")
    )
    private void argentum$skipBakedFrame(BlockModelRenderer renderer, BakedModel model, float brightness, float red, float green,
            float blue, Operation<Void> original, @Local(argsOnly = true) ItemFrameEntity frame) {
        if (!BakedItemFrames.frameBaked(frame)) original.call(renderer, model, brightness, red, green, blue);
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/entity/decoration/ItemFrameEntity;DDDFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/ItemFrameRenderer;renderDisplayItem(Lnet/minecraft/entity/decoration/ItemFrameEntity;)V")
    )
    private void argentum$skipBakedItem(ItemFrameRenderer renderer, ItemFrameEntity frame, Operation<Void> original) {
        if (!BakedItemFrames.itemBaked(frame)) original.call(renderer, frame);
    }
}
