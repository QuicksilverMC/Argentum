package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.layer.EnderDragonEyesLayer;
import net.minecraft.client.render.entity.layer.EndermanEyesLayer;
import net.minecraft.client.render.entity.layer.EntityRenderLayer;
import net.minecraft.client.render.entity.layer.SpiderEyesLayer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity> {
    @Shadow
    protected boolean solidRender;

    @Inject(method = "renderModel(Lnet/minecraft/entity/living/LivingEntity;FFFFFF)V", at = @At("HEAD"))
    private void cera$captureAnimationParameters(LivingEntity entity, float limbSwing, float limbSpeed, float age,
            float headYaw, float headPitch, float scale, CallbackInfo ci) {
        Minecraft.getInstance().cera$getCustomEntityModels().animations()
                .setModelParameters(limbSwing, limbSpeed, age, headYaw, headPitch, scale);
    }

    @WrapOperation(
            method = "renderLayers",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/layer/EntityRenderLayer;render(Lnet/minecraft/entity/living/LivingEntity;FFFFFFF)V")
    )
    private void cera$renderLayer(EntityRenderLayer<LivingEntity> layer, LivingEntity entity, float limbSwing,
            float limbSpeed, float tickDelta, float age, float headYaw, float headPitch, float scale, Operation<Void> original) {
        Runnable render = () -> Minecraft.getInstance().getTextureManager().cera$getEmissiveTextures().render(
                this.solidRender || EntityCapture.current() != null,
                () -> original.call(layer, entity, limbSwing, limbSpeed, tickDelta, age, headYaw, headPitch, scale));
        Object eyes = layer;
        if (!(eyes instanceof SpiderEyesLayer || eyes instanceof EndermanEyesLayer || eyes instanceof EnderDragonEyesLayer)) {
            render.run();
            return;
        }
        var models = Minecraft.getInstance().cera$getCustomEntityModels();
        models.hideTexturedParts(this);
        try {
            render.run();
        } finally {
            models.showTexturedParts();
        }
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;renderModel(Lnet/minecraft/entity/living/LivingEntity;FFFFFF)V")
    )
    private void cera$emissivePass(LivingEntityRenderer<T> self, LivingEntity entity, float limbAngle, float limbDistance,
            float tickDelta, float headYaw, float headPitch, float scale, Operation<Void> original) {
        Minecraft.getInstance().getTextureManager().cera$getEmissiveTextures().render(this.solidRender || EntityCapture.current() != null,
                () -> original.call(self, entity, limbAngle, limbDistance, tickDelta, headYaw, headPitch, scale));
    }
}
