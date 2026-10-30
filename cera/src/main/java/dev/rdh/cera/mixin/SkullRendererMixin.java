package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.entity.SkullRenderer;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SkullRenderer.class)
public class SkullRendererMixin {
    @WrapMethod(method = "render(FFFLnet/minecraft/util/math/Direction;FILcom/mojang/authlib/GameProfile;I)V")
    private void cera$selectSkullModel(float x, float y, float z, Direction direction, float rotation, int type,
            GameProfile profile, int breakStage, Operation<Void> original) {
        var models = Minecraft.getInstance().cera$getCustomEntityModels();
        models.beginSkull(this, type);
        try {
            original.call(x, y, z, direction, rotation, type, profile, breakStage);
        } finally {
            models.endSkull();
        }
    }
}
