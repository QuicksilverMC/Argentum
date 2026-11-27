package dev.rdh.argentum.mixin.features.model.instancing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.layer.EntityRenderLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.rdh.argentum.impl.render.entity.instancing.CapturedOverlay;
import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;
import dev.rdh.argentum.impl.render.entity.instancing.EntityInstancing;

import java.nio.FloatBuffer;
import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Unique
    private boolean argentum$overlayMuted;

    @Shadow
    protected Model model;

    @Shadow
    protected List<?> layers;

    @Shadow
    protected boolean solidRender;

    @Shadow
    protected FloatBuffer tintBuffer;

    @Shadow
    protected abstract int getOverlayColor(LivingEntity entity, float brightness, float tickDelta);

    @Shadow
    protected abstract boolean setupOverlayColor(LivingEntity entity, float tickDelta, boolean alwaysRender);

    @WrapMethod(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V")
    private void celeritas$captureEntity(LivingEntity entity, double x, double y, double z, float yaw,
            float tickDelta, Operation<Void> original) {
        EntityInstancing instancing = EntityInstancing.current();
        boolean player = entity instanceof PlayerEntity && this.model instanceof PlayerModel;
        boolean eligible = instancing != null
                && !this.solidRender
                && !entity.isInvisible()
                && !entity.shouldRenderOnFire()
                && !(instancing.overlayPassDetected() && celeritas$isTinted(entity))
                && !(instancing.usesStandardAttributes() && argentum$hasOverlay(entity, tickDelta));
        Identifier texture = eligible ? ((EntityRendererAccessor)this).celeritas$getTextureLocation(entity) : null;
        try (EntityCapture _ = eligible ? instancing.beginEntity(
                entity, this.model, texture, player, true,
                EntityInstancing.packedLight(entity, tickDelta), entity.ticks + tickDelta,
                0.0F, 0.0F, 0.0F, 0.0F) : null) {
            original.call(entity, x, y, z, yaw, tickDelta);
        }
    }

    @Unique
    private static boolean celeritas$isTinted(LivingEntity entity) {
        return entity.damagedTimer > 0 || entity.deathTicks > 0;
    }

    @Unique
    private boolean argentum$hasOverlay(LivingEntity entity, float tickDelta) {
        return celeritas$isTinted(entity) || this.getOverlayColor(entity, entity.getBrightness(tickDelta), tickDelta) >>> 24 != 0;
    }

    // this can't be TAIL because javac did something weird to the code flow in this method
    @ModifyReturnValue(method = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z", at = @At("RETURN"))
    private boolean celeritas$captureOverlayColor(boolean overlay) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null && overlay) {
            capture.setOverlayColor(this.tintBuffer.get(0), this.tintBuffer.get(1),
                    this.tintBuffer.get(2), this.tintBuffer.get(3));
        } else if (capture != null) {
            capture.setOverlayColor(0.0F, 0.0F, 0.0F, 0.0F);
        }
        if (capture != null) capture.overlay().endSetup(this.argentum$overlayMuted, overlay);
        this.argentum$overlayMuted = false;
        return overlay;
    }

    @Inject(method = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z", at = @At("HEAD"))
    private void argentum$beginOverlaySetup(LivingEntity entity, float tickDelta, boolean alwaysRender, CallbackInfoReturnable<Boolean> cir) {
        EntityCapture capture = EntityCapture.current();
        this.argentum$overlayMuted = capture != null && capture.overlay().beginSetup(tickDelta, alwaysRender);
    }

    @Inject(method = "tearDownOverlayColor", at = @At("HEAD"))
    private void argentum$beginOverlayTeardown(CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        this.argentum$overlayMuted = capture != null && capture.overlay().beginTeardown();
    }

    @Inject(method = "tearDownOverlayColor", at = @At("RETURN"))
    private void argentum$endOverlayTeardown(CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null) capture.overlay().endTeardown();
        this.argentum$overlayMuted = false;
    }

    @WrapOperation(method = {
            "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z", "tearDownOverlayColor"
    }, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexEnvi(III)V", remap = false))
    private void argentum$texEnvi(int target, int name, int value, Operation<Void> original) {
        if (!this.argentum$overlayMuted) original.call(target, name, value);
    }

    @WrapOperation(method = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexEnv(IILjava/nio/FloatBuffer;)V", remap = false))
    private void argentum$texEnv(int target, int name, FloatBuffer params, Operation<Void> original) {
        if (!this.argentum$overlayMuted) original.call(target, name, params);
    }

    @WrapOperation(method = {
            "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z", "tearDownOverlayColor"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;activeTexture(I)V"))
    private void argentum$activeTexture(int unit, Operation<Void> original) {
        if (!this.argentum$overlayMuted) original.call(unit);
    }

    @WrapOperation(method = {
            "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z", "tearDownOverlayColor"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;enableTexture()V"))
    private void argentum$enableTexture(Operation<Void> original) {
        if (!this.argentum$overlayMuted) original.call();
    }

    @WrapOperation(method = "tearDownOverlayColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;disableTexture()V"))
    private void argentum$disableTexture(Operation<Void> original) {
        if (!this.argentum$overlayMuted) original.call();
    }

    @WrapOperation(method = {
            "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z", "tearDownOverlayColor"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;bindTexture(I)V"))
    private void argentum$bindTexture(int texture, Operation<Void> original) {
        if (!this.argentum$overlayMuted) original.call(texture);
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V", at = @At("HEAD"), cancellable = true)
    private void celeritas$deferNameTag(LivingEntity entity, double x, double y, double z, CallbackInfo ci) {
        EntityInstancing instancing = EntityInstancing.current();
        if (instancing != null && instancing.isShadowPass()) {
            ci.cancel();
            return;
        }
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
                EntityInstancing.current().noteOverlayPass();
            }
            capture.beginModel();
        }
        boolean immediate = capture != null && !capture.isModelActive();
        if (immediate) this.argentum$beginImmediate(capture.overlay(), entity);
        try {
            original.call(entity, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale);
        } finally {
            if (immediate) capture.overlay().endImmediate();
            if (capture != null) {
                capture.endModel();
            }
        }
    }

    @WrapOperation(method = "renderLayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/layer/EntityRenderLayer;render(Lnet/minecraft/entity/living/LivingEntity;FFFFFFF)V"))
    private void celeritas$captureLayer(EntityRenderLayer<LivingEntity> layer, LivingEntity entity,
            float walkAnimationProgress, float walkAnimationSpeed, float tickDelta, float bob,
            float yaw, float pitch, float scale, Operation<Void> original) {
        EntityCapture active = EntityCapture.current();
        boolean capture = active != null && active.beginLayer(layer, entity);
        boolean immediate = active != null && !capture;
        if (immediate) this.argentum$beginImmediate(active.overlay(), entity);
        EntityInstancing instancing = EntityInstancing.current();
        if (instancing != null) instancing.beginLayerRender();
        try {
            original.call(layer, entity, walkAnimationProgress, walkAnimationSpeed, tickDelta, bob, yaw, pitch, scale);
        } finally {
            if (immediate) active.overlay().endImmediate();
            if (instancing != null) instancing.endLayerRender();
            if (capture) {
                active.endLayer();
            }
        }
    }

    @Unique
    private void argentum$beginImmediate(CapturedOverlay overlay, LivingEntity entity) {
        if (overlay.beginImmediate()) this.setupOverlayColor(entity, overlay.tickDelta(), overlay.combine());
    }
}
