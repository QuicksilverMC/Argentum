package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.rdh.argentum.impl.render.gui.hud.item.GuiItemIcons;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GuiItemIcons.class, remap = false)
public abstract class GuiItemIconsMixin {
    @ModifyReturnValue(method = "canBake", at = @At("RETURN"))
    private static boolean cera$drawCustomGlintItemsDirectly(boolean original, ItemStack item) {
        if (!original || !item.hasEnchantmentGlint()) return original;
        var customItems = Minecraft.getInstance().cera$getCustomItems();
        return customItems.useGlint() && customItems.effects(item).isEmpty();
    }
}
