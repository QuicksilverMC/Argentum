package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.rdh.cera.ext.CeraEntityRendererExtension;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin implements CeraEntityRendererExtension {
    @Unique
    private Identifier cera$customTexture;

    @Override
    public Identifier cera$getCustomTexture() {
        return this.cera$customTexture;
    }

    @Override
    public void cera$setCustomTexture(Identifier texture) {
        this.cera$customTexture = texture;
    }

    @ModifyExpressionValue(
            method = "bindTexture(Lnet/minecraft/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;getTextureLocation(Lnet/minecraft/entity/Entity;)Lnet/minecraft/resource/Identifier;")
    )
    private Identifier cera$customModelTexture(Identifier texture) {
        return this.cera$customTexture != null ? this.cera$customTexture : texture;
    }
}
