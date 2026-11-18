package dev.rdh.argentum.mixin.features.nametag;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.argentum.impl.render.entity.NameTagBatch;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @WrapMethod(method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V")
    private void argentum$batchNameTag(Entity entity, String text, double x, double y, double z, int maxDistance, Operation<Void> original) {
        NameTagBatch batch = NameTagBatch.activeForEntities();
        if (batch == null) {
            original.call(entity, text, x, y, z, maxDistance);
            return;
        }
        batch.enter();
        try {
            original.call(entity, text, x, y, z, maxDistance);
        } finally {
            batch.exit();
        }
    }
}
