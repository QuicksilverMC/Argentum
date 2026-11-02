package dev.rdh.cera.mixin.colors;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.layer.WolfCollarLayer;
import net.minecraft.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WolfCollarLayer.class)
public class WolfCollarLayerMixin {
    @WrapOperation(method = "render(Lnet/minecraft/entity/living/mob/passive/animal/tameable/WolfEntity;FFFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/living/mob/passive/animal/SheepEntity;getColorRgb(Lnet/minecraft/item/DyeColor;)[F"))
    private float[] cera$collarColor(DyeColor dye, Operation<float[]> original) {
        return Minecraft.getInstance().cera$getCustomColors().getCollarColor(dye, original.call(dye));
    }
}
