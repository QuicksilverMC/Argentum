package dev.rdh.argentum.mixin.core;

import net.minecraft.client.Minecraft;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.embeddedt.embeddium.impl.render.frame.RenderAheadManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.compat.NvidiaWorkarounds;
import dev.rdh.argentum.impl.render.gui.hud.item.GuiItemIcons;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Unique
    private final RenderAheadManager celeritas$renderAheadManager = new RenderAheadManager();

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

    @Inject(method = "runGame", at = @At("HEAD"))
    private void celeritas$startFrame(CallbackInfo ci) {
        this.celeritas$renderAheadManager.startFrame(Argentum.CONFIG.cpuRenderAheadLimit);
    }

    @Inject(method = "runGame", at = @At("RETURN"))
    private void celeritas$endFrame(CallbackInfo ci) {
        GuiItemIcons.warnIfPending();
        this.celeritas$renderAheadManager.endFrame();
    }

    @WrapWithCondition(method = "runGame", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;yield()V"))
    private boolean argentum$conditionallyYield() {
        return !Argentum.CONFIG.greedyRenderThread;
    }
}
