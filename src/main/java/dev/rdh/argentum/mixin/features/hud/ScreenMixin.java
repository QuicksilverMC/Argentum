package dev.rdh.argentum.mixin.features.hud;

import dev.rdh.argentum.impl.render.hud.GradientBatch;

import net.minecraft.client.gui.screen.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "renderTooltip(Ljava/util/List;II)V", at = @At("HEAD"))
    private void argentum$batchTooltipBackground(List<String> lines, int x, int y, CallbackInfo ci) {
        GradientBatch.begin();
    }

    @Inject(method = "renderTooltip(Ljava/util/List;II)V", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;drawWithShadow(Ljava/lang/String;FFI)I"),
            @At("RETURN")
    })
    private void argentum$drawTooltipBackground(List<String> lines, int x, int y, CallbackInfo ci) {
        GradientBatch.flush();
    }
}
