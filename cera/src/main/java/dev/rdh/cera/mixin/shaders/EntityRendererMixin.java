package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.rdh.cera.modules.shaders.ShaderRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @WrapWithCondition(method = "postRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;renderShadow(Lnet/minecraft/entity/Entity;DDDFF)V"))
    private boolean cera$circleShadow(EntityRenderer<?> renderer, Entity entity, double dx, double dy, double dz, float yaw, float tickDelta) {
        if (!Minecraft.getInstance().cera$getShaderEngine().isInitializedImpl()) {
            return true;
        }
        if (Minecraft.getInstance().cera$getShaderEngine().shadowPassExists()) {
            return false;
        }
        ShaderRender.resetEntityColor();
        ShaderRender.fullBrightLightmap();
        return true;
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;getTextRenderer()Lnet/minecraft/client/render/TextRenderer;"), cancellable = true)
    private void cera$nameTag(CallbackInfo ci) {
        if (ShaderRender.isManaged()) {
            if (ShaderRender.isShadowPass()) {
                ci.cancel();
                return;
            }
            ShaderRender.resetEntityColor();
            ShaderRender.bindWhiteTexture();
        }
    }
}
