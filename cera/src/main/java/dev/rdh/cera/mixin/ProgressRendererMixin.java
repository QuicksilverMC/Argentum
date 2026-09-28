package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import dev.rdh.cera.modules.CustomLoadingScreens.LoadingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.ProgressRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ProgressRenderer.class)
public class ProgressRendererMixin {
    @Shadow
    private Minecraft minecraft;

    @WrapWithCondition(method = "progressStagePercentage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 0))
    private boolean cera$drawCustomBackground(Tesselator tesselator, @Local(ordinal = 2) int width, @Local(ordinal = 3) int height) {
        LoadingScreen screen = this.minecraft.cera$getCustomLoadingScreens().active();
        if (screen != null) {
            tesselator.getBuffer().end();
            screen.draw(width, height);
            return false;
        }
        return true;
    }
}
