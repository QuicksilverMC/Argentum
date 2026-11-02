package dev.rdh.argentum.mixin.features.model.instancing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;
import dev.rdh.argentum.impl.render.entity.instancing.EntityInstancing;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Shadow
    @Final
    private ItemRenderer itemRenderer;

    @WrapMethod(method = "render(Lnet/minecraft/entity/ItemEntity;DDDFF)V")
    private void celeritas$captureItemEntity(ItemEntity entity, double x, double y, double z, float yaw,
            float tickDelta, Operation<Void> original) {
        EntityInstancing instancing = EntityInstancing.current();
        BakedModel model = this.itemRenderer.getModelShaper().getModel(entity.getItem());
        try (EntityCapture _ = instancing == null ? null
                : instancing.beginItemEntity(entity, model, EntityInstancing.packedLight(entity, tickDelta))) {
            original.call(entity, x, y, z, yaw, tickDelta);
        }
    }

    @WrapOperation(method = "render(Lnet/minecraft/entity/ItemEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;DDDFF)V"))
    private void celeritas$finishItemCapture(ItemEntityRenderer renderer, Entity entity, double x, double y,
            double z, float yaw, float tickDelta, Operation<Void> original) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null) {
            capture.finish();
        }
        original.call(renderer, entity, x, y, z, yaw, tickDelta);
    }
}
