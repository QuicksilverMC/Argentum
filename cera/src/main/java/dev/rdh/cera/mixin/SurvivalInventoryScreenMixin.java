package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.menu.SurvivalInventoryScreen;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SurvivalInventoryScreen.class)
public class SurvivalInventoryScreenMixin {
    @WrapMethod(method = "renderEntity")
    private static void cera$markInGui(int x, int y, int size, float mouseX, float mouseY, LivingEntity entity, Operation<Void> original) {
        var animations = Minecraft.getInstance().cera$getCustomEntityModels().animations();
        boolean previous = animations.setInGui(true);
        try {
            original.call(x, y, size, mouseX, mouseY, entity);
        } finally {
            animations.setInGui(previous);
        }
    }
}
