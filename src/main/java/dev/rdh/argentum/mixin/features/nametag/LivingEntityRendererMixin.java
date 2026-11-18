package dev.rdh.argentum.mixin.features.nametag;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.argentum.impl.render.entity.NameTagBatch;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @WrapMethod(method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V")
    private void argentum$batchNameTag(LivingEntity entity, double x, double y, double z, Operation<Void> original) {
        NameTagBatch batch = NameTagBatch.activeForEntities();
        if (batch == null) {
            original.call(entity, x, y, z);
            return;
        }
        batch.enter();
        try {
            original.call(entity, x, y, z);
        } finally {
            batch.exit();
        }
    }
}
