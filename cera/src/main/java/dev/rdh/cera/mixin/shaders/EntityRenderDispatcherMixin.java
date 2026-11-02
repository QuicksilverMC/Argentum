package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.cera.modules.shaders.ShaderRender;
import com.ventooth.swansong.shader.StateGraph.Stack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "render(Lnet/minecraft/entity/Entity;F)Z", at = @At("HEAD"), require = 1)
    private void cera$entityId(Entity entity, float tickDelta, CallbackInfoReturnable<Boolean> cir) {
        Minecraft.getInstance().cera$getShaderEngine().nextEntity(entity);
    }

    @WrapMethod(method = "renderHitbox")
    private void cera$hitbox(Entity entity, double x, double y, double z, float yaw, float tickDelta,
                             Operation<Void> original) {
        boolean pushed = ShaderRender.isManaged();
        if (pushed && ShaderRender.isShadowPass()) return;
        if (pushed) ShaderRender.push(Stack.AABBOutline);
        try {
            if (pushed) {
                ShaderRender.bindWhiteTexture();
                ShaderRender.fullBrightLightmap();
            }
            original.call(entity, x, y, z, yaw, tickDelta);
        } finally {
            if (pushed) ShaderRender.pop(Stack.AABBOutline);
        }
    }
}
