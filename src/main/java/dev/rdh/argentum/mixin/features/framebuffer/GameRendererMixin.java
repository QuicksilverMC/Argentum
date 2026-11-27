package dev.rdh.argentum.mixin.features.framebuffer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.objectweb.asm.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GLX;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    private Minecraft minecraft;

    @ModifyExpressionValue(method = "render(FJ)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/platform/GLX;usePostProcess:Z", opcode = Opcodes.GETSTATIC))
    private boolean argentum$postProcessNeedsFramebuffer(boolean usePostProcess) {
        return usePostProcess && GLX.useFbo() && !this.minecraft.argentum$isRenderingDirect();
    }
}
