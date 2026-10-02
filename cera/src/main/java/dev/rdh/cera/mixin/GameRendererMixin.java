package dev.rdh.cera.mixin;

import net.minecraft.client.render.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import dev.rdh.cera.Cera;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
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
