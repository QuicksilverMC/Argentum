package dev.rdh.argentum.mixin.core.state;

import java.nio.FloatBuffer;

import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.rdh.argentum.impl.render.GlStateMirror;
import net.minecraft.client.render.platform.GlStateManager;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {
    @Inject(method = "matrixMode", at = @At("TAIL"))
    private static void argentum$matrixMode(int mode, CallbackInfo ci) {
        GlStateMirror.matrixMode(mode);
    }

    @Inject(method = "loadIdentity", at = @At("TAIL"))
    private static void argentum$loadIdentity(CallbackInfo ci) {
        GlStateMirror.loadIdentity();
    }

    @Inject(method = "pushMatrix", at = @At("TAIL"))
    private static void argentum$pushMatrix(CallbackInfo ci) {
        GlStateMirror.push();
    }

    @Inject(method = "popMatrix", at = @At("TAIL"))
    private static void argentum$popMatrix(CallbackInfo ci) {
        GlStateMirror.pop();
    }

    @Inject(method = "ortho", at = @At("TAIL"))
    private static void argentum$ortho(double l, double r, double b, double t, double n, double f, CallbackInfo ci) {
        GlStateMirror.ortho(l, r, b, t, n, f);
    }

    @Inject(method = "rotatef", at = @At("TAIL"))
    private static void argentum$rotatef(float angle, float x, float y, float z, CallbackInfo ci) {
        GlStateMirror.rotate(angle, x, y, z);
    }

    @Inject(method = "scalef", at = @At("TAIL"))
    private static void argentum$scalef(float x, float y, float z, CallbackInfo ci) {
        GlStateMirror.scale(x, y, z);
    }

    @Inject(method = "scaled", at = @At("TAIL"))
    private static void argentum$scaled(double x, double y, double z, CallbackInfo ci) {
        GlStateMirror.scale(x, y, z);
    }

    @Inject(method = "translatef", at = @At("TAIL"))
    private static void argentum$translatef(float x, float y, float z, CallbackInfo ci) {
        GlStateMirror.translate(x, y, z);
    }

    @Inject(method = "translated", at = @At("TAIL"))
    private static void argentum$translated(double x, double y, double z, CallbackInfo ci) {
        GlStateMirror.translate(x, y, z);
    }

    @Inject(method = "multMatrix", at = @At("TAIL"))
    private static void argentum$multMatrix(FloatBuffer m, CallbackInfo ci) {
        GlStateMirror.multiply(m);
    }

    @Inject(method = "viewport", at = @At("TAIL"))
    private static void argentum$viewport(int x, int y, int width, int height, CallbackInfo ci) {
        GlStateMirror.viewport(x, y, width, height);
    }

    @Inject(method = "getFloat", at = @At("HEAD"), cancellable = true)
    private static void argentum$getFloat(int parameter, FloatBuffer out, CallbackInfo ci) {
        if (!GlStateMirror.readMatrix(parameter, out)) return;
        if (GlStateMirror.VERIFY) {
            FloatBuffer actual = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(parameter, actual);
            for (int i = 0; i < 16; i++) {
                if (Math.abs(actual.get(i) - out.get(out.position() + i)) > 1.0E-4F) {
                    LogManager.getLogger("argentum").warn("GL state mirror mismatch for {} at {}: mirror={} gl={}",
                            parameter, i, out.get(out.position() + i), actual.get(i));
                    break;
                }
            }
        }
        ci.cancel();
    }
}
