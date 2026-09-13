package dev.rdh.argentum.mixin.features.hud;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.hud.HudBatch;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.inventory.menu.CreativeInventoryScreen;
import net.minecraft.resource.Identifier;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin extends GuiElement {
    @Shadow
    @Final
    private static Identifier MENU_LOCATION;

    @Unique
    private HudBatch.Textured argentum$tabBatch;

    @Unique
    private boolean argentum$batchingTabs;

    @WrapWithCondition(
            method = "renderTabIcon",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/inventory/menu/CreativeInventoryScreen;drawTexture(IIIIII)V")
    )
    private boolean argentum$batchTabIcon(CreativeInventoryScreen screen, int x, int y, int u, int v, int width, int height) {
        if (!this.argentum$batchingTabs) {
            return true;
        } else {
            if (this.argentum$tabBatch == null) {
                this.argentum$tabBatch = HudBatch.textured(4 * 1024);
            }
            this.argentum$tabBatch.quad(x, y, u, v, width, height, width, height, 256, 256, this.drawOffset);
            return false;
        }
    }

    @Inject(method = "renderMenuBackground", at = @At("HEAD"))
    private void argentum$openTabBatch(float tickDelta, int mouseX, int mouseY, CallbackInfo ci) {
        this.argentum$batchingTabs = !Argentum.CONFIG.guiItemAtlas;
    }

    @Inject(method = "renderMenuBackground", at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/render/texture/TextureManager;bind(Lnet/minecraft/resource/Identifier;)V"))
    private void argentum$drawTabBatch(float tickDelta, int mouseX, int mouseY, CallbackInfo ci) {
        this.argentum$batchingTabs = false;
        if (this.argentum$tabBatch != null) {
            Minecraft.getInstance().getTextureManager().bind(MENU_LOCATION);
            this.argentum$tabBatch.draw();
        }
    }
}
