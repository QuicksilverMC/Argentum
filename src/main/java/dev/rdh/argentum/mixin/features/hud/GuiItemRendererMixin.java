package dev.rdh.argentum.mixin.features.hud;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.rdh.argentum.impl.render.hud.item.GuiItemIcons;

import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.item.ItemModelShaper;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.client.resource.manager.ResourceManager;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class GuiItemRendererMixin {
    @Shadow
    public float zOffset;

    @Shadow
    @Final
    private ItemModelShaper modelShaper;

    @Inject(
            method = "renderGuiItemModel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;blendFunc(II)V",
                    shift = At.Shift.AFTER)
    )
    private void argentum$keepAlphaWhileBaking(ItemStack item, int x, int y, CallbackInfo ci) {
        // blendFunc sets the alpha factors too, which would leave a*a in the atlas
        if (GuiItemIcons.baking()) {
            GlStateManager.blendFuncSeparate(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), 1, 771);
        }
    }

    @WrapMethod(method = "renderGuiItemModel")
    private void argentum$bakeGuiItem(ItemStack item, int x, int y, Operation<Void> original) {
        if (!GuiItemIcons.enabled()) {
            original.call(item, x, y);
            return;
        }
        if (!GuiItemIcons.canBake(item)) {
            GuiItemIcons.flush();
            original.call(item, x, y);
            return;
        }

        BakedModel model = this.modelShaper.getModel(item);
        if (model.isCustomRenderer() || (item.hasEnchantmentGlint()
                && (model.isGui3d() || model.getParticleIcon() == null))) {
            GuiItemIcons.flush();
            original.call(item, x, y);
            return;
        }
        int slot = GuiItemIcons.acquire(model, item, () -> original.call(item, 0, 0));

        if (slot < 0) {
            original.call(item, x, y);
            return;
        }

        GuiItemIcons.draw(slot, x, y, this.zOffset);
        if (item.hasEnchantmentGlint()) GuiItemIcons.drawGlint(slot, x, y, this.zOffset, model);
    }

    @WrapMethod(method = "renderEnchantmentGlint")
    private void argentum$skipGlintWhileBaking(BakedModel model, Operation<Void> original) {
        if (!GuiItemIcons.baking()) original.call(model);
    }

    @Inject(method = "renderGuiItemDecorations(Lnet/minecraft/client/render/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At("HEAD"))
    private void argentum$flushBeforeDecorations(TextRenderer textRenderer, ItemStack item, int x, int y, String stackSizeText, CallbackInfo ci) {
        if (item != null && (item.size != 1 || stackSizeText != null || item.isDamaged())) {
            GuiItemIcons.flush();
        }
    }

    @Inject(method = "reload", at = @At("RETURN"))
    private void argentum$invalidateOnReload(ResourceManager resourceManager, CallbackInfo ci) {
        GuiItemIcons.invalidate();
    }
}
