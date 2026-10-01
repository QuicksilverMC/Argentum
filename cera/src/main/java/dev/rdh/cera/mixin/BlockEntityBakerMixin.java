package dev.rdh.cera.mixin;

import dev.rdh.argentum.impl.render.blockentity.BlockEntityBaker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockEntityBaker.class, remap = false)
public class BlockEntityBakerMixin {
    @Inject(method = "useTexture", at = @At("HEAD"), cancellable = true)
    private void cera$skipCustomTextures(String texture, CallbackInfoReturnable<Boolean> cir) {
        Identifier sprite = new Identifier(texture);
        Identifier file = new Identifier(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        if (textures.cera$getRandomEntities().hasVariants(file) || textures.cera$getEmissiveTextures().hasEmissiveTexture(file)) {
            cir.setReturnValue(false);
        }
    }
}
