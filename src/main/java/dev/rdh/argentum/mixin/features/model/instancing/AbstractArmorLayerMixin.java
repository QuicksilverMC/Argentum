package dev.rdh.argentum.mixin.features.model.instancing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;

@Mixin(AbstractArmorLayer.class)
public abstract class AbstractArmorLayerMixin {
    @WrapOperation(method = "renderEnchantmentGlint", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/Model;render(Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private void argentum$replayGlint(Model model, Entity entity, float walkAnimationProgress, float walkAnimationSpeed,
            float bob, float yaw, float pitch, float scale, Operation<Void> original, @Share("glinted") LocalBooleanRef glinted) {
        EntityCapture capture = EntityCapture.current();
        boolean first = !glinted.get();
        glinted.set(true);
        if (capture != null && capture.capturesGlint() && (!first || capture.replayArmorGlint())) return;
        original.call(model, entity, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale);
    }
}
