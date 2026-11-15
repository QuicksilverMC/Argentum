package dev.rdh.argentum.extras.mixin;

import dev.rdh.argentum.extras.ArgentumExtras;

import net.minecraft.client.gui.ToastGui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastGui.class)
public class ToastGuiMixin {
    @Inject(method = {"set", "setTutorial"}, at = @At("HEAD"), cancellable = true)
    private void argentumExtras$hideAchievementToasts(CallbackInfo ci) {
        if (!ArgentumExtras.CONFIG.achievementToasts) ci.cancel();
    }
}
