package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.cera.ext.CeraEntityRendererExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
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

    @WrapOperation(
            method = "postRender",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;renderShadow(Lnet/minecraft/entity/Entity;DDDFF)V")
    )
    private void cera$offsetShadow(EntityRenderer<?> renderer, Entity entity, double x, double y, double z, float opacity,
            float tickDelta, Operation<Void> original) {
        var animations = Minecraft.getInstance().cera$getCustomEntityModels().animations();
        original.call(renderer, entity, x + animations.shadowOffsetX(), y, z + animations.shadowOffsetZ(), opacity, tickDelta);
    }
}
