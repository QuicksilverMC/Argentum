package dev.rdh.argentum.mixin.features.model.instancing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.layer.EntityRenderLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;
import dev.rdh.argentum.impl.render.entity.instancing.EntityInstancing;

import java.nio.FloatBuffer;
import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow
    protected Model model;

    @Shadow
    protected List<?> layers;

    @Shadow
    protected boolean solidRender;

    @Shadow
    protected FloatBuffer tintBuffer;

    @WrapMethod(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V")
    private void celeritas$captureEntity(LivingEntity entity, double x, double y, double z, float yaw,
            float tickDelta, Operation<Void> original) {
        EntityInstancing instancing = EntityInstancing.current();
        boolean player = entity instanceof PlayerEntity && this.model instanceof PlayerModel;
        boolean eligible = instancing != null
                && !this.solidRender
                && !entity.isInvisible()
                && !entity.shouldRenderOnFire()
                && !(EntityInstancing.overlayPassDetected() && celeritas$isTinted(entity));
        Identifier texture = eligible ? ((EntityRendererAccessor)this).celeritas$getTextureLocation(entity) : null;
        try (EntityCapture capture = eligible ? instancing.beginEntity(
                this.model, texture, player, player || !this.layers.isEmpty(),
                EntityInstancing.packedLight(entity, tickDelta), entity.ticks + tickDelta,
                0.0F, 0.0F, 0.0F, 0.0F) : null) {
            original.call(entity, x, y, z, yaw, tickDelta);
        }
    }

    @Unique
    private static boolean celeritas$isTinted(LivingEntity entity) {
        return entity.damagedTimer > 0 || entity.deathTicks > 0;
    }

    // not TAIL: the "no overlay at all" branch is compiled to the last return in the method, so TAIL would bind to
    // the one path that leaves tintBuffer holding the previous entity's colour
    @Inject(method = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z", at = @At("RETURN"))
    private void celeritas$captureOverlayColor(LivingEntity entity, float tickDelta, boolean alwaysRender,
            CallbackInfoReturnable<Boolean> cir) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null && cir.getReturnValueZ()) {
            capture.setOverlayColor(this.tintBuffer.get(0), this.tintBuffer.get(1),
                    this.tintBuffer.get(2), this.tintBuffer.get(3));
        } else if (capture != null) {
            capture.setOverlayColor(0.0F, 0.0F, 0.0F, 0.0F);
        }
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V", at = @At("HEAD"), cancellable = true)
    private void celeritas$deferNameTag(LivingEntity entity, double x, double y, double z, CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null && capture.deferNameTag((LivingEntityRenderer<?>)(Object)this, entity, x, y, z)) {
            ci.cancel();
        }
    }

    @WrapMethod(method = "renderModel(Lnet/minecraft/entity/living/LivingEntity;FFFFFF)V")
    private void celeritas$captureModel(LivingEntity entity, float walkAnimationProgress, float walkAnimationSpeed,
            float bob, float yaw, float pitch, float scale, Operation<Void> original) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null) {
            if (!capture.firstModelPass()) {
                // something is drawing the model again to tint it over the top (old animations' damage tint).
                // one instance cannot express two passes, so stop instancing tinted entities from here on
                EntityInstancing.noteOverlayPass();
            }
            capture.beginModel();
        }
        try {
            original.call(entity, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale);
        } finally {
            if (capture != null) {
                capture.endModel();
            }
        }
    }

    @Redirect(
            method = "renderLayers",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/layer/EntityRenderLayer;render(Lnet/minecraft/entity/living/LivingEntity;FFFFFFF)V")
    )
    private void celeritas$captureLayer(EntityRenderLayer<LivingEntity> layer, LivingEntity entity,
            float walkAnimationProgress, float walkAnimationSpeed, float tickDelta, float bob,
            float yaw, float pitch, float scale) {
        EntityCapture active = EntityCapture.current();
        boolean capture = active != null && active.beginLayer(layer, entity);
        EntityInstancing.beginLayerRender();
        try {
            layer.render(entity, walkAnimationProgress, walkAnimationSpeed, tickDelta, bob, yaw, pitch, scale);
        } finally {
            EntityInstancing.endLayerRender();
            if (capture) {
                active.endLayer();
            }
        }
    }
}
