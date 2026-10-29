package dev.rdh.argentum.mixin.features.framebuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.ext.MinecraftExtension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;

@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {
    @Shadow
    public int viewWidth;

    @Shadow
    public int viewHeight;

    @Inject(method = "bindWrite", at = @At("HEAD"), cancellable = true)
    private void argentum$bindWindow(boolean viewport, CallbackInfo ci) {
        if (this.argentum$isDirectMain()) {
            GLX.bindFramebuffer(GLX.GL_FRAMEBUFFER, 0);
            if (viewport) GlStateManager.viewport(0, 0, this.viewWidth, this.viewHeight);
            ci.cancel();
        }
    }

    @Inject(method = "draw(IIZ)V", at = @At("HEAD"), cancellable = true)
    private void argentum$skipBlit(int width, int height, boolean disableBlend, CallbackInfo ci) {
        if (this.argentum$isDirectMain()) ci.cancel();
    }

    @Unique
    private boolean argentum$isDirectMain() {
        Minecraft minecraft = Minecraft.getInstance();
        return ((MinecraftExtension) minecraft).argentum$isRenderingDirect() && minecraft.getRenderTarget() == (Object) this;
    }
}
