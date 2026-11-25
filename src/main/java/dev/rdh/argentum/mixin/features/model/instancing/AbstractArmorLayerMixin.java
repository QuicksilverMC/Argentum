package dev.rdh.argentum.mixin.features.model.instancing;

import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.entity.Entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;

/**
 * Captures armor glint without walking the armor model again.
 *
 * <p>renderEnchantmentGlint draws the model twice with different texture matrices. Under capture the instancing
 * batcher draws both glint layers from one capture with its own matrices, so the second pass records nothing
 * (glint parts are deduplicated) and the first pass records exactly the parts the piece's base pass just did.
 * The first pass is replaced by {@link EntityCapture#replayArmorGlint} and the second is skipped. If there is
 * nothing to replay, the model is walked as before.
 */
@Mixin(AbstractArmorLayer.class)
public abstract class AbstractArmorLayerMixin {
    @Unique
    private int argentum$glintPass;

    @Inject(method = "renderEnchantmentGlint", at = @At("HEAD"))
    private void argentum$resetGlintPass(CallbackInfo ci) {
        this.argentum$glintPass = 0;
    }

    @WrapOperation(method = "renderEnchantmentGlint", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/model/Model;render(Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private void argentum$captureGlintOnce(Model model, Entity entity, float walkAnimationProgress, float walkAnimationSpeed,
            float bob, float yaw, float pitch, float scale, Operation<Void> original) {
        int pass = this.argentum$glintPass++;
        EntityCapture capture = EntityCapture.current();
        if (capture != null && capture.capturesGlint() && (pass > 0 || capture.replayArmorGlint())) {
            return;
        }
        original.call(model, entity, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale);
    }
}
