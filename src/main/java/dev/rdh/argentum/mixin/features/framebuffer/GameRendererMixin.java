package dev.rdh.argentum.mixin.features.framebuffer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import dev.rdh.argentum.impl.ext.MinecraftExtension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GLX;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    private Minecraft minecraft;

    @ModifyExpressionValue(method = "render(FJ)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/platform/GLX;usePostProcess:Z"))
    private boolean argentum$postProcessNeedsFramebuffer(boolean usePostProcess) {
        return usePostProcess && GLX.useFbo() && !((MinecraftExtension) this.minecraft).argentum$isRenderingDirect();
    }
}
