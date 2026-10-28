package dev.rdh.argentum.mixin.features.framebuffer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.ext.MinecraftExtension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.Tesselator;

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

    @ModifyArg(method = "draw(IIZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;colorMask(ZZZZ)V", ordinal = 0), index = 3)
    private boolean argentum$writeWindowAlpha(boolean alpha) {
        return alpha || Minecraft.getInstance().getRenderTarget() == (Object) this;
    }

    @WrapOperation(method = "draw(IIZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V"))
    private void argentum$blitOpaque(Tesselator tesselator, Operation<Void> original) {
        if (Minecraft.getInstance().getRenderTarget() != (Object) this) {
            original.call(tesselator);
            return;
        }

        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL13.GL_PRIMARY_COLOR);
        original.call(tesselator);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
    }

    @Unique
    private boolean argentum$isDirectMain() {
        Minecraft minecraft = Minecraft.getInstance();
        return ((MinecraftExtension) minecraft).argentum$isRenderingDirect() && minecraft.getRenderTarget() == (Object) this;
    }
}
