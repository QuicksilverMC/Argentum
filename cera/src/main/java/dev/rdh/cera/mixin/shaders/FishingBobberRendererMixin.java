package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.cera.modules.shaders.ShaderRender;
import com.ventooth.swansong.shader.StateGraph.Stack;
import net.minecraft.client.render.entity.FishingBobberRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FishingBobberRenderer.class)
public abstract class FishingBobberRendererMixin {
    @WrapOperation(method = "render(Lnet/minecraft/entity/FishingBobberEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 1))
    private void cera$wrapLine(Tesselator tesselator, Operation<Void> original) {
        if (ShaderRender.isManaged()) {
            ShaderRender.bindWhiteTexture();
            ShaderRender.push(Stack.Leash);
            try {
                original.call(tesselator);
            } finally {
                ShaderRender.pop(Stack.Leash);
            }
        } else {
            original.call(tesselator);
        }
    }
}
