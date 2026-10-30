package dev.rdh.argentum.mixin.features.framebuffer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.ext.MinecraftExtension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.PostPass;
import net.minecraft.client.render.pipeline.RenderTarget;

@Mixin(PostPass.class)
public abstract class PostPassMixin {
    @Shadow
    @Final
    public RenderTarget inTarget;

    @Shadow
    @Final
    public RenderTarget outTarget;

    @Inject(method = "process", at = @At("HEAD"))
    private void argentum$requestMainFramebuffer(float tickDelta, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getRenderTarget();
        if (this.inTarget == main || this.outTarget == main) ((MinecraftExtension) minecraft).argentum$requestFramebuffer();
    }
}
