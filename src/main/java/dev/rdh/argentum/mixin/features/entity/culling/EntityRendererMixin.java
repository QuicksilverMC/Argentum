package dev.rdh.argentum.mixin.features.entity.culling;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @ModifyArg(method = "shouldRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Culler;isVisible(Lnet/minecraft/util/math/Box;)Z"))
    private Box argentum$padFrustumBox(Box box) {
        return box.grown(0.5, 0.5, 0.5);
    }
}
