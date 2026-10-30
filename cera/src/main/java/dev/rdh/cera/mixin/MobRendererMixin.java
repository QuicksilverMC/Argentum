package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.MobRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MobRenderer.class)
public class MobRendererMixin {
    @ModifyExpressionValue(method = "renderRiders", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/MobRenderer;lerp(DDD)D", ordinal = 6))
    private double cera$leashOffsetX(double x) {
        return x + Minecraft.getInstance().cera$getCustomEntityModels().animations().leashOffset(0);
    }

    @ModifyExpressionValue(method = "renderRiders", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/MobRenderer;lerp(DDD)D", ordinal = 7))
    private double cera$leashOffsetY(double y) {
        return y + Minecraft.getInstance().cera$getCustomEntityModels().animations().leashOffset(1);
    }

    @ModifyExpressionValue(method = "renderRiders", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/MobRenderer;lerp(DDD)D", ordinal = 8))
    private double cera$leashOffsetZ(double z) {
        return z + Minecraft.getInstance().cera$getCustomEntityModels().animations().leashOffset(2);
    }
}
