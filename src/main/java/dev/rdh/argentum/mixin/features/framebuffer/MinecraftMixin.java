package dev.rdh.argentum.mixin.features.framebuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.ext.MinecraftExtension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.world.WorldRenderer;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin implements MinecraftExtension {
    @Shadow
    public GameRenderer gameRenderer;

    @Shadow
    public WorldRenderer worldRenderer;

    @Unique
    private boolean argentum$renderingDirect;

    @Unique
    private boolean argentum$framebufferRequested;

    @Override
    public boolean argentum$isRenderingDirect() {
        return this.argentum$renderingDirect;
    }

    @Override
    public void argentum$requestFramebuffer() {
        this.argentum$framebufferRequested = true;
    }

    @Inject(method = "runGame", at = @At("HEAD"))
    private void argentum$chooseFramebuffer(CallbackInfo ci) {
        this.argentum$renderingDirect = Argentum.CONFIG.directRendering && GLX.useFbo()
                && !this.gameRenderer.hasShader()
                && !((WorldRendererInvoker) this.worldRenderer).invokeShouldRenderEntityOutlines()
                && !this.argentum$framebufferRequested;
        this.argentum$framebufferRequested = false;
    }
}
