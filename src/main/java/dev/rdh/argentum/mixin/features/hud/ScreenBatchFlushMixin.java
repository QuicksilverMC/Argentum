package dev.rdh.argentum.mixin.features.hud;

import dev.rdh.argentum.impl.render.gui.hud.ScreenBatch;

import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BufferUploader.class, GLX.class})
public class ScreenBatchFlushMixin {
    @Inject(method = "end", at = @At("HEAD"), require = 0)
    private void argentum$flushBeforeDraw(BufferBuilder buffer, CallbackInfo ci) {
        ScreenBatch.flush();
    }

    @Inject(method = "bindFramebuffer", at = @At("HEAD"), require = 0)
    private static void argentum$flushBeforeFramebuffer(int target, int framebuffer, CallbackInfo ci) {
        ScreenBatch.flush();
    }
}
