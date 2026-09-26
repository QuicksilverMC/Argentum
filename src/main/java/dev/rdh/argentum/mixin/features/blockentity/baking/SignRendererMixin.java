package dev.rdh.argentum.mixin.features.blockentity.baking;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.rdh.argentum.impl.render.blockentity.BakedBlockEntities;

import net.minecraft.client.render.block.entity.SignRenderer;
import net.minecraft.client.render.model.block.entity.SignModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SignRenderer.class)
public abstract class SignRendererMixin {
    @WrapWithCondition(
            method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/block/entity/SignModel;render()V")
    )
    private boolean argentum$skipBakedBoard(SignModel model) {
        return !BakedBlockEntities.bakesSigns();
    }
}
