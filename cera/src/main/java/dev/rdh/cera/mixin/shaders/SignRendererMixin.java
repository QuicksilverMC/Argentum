package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ventooth.swansong.shader.StateGraph.Stack;
import dev.rdh.cera.modules.shaders.ShaderRender;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.render.block.entity.SignRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SignRenderer.class)
abstract class SignRendererMixin {
    @Unique
    private boolean cera$textPushed;

    @WrapMethod(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V")
    private void cera$balanceText(SignBlockEntity sign, double x, double y, double z, float tickDelta, int breakProgress, Operation<Void> original) {
        try {
            original.call(sign, x, y, z, tickDelta, breakProgress);
        } finally {
            this.cera$popText();
        }
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;depthMask(Z)V", ordinal = 0, shift = At.Shift.AFTER))
    private void cera$pushText(CallbackInfo ci) {
        if (ShaderRender.isManaged()) {
            ShaderRender.push(Stack.Text);
            this.cera$textPushed = true;
        }
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;depthMask(Z)V", ordinal = 1))
    private void cera$endText(CallbackInfo ci) {
        this.cera$popText();
    }

    @Unique
    private void cera$popText() {
        if (this.cera$textPushed) {
            this.cera$textPushed = false;
            ShaderRender.pop(Stack.Text);
        }
    }
}
