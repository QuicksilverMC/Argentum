package dev.rdh.argentum.mixin.features.hud;

import dev.rdh.argentum.impl.render.gui.hud.item.GuiItemIcons;
import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {
    @Inject(method = "matrixMode", at = @At("HEAD"))
    private static void argentum$trackMatrixMode(int mode, CallbackInfo ci) {
        GuiItemIcons.matrixModeChanged(mode);
    }

    @Inject(method = "pushMatrix", at = @At("HEAD"))
    private static void argentum$trackPush(CallbackInfo ci) {
        GuiItemIcons.matrixPushed();
    }

    @Inject(method = "popMatrix", at = @At("HEAD"))
    private static void argentum$trackPop(CallbackInfo ci) {
        GuiItemIcons.matrixPopped();
    }

    @Inject(method = "loadIdentity", at = @At("HEAD"))
    private static void argentum$trackIdentity(CallbackInfo ci) {
        GuiItemIcons.identityLoaded();
    }

    @Inject(method = "scalef", at = @At("HEAD"))
    private static void argentum$trackScalef(float x, float y, float z, CallbackInfo ci) {
        GuiItemIcons.scaled(x, y);
    }

    @Inject(method = "scaled", at = @At("HEAD"))
    private static void argentum$trackScaled(double x, double y, double z, CallbackInfo ci) {
        GuiItemIcons.scaled(x, y);
    }

    @Inject(method = "ortho", at = @At("HEAD"))
    private static void argentum$trackOrtho(double left, double right, double bottom, double top, double near, double far, CallbackInfo ci) {
        GuiItemIcons.orthoApplied(left, right, bottom, top);
    }

    @Inject(method = "multMatrix", at = @At("HEAD"))
    private static void argentum$trackMultMatrix(FloatBuffer matrix, CallbackInfo ci) {
        GuiItemIcons.matrixMultiplied(matrix);
    }

    @Inject(method = "viewport", at = @At("HEAD"))
    private static void argentum$trackViewport(int x, int y, int width, int height, CallbackInfo ci) {
        GuiItemIcons.viewportChanged(width, height);
    }
}
