package dev.rdh.argentum.mixin.features.hud;

import dev.rdh.argentum.impl.render.gui.hud.item.GuiItemIcons;

import net.minecraft.client.gui.screen.inventory.menu.InventoryMenuScreen;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GlStateManager.class, Lighting.class, InventoryMenuScreen.class})
public class GuiItemFlushMixin {
    @Inject(method = {"disableDepthTest", "turnOff", "pushMatrix", "popMatrix"}, at = @At("HEAD"), require = 0)
    private static void argentum$flushBeforeOverlay(CallbackInfo ci) {
        GuiItemIcons.flush();
    }

    @Inject(method = "drawItem", at = @At("RETURN"), require = 0)
    private static void argentum$flushDraggedItem(CallbackInfo ci) {
        GuiItemIcons.flush();
    }
}
