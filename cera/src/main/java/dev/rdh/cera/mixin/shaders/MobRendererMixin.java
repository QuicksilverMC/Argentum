package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.rdh.cera.modules.shaders.ShaderRender;
import com.ventooth.swansong.shader.StateGraph.Stack;
import net.minecraft.client.render.entity.MobRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(MobRenderer.class)
public abstract class MobRendererMixin {
    @WrapOperation(method = "renderRiders", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/living/mob/MobEntity;getLeashHolder()Lnet/minecraft/entity/Entity;"))
    private Entity cera$pushLeash(MobEntity mob, Operation<Entity> original, @Share("leash") LocalBooleanRef pushed) {
        var holder = original.call(mob);
        if (holder != null && ShaderRender.isManaged()) {
            ShaderRender.bindWhiteTexture();
            ShaderRender.push(Stack.Leash);
            pushed.set(true);
        }
        return holder;
    }

    @WrapMethod(method = "renderRiders")
    private void cera$leash(@Coerce MobEntity mob, double x, double y, double z, float yaw, float tickDelta,
                            Operation<Void> original, @Share("leash") LocalBooleanRef pushed) {
        try {
            original.call(mob, x, y, z, yaw, tickDelta);
        } finally {
            if (pushed.get()) ShaderRender.pop(Stack.Leash);
        }
    }
}
