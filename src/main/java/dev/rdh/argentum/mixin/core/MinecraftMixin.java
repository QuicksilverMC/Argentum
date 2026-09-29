package dev.rdh.argentum.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GLX;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.embeddedt.embeddium.impl.render.frame.RenderAheadManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.compat.NvidiaWorkarounds;
import dev.rdh.argentum.impl.render.gui.hud.item.GuiItemIcons;

import java.util.ArrayList;
import java.util.List;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Unique
    private RenderAheadManager celeritas$renderAheadManager;

    @Shadow
    private boolean logGlErrors;

    @Inject(method = "initDisplay", at = @At("HEAD"))
    private void argentum$installNvidiaWorkarounds(CallbackInfo ci) {
        NvidiaWorkarounds.install();
    }

    @Inject(method = "initDisplay", at = @At("RETURN"))
    private void argentum$uninstallNvidiaWorkarounds(CallbackInfo ci) {
        NvidiaWorkarounds.uninstall();
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void argentum$configureGlErrorChecking(CallbackInfo ci) {
        this.logGlErrors = Argentum.CONFIG.checkGlErrors;
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void argentum$checkCapabilities(CallbackInfo ci) {
        GLCapabilities caps = GL.getCapabilities();
        List<String> missing = new ArrayList<>();
        if (!caps.OpenGL20) missing.add("OpenGL 2.0");
        if (!caps.OpenGL32 && !caps.GL_ARB_draw_elements_base_vertex) missing.add("GL_ARB_draw_elements_base_vertex");
        if (!caps.OpenGL30 && !caps.GL_EXT_gpu_shader4) missing.add("GL_EXT_gpu_shader4");
        if (!caps.OpenGL30 && !caps.GL_ARB_vertex_array_object && !caps.GL_APPLE_vertex_array_object) missing.add("GL_ARB_vertex_array_object");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Argentum requires " + String.join(", ", missing) + " (GL_VERSION " + GL11.glGetString(GL11.GL_VERSION) + ", GL_RENDERER " + GL11.glGetString(GL11.GL_RENDERER) + ")");
        }
        if (Argentum.renderAheadSupported()) this.celeritas$renderAheadManager = new RenderAheadManager();
    }

    @Inject(method = "runGame", at = @At("HEAD"))
    private void celeritas$startFrame(CallbackInfo ci) {
        if (this.celeritas$renderAheadManager != null) this.celeritas$renderAheadManager.startFrame(Argentum.CONFIG.cpuRenderAheadLimit);
    }

    @Inject(method = "runGame", at = @At("RETURN"))
    private void celeritas$endFrame(CallbackInfo ci) {
        GuiItemIcons.endFrame();
        if (this.celeritas$renderAheadManager != null) this.celeritas$renderAheadManager.endFrame();
    }

    @Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/pipeline/RenderTarget;unbindWrite()V"))
    private void argentum$flushGuiItems(CallbackInfo ci) {
        GuiItemIcons.flush();
    }

    @WrapWithCondition(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;clear(I)V"))
    private boolean argentum$needsBackbufferClear(int mask) {
        return !GLX.useFbo();
    }

    @WrapWithCondition(method = "runGame", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;yield()V"))
    private boolean argentum$conditionallyYield() {
        return !Argentum.CONFIG.greedyRenderThread;
    }
}
