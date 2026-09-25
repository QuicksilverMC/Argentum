package dev.rdh.argentum.mixin.features.model.instancing;

import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {
    @Inject(method = "matrixMode", at = @At("HEAD"))
    private static void celeritas$captureEntityMatrixMode(int mode, CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null) capture.setMatrixMode(mode);
    }

    @Inject(method = "loadIdentity", at = @At("HEAD"))
    private static void celeritas$loadEntityIdentity(CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null) capture.loadIdentity();
    }

    @Inject(method = "color4f", at = @At("HEAD"))
    private static void celeritas$captureEntityColor(float red, float green, float blue, float alpha, CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null) capture.setColor(red, green, blue, alpha);
    }

    @Inject(method = "color3f", at = @At("HEAD"))
    private static void celeritas$captureEntityColor(float red, float green, float blue, CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null) capture.setColor(red, green, blue, 1.0F);
    }

    @Inject(method = "pushMatrix", at = @At("HEAD"), cancellable = true)
    private static void celeritas$pushEntityMatrix(CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null && capture.pushMatrix()) {
            ci.cancel();
        }
    }

    @Inject(method = "popMatrix", at = @At("HEAD"), cancellable = true)
    private static void celeritas$popEntityMatrix(CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null && capture.popMatrix()) {
            ci.cancel();
        }
    }

    @Inject(method = "translatef", at = @At("HEAD"), cancellable = true)
    private static void celeritas$translateEntity(float x, float y, float z, CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null && capture.translate(x, y, z)) {
            ci.cancel();
        }
    }

    @Inject(method = "translated", at = @At("HEAD"), cancellable = true)
    private static void celeritas$translateEntity(double x, double y, double z, CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null && capture.translate((float)x, (float)y, (float)z)) {
            ci.cancel();
        }
    }

    @Inject(method = "rotatef", at = @At("HEAD"), cancellable = true)
    private static void celeritas$rotateEntity(float angle, float x, float y, float z, CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null && capture.rotate(angle, x, y, z)) {
            ci.cancel();
        }
    }

    @Inject(method = "scalef", at = @At("HEAD"), cancellable = true)
    private static void celeritas$scaleEntity(float x, float y, float z, CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null && capture.scale(x, y, z)) {
            ci.cancel();
        }
    }

    @Inject(method = "scaled", at = @At("HEAD"), cancellable = true)
    private static void celeritas$scaleEntity(double x, double y, double z, CallbackInfo ci) {
        EntityCapture capture = EntityCapture.current();
        if (capture != null && capture.scale((float)x, (float)y, (float)z)) {
            ci.cancel();
        }
    }
}
