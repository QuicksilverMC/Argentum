package dev.rdh.argentum.mixin.core.state;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import dev.rdh.argentum.impl.render.GlStateMirror;
import net.minecraft.client.render.GameRenderer;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @WrapOperation(
            method = {"setupCamera", "renderItemInHand", "render(IFJ)V", "renderClouds"},
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V"),
            require = 6
    )
    private void argentum$mirrorPerspective(float fov, float aspect, float near, float far, Operation<Void> original) {
        original.call(fov, aspect, near, far);
        GlStateMirror.perspective(fov, aspect, near, far);
    }
}
