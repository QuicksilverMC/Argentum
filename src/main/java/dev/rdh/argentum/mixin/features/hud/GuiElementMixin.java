package dev.rdh.argentum.mixin.features.hud;

import dev.rdh.argentum.impl.render.hud.GradientBatch;

import net.minecraft.client.gui.GuiElement;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiElement.class)
public class GuiElementMixin {
    @Shadow
    protected float drawOffset;

    @Inject(method = "fillGradient", at = @At("HEAD"), cancellable = true)
    private void argentum$batchGradient(int left, int top, int right, int bottom, int colorTop, int colorBottom, CallbackInfo ci) {
        if (GradientBatch.record(left, top, right, bottom, colorTop, colorBottom, this.drawOffset)) {
            ci.cancel();
        }
    }
}
