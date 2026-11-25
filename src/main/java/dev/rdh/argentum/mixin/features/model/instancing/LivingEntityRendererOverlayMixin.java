package dev.rdh.argentum.mixin.features.model.instancing;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.rdh.argentum.impl.render.entity.instancing.CapturedOverlay;

import java.nio.FloatBuffer;

/**
 * Skips the hurt/tint overlay's texture-combiner GL calls while an entity is captured; see {@link CapturedOverlay}.
 * tintBuffer is still filled, which is where the captured overlay colour comes from.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererOverlayMixin {
    private static final String SETUP = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z";
    private static final String TEARDOWN = "tearDownOverlayColor()V";

    @Inject(method = SETUP, at = @At("HEAD"))
    private void argentum$beginOverlaySetup(LivingEntity entity, float tickDelta, boolean combine, CallbackInfoReturnable<Boolean> cir) {
        CapturedOverlay.beginSetup((LivingEntityRenderer<?>)(Object)this, entity, tickDelta, combine);
    }

    @ModifyReturnValue(method = SETUP, at = @At("RETURN"))
    private boolean argentum$endOverlaySetup(boolean overlay) {
        CapturedOverlay.endSetup(overlay);
        return overlay;
    }

    @Inject(method = TEARDOWN, at = @At("HEAD"))
    private void argentum$beginOverlayTeardown(CallbackInfo ci) {
        CapturedOverlay.beginTeardown();
    }

    @Inject(method = TEARDOWN, at = @At("RETURN"))
    private void argentum$endOverlayTeardown(CallbackInfo ci) {
        CapturedOverlay.endTeardown();
    }

    @WrapOperation(method = {SETUP, TEARDOWN}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexEnvi(III)V", remap = false))
    private void argentum$texEnvi(int target, int name, int value, Operation<Void> original) {
        if (!CapturedOverlay.skipGl()) original.call(target, name, value);
    }

    @WrapOperation(method = SETUP, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexEnv(IILjava/nio/FloatBuffer;)V", remap = false))
    private void argentum$texEnv(int target, int name, FloatBuffer params, Operation<Void> original) {
        if (!CapturedOverlay.skipGl()) original.call(target, name, params);
    }

    @WrapOperation(method = {SETUP, TEARDOWN}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;activeTexture(I)V"))
    private void argentum$activeTexture(int unit, Operation<Void> original) {
        if (!CapturedOverlay.skipGl()) original.call(unit);
    }

    @WrapOperation(method = {SETUP, TEARDOWN}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;bindTexture(I)V"))
    private void argentum$bindTexture(int id, Operation<Void> original) {
        if (!CapturedOverlay.skipGl()) original.call(id);
    }

    @WrapOperation(method = {SETUP, TEARDOWN}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;enableTexture()V"))
    private void argentum$enableTexture(Operation<Void> original) {
        if (!CapturedOverlay.skipGl()) original.call();
    }

    @WrapOperation(method = TEARDOWN, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;disableTexture()V"))
    private void argentum$disableTexture(Operation<Void> original) {
        if (!CapturedOverlay.skipGl()) original.call();
    }
}
