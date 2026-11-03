package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.layer.ItemInHandLayer;
import net.minecraft.client.render.model.entity.HumanoidModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {
    @Shadow
    @Final
    private LivingEntityRenderer<?> parent;

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/entity/HumanoidModel;translateRightArm(F)V"))
    private void cera$attachToCustomPart(HumanoidModel model, float scale, Operation<Void> original) {
        if (!Minecraft.getInstance().cera$getCustomEntityModels().transformRightHand(this.parent, scale)) {
            original.call(model, scale);
        }
    }
}
