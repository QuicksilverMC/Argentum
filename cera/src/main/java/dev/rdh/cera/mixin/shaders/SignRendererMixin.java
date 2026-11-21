package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.cera.modules.shaders.ShaderRender;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.block.entity.SignRenderer;
import net.minecraft.client.render.model.block.entity.SignModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SignRenderer.class, priority = 1100)
public abstract class SignRendererMixin {
    @WrapMethod(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V")
    private void cera$popSignText(SignBlockEntity sign, double x, double y, double z, float tickDelta, int breakProgress, Operation<Void> original) {
        boolean deferred = breakProgress < 0 && ShaderRender.deferSignText(sign);
        try {
            original.call(sign, x, y, z, tickDelta, breakProgress);
        } catch (RuntimeException e) {
            if (deferred) ShaderRender.undeferLastSignText();
            throw e;
        } finally {
            if (!ShaderRender.isSignTextPass()) ShaderRender.popTextBatch();
        }
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;depthMask(Z)V", ordinal = 0, shift = At.Shift.AFTER))
    private void cera$pushSignText(SignBlockEntity sign, double x, double y, double z, float tickDelta, int breakProgress, CallbackInfo ci) {
        if (!ShaderRender.isDeferringSignText()) ShaderRender.pushTextBatch();
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;depthMask(Z)V", ordinal = 1, shift = At.Shift.AFTER))
    private void cera$endSignText(SignBlockEntity sign, double x, double y, double z, float tickDelta, int breakProgress, CallbackInfo ci) {
        if (!ShaderRender.isSignTextPass()) ShaderRender.popTextBatch();
    }

    @WrapWithCondition(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/block/entity/SignModel;render()V"))
    private boolean cera$skipModelInTextPass(SignModel model) {
        return !ShaderRender.isSignTextPass();
    }

    @WrapOperation(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I"))
    private int cera$deferText(TextRenderer textRenderer, String text, int x, int y, int color, Operation<Integer> original) {
        return ShaderRender.isDeferringSignText() ? 0 : original.call(textRenderer, text, x, y, color);
    }
}
