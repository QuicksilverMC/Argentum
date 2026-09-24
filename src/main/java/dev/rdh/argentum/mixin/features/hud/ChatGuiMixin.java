package dev.rdh.argentum.mixin.features.hud;

import dev.rdh.argentum.impl.render.gui.hud.HudBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.client.render.platform.GlStateManager;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatGui.class)
public abstract class ChatGuiMixin extends GuiElement {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private HudBatch.Colored argentum$backgroundBatch;

    @Unique
    private HudBatch.Text argentum$textBatch;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void argentum$createBackgroundBatch(Minecraft minecraft, CallbackInfo ci) {
        this.argentum$backgroundBatch = HudBatch.colored();
        this.argentum$textBatch = HudBatch.text(this.minecraft.textRenderer, this::argentum$prepareTextBatch);
    }

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;scalef(FFF)V", shift = At.Shift.AFTER)
    )
    private void argentum$beginTextBatch(int ticks, CallbackInfo ci) {
        this.argentum$textBatch.begin();
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/chat/ChatGui;fill(IIIII)V")
    )
    private void argentum$captureBackground(int left, int top, int right, int bottom, int color) {
        if (this.argentum$textBatch.isDrawing()) {
            this.argentum$backgroundBatch.fill(left, top, right, bottom, color);
        } else {
            GuiElement.fill(left, top, right, bottom, color);
        }
    }

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;translatef(FFF)V", ordinal = 1)
    )
    private void argentum$drawTextBeforeScrollbar(int ticks, CallbackInfo ci) {
        this.argentum$drawTextBatch();
    }

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;popMatrix()V")
    )
    private void argentum$drawTextBatch(int ticks, CallbackInfo ci) {
        this.argentum$drawTextBatch();
    }

    @Unique
    private void argentum$drawTextBatch() {
        if (!this.argentum$textBatch.isDrawing()) {
            return;
        }
        this.argentum$textBatch.draw();
        GlStateManager.disableAlphaTest();
        GlStateManager.disableBlend();
    }

    @Unique
    private void argentum$prepareTextBatch() {
        this.argentum$backgroundBatch.draw();
        GlStateManager.enableBlend();
        GlStateManager.enableAlphaTest();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
    }
}
