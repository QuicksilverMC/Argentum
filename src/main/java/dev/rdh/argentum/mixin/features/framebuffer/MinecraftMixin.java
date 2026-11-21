package dev.rdh.argentum.mixin.features.framebuffer;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.compat.ScreencapperCompat;
import dev.rdh.argentum.impl.ext.MinecraftExtension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.text.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin implements MinecraftExtension {
    @Shadow
    public GameRenderer gameRenderer;

    @Shadow
    public WorldRenderer worldRenderer;

    @Shadow
    public Screen screen;

    @Shadow
    public GameGui gui;

    @Shadow
    @Final
    public File gameDir;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Shadow
    private RenderTarget renderTarget;

    @Unique
    private boolean argentum$renderingDirect;

    @Unique
    private boolean argentum$framebufferRequested;

    @Unique
    private final List<Runnable> argentum$framebufferFrameTasks = new ArrayList<>();

    @Override
    public boolean argentum$isRenderingDirect() {
        return this.argentum$renderingDirect;
    }

    @Override
    public void argentum$requestFramebuffer() {
        this.argentum$framebufferRequested = true;
    }

    @Override
    public void argentum$runOnFramebufferFrame(Runnable task) {
        this.argentum$framebufferFrameTasks.add(task);
    }

    @Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/pipeline/RenderTarget;bindWrite(Z)V"))
    private void argentum$chooseFramebuffer(CallbackInfo ci) {
        this.argentum$renderingDirect = Argentum.CONFIG.directRendering && GLX.useFbo()
                && this.screen == null
                && !this.gameRenderer.hasShader()
                && !((WorldRendererInvoker) this.worldRenderer).invokeShouldRenderEntityOutlines()
                && !this.argentum$framebufferRequested
                && this.argentum$framebufferFrameTasks.isEmpty()
                && !ScreencapperCompat.isBusy();
        this.argentum$framebufferRequested = false;
    }

    @WrapOperation(method = "handleGuiKeyBindings", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;take(Ljava/io/File;IILnet/minecraft/client/render/pipeline/RenderTarget;)Lnet/minecraft/text/Text;"))
    private Text argentum$deferScreenshot(File gameDir, int width, int height, RenderTarget target, Operation<Text> original) {
        this.argentum$runOnFramebufferFrame(() -> this.gui.getChat().addMessage(Screenshot.take(this.gameDir, this.width, this.height, this.renderTarget)));
        return null;
    }

    @WrapWithCondition(method = "handleGuiKeyBindings", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/chat/ChatGui;addMessage(Lnet/minecraft/text/Text;)V"))
    private boolean argentum$skipDeferredScreenshotMessage(ChatGui chat, Text message) {
        return message != null;
    }

    @Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateDisplay()V"))
    private void argentum$runFramebufferFrameTasks(CallbackInfo ci) {
        if (this.argentum$framebufferFrameTasks.isEmpty() || this.argentum$renderingDirect) return;
        List<Runnable> tasks = new ArrayList<>(this.argentum$framebufferFrameTasks);
        this.argentum$framebufferFrameTasks.clear();
        GLX.bindFramebuffer(GLX.GL_FRAMEBUFFER, 0);
        tasks.forEach(Runnable::run);
    }
}
