package dev.rdh.cera.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GLX.class)
public class GLXMixin {
    @Inject(method = "multiTexCoord2f", at = @At("HEAD"))
    private static void cera$captureBrightness(int unit, float x, float y, CallbackInfo ci) {
        if (unit != GLX.GL_TEXTURE1) return;
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        if (textureManager != null) textureManager.cera$getEmissiveTextures().captureBrightness(x, y);
    }
}
