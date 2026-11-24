package dev.rdh.argentum.mixin.features.hud;

import dev.rdh.argentum.impl.render.gui.hud.ScreenBatch;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @WrapOperation(method = "render(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(IIF)V"))
    private void argentum$batchScreen(Screen screen, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        ScreenBatch.begin();
        try {
            original.call(screen, mouseX, mouseY, tickDelta);
        } finally {
            ScreenBatch.end();
        }
    }
}
