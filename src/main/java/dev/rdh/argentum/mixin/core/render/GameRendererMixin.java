package dev.rdh.argentum.mixin.core.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.FrustumData;
import net.minecraft.client.render.GameRenderer;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyExpressionValue(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Frustum;getInstance()Lnet/minecraft/client/render/FrustumData;"))
    private FrustumData argentum$keepFrustum(FrustumData frustum, @Share("frustum") LocalRef<FrustumData> shared) {
        shared.set(frustum);
        return frustum;
    }

    @WrapOperation(method = "render(IFJ)V", at = @At(value = "NEW", target = "()Lnet/minecraft/client/render/FrustumCuller;"))
    private FrustumCuller argentum$reuseFrustum(Operation<FrustumCuller> original, @Share("frustum") LocalRef<FrustumData> shared) {
        FrustumData frustum = shared.get();
        return frustum == null ? original.call() : new FrustumCuller(frustum);
    }
}
