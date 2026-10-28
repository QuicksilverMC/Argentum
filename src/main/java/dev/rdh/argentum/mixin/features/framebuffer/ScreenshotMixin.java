package dev.rdh.argentum.mixin.features.framebuffer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.rdh.argentum.impl.ext.MinecraftExtension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
    @ModifyExpressionValue(method = "take(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/render/pipeline/RenderTarget;)Lnet/minecraft/text/Text;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;useFbo()Z"))
    private static boolean argentum$readWindowWhenDirect(boolean useFbo) {
        return useFbo && !((MinecraftExtension) Minecraft.getInstance()).argentum$isRenderingDirect();
    }
}
