package dev.rdh.cera.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.rdh.cera.Cera;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render(FJ)V", at = @At("HEAD"))
    private void cera$beginAnimationFrame(float tickDelta, long nanoTime, CallbackInfo ci) {
        Minecraft.getInstance().cera$getCustomEntityModels().animations().beginFrame();
    }

    @ModifyArg(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", ordinal = 0), index = 3)
    private float cera$fitCustomSkyBox(float far) {
//        return Math.max(far, 175.0F);
        if (Cera.CONFIG.customSky) {
            return Math.max(far, 175);
        } else {
            return far;
        }
    }
}
