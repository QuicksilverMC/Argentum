package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.cera.modules.shaders.ShaderRender;
import com.ventooth.swansong.shader.StateGraph.Stack;
import net.minecraft.client.render.entity.layer.EnderDragonEyesLayer;
import net.minecraft.client.render.entity.layer.EndermanEyesLayer;
import net.minecraft.client.render.entity.layer.SpiderEyesLayer;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import net.minecraft.entity.living.LivingEntity;

@Mixin({SpiderEyesLayer.class, EndermanEyesLayer.class, EnderDragonEyesLayer.class})
public abstract class EyesLayerMixin {
    @WrapMethod(method = {
            "render(Lnet/minecraft/entity/living/mob/monster/SpiderEntity;FFFFFFF)V",
            "render(Lnet/minecraft/entity/living/mob/monster/EndermanEntity;FFFFFFF)V",
            "render(Lnet/minecraft/entity/living/mob/monster/boss/EnderDragonEntity;FFFFFFF)V"
    })
    private void cera$eyes(@Coerce LivingEntity entity, float walk, float speed, float tickDelta, float bob,
                           float yaw, float pitch, float scale, Operation<Void> original) {
        boolean managed = ShaderRender.isManaged();
        if (managed && ShaderRender.isShadowPass()) return;
        boolean pushed = managed && !ShaderRender.isLayerCaptured();
        if (pushed) ShaderRender.push(Stack.SpiderEyes);
        try {
            original.call(entity, walk, speed, tickDelta, bob, yaw, pitch, scale);
        } finally {
            if (managed) GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            if (pushed) ShaderRender.pop(Stack.SpiderEyes);
        }
    }

    @WrapOperation(method = {
            "render(Lnet/minecraft/entity/living/mob/monster/SpiderEntity;FFFFFFF)V",
            "render(Lnet/minecraft/entity/living/mob/monster/EndermanEntity;FFFFFFF)V",
            "render(Lnet/minecraft/entity/living/mob/monster/boss/EnderDragonEntity;FFFFFFF)V"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;disableAlphaTest()V"))
    private void cera$eyesAlphaTest(Operation<Void> original) {
        if (ShaderRender.isManaged()) {
            GlStateManager.enableAlphaTest();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0F);
        } else {
            original.call();
        }
    }

    @WrapOperation(method = {
            "render(Lnet/minecraft/entity/living/mob/monster/SpiderEntity;FFFFFFF)V",
            "render(Lnet/minecraft/entity/living/mob/monster/EndermanEntity;FFFFFFF)V",
            "render(Lnet/minecraft/entity/living/mob/monster/boss/EnderDragonEntity;FFFFFFF)V"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;blendFunc(II)V"))
    private void cera$eyesBlend(int src, int dst, Operation<Void> original) {
        if (ShaderRender.isManaged()) {
            original.call(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            original.call(src, dst);
        }
    }
}
