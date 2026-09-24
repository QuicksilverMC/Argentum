package dev.rdh.argentum.mixin.core.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.argentum.impl.debug.DebugStrings;
import dev.rdh.argentum.impl.render.gui.hud.HudBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.overlay.DebugOverlay;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugOverlay.class)
public class DebugOverlayMixin {
    @Shadow
    @Final
    private TextRenderer textRenderer;

    @Unique
    private HudBatch.Colored argentum$backgroundBatch;

    @Unique
    private HudBatch.Text argentum$textBatch;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void argentum$createBackgroundBatch(Minecraft minecraft, CallbackInfo ci) {
        this.argentum$backgroundBatch = HudBatch.colored();
        this.argentum$textBatch = HudBatch.text(this.textRenderer, this.argentum$backgroundBatch);
    }

    @WrapMethod(method = "render")
    private void argentum$alwaysDrawTextBatch(Window window, Operation<Void> original) {
        try {
            original.call(window);
        } finally {
            this.argentum$textBatch.draw();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void argentum$beginTextBatch(Window window, CallbackInfo ci) {
        this.argentum$textBatch.begin();
    }

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;popMatrix()V")
    )
    private void argentum$drawTextBatch(Window window, CallbackInfo ci) {
        this.argentum$textBatch.draw();
    }

    @Redirect(
            method = {"drawGameInfo", "drawSystemInfo"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/overlay/DebugOverlay;fill(IIIII)V")
    )
    private void argentum$captureBackground(int left, int top, int right, int bottom, int color) {
        this.argentum$backgroundBatch.fill(left, top, right, bottom, color);
    }

    @Inject(method = "getSystemInfo", at = @At("RETURN"))
    private void appendArgentumSystemInfo(CallbackInfoReturnable<List<String>> cir) {
        var strings = cir.getReturnValue();
        strings.add("");
        strings.addAll(
                DebugStrings.getStringsToRender().stream()
                        .map(pair -> pair.right().toString() + pair.left())
                        .toList()
        );
    }
}
