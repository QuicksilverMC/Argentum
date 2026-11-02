package dev.rdh.argentum.mixin.features.model.instancing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.resource.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;

@Mixin(BlockModelRenderer.class)
public abstract class BlockModelRendererMixin {
    @WrapOperation(method = "render(Lnet/minecraft/client/resource/model/BakedModel;Lnet/minecraft/block/state/BlockState;FZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockModelRenderer;render(Lnet/minecraft/client/resource/model/BakedModel;FFFF)V"))
    private void celeritas$instanceEntityBlock(BlockModelRenderer renderer, BakedModel model,
            float brightness, float red, float green, float blue, Operation<Void> original) {
        EntityCapture capture = EntityCapture.current();
        if (capture == null || !capture.recordBlock(model, brightness, red, green, blue)) {
            original.call(renderer, model, brightness, red, green, blue);
        }
    }
}
