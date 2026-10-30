package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.layer.WornSkullLayer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WornSkullLayer.class)
public class WornSkullLayerMixin {
    @WrapMethod(method = "render(Lnet/minecraft/entity/living/LivingEntity;FFFFFFF)V")
    private void cera$markOnHead(LivingEntity entity, float limbSwing, float limbSpeed, float tickDelta, float age,
            float headYaw, float headPitch, float scale, Operation<Void> original) {
        var animations = Minecraft.getInstance().cera$getCustomEntityModels().animations();
        boolean previous = animations.setOnHead(true);
        try {
            original.call(entity, limbSwing, limbSpeed, tickDelta, age, headYaw, headPitch, scale);
        } finally {
            animations.setOnHead(previous);
        }
    }
}
