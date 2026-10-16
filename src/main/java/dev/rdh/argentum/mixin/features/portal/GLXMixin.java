package dev.rdh.argentum.mixin.features.portal;

import dev.rdh.argentum.impl.render.environment.EndPortalRenderer;
import net.minecraft.client.render.platform.GLX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GLX.class)
public abstract class GLXMixin {
    @Inject(method = "multiTexCoord2f", at = @At("HEAD"))
    private static void argentum$trackPortalLight(int unit, float u, float v, CallbackInfo ci) {
        EndPortalRenderer renderer = EndPortalRenderer.active();
        if (renderer != null) renderer.lightmap(unit, u, v);
    }
}
