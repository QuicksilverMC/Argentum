package dev.rdh.cera.mixin;

import dev.rdh.cera.modules.cit.CustomItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractArmorLayer.class)
public class AbstractArmorLayerMixin<T extends Model> {
    @Shadow
    private void renderEnchantmentGlint(LivingEntity entity, Model model, float walkAnimationProgress, float walkAnimationSpeed,
                                        float tickDelta, float bob, float yaw, float pitch, float scale) {
    }

    @ModifyArg(method = "renderArmor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;bindTexture(Lnet/minecraft/resource/Identifier;)V"))
    private Identifier cera$resolveArmorTexture(Identifier original, @Local(argsOnly = true) LivingEntity entity, @Local(argsOnly = true) int equipmentSlot) {
        ItemStack stack = entity.getArmor(equipmentSlot - 1);
        if (!(stack.getItem() instanceof ArmorItem armor)) {
            return original;
        }
        return Minecraft.getInstance().cera$getCustomItems().resolveArmor(stack, armor.getTier().getKey(),
                equipmentSlot == 2 ? 2 : 1, original.getPath().endsWith("_overlay.png"), original);
    }

    @WrapOperation(method = "renderArmor(Lnet/minecraft/entity/living/LivingEntity;FFFFFFFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/layer/AbstractArmorLayer;renderEnchantmentGlint(Lnet/minecraft/entity/living/LivingEntity;Lnet/minecraft/client/render/model/Model;FFFFFFF)V"))
    private void cera$renderCustomGlint(AbstractArmorLayer<T> instance, LivingEntity entity, T model, float walkAnimationProgress,
                                        float walkAnimationSpeed, float tickDelta, float bob, float yaw, float pitch, float scale,
                                        Operation<Void> original, @Local(argsOnly = true) int equipmentSlot, @Local(argsOnly = true) LivingEntity glintEntity) {
        ItemStack stack = entity.getArmor(equipmentSlot - 1);
        var effects = Minecraft.getInstance().cera$getCustomItems().effects(stack);
        if (effects.isEmpty()) {
            this.renderEnchantmentGlint(glintEntity, model, walkAnimationProgress, walkAnimationSpeed, tickDelta, bob, yaw, pitch, scale);
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.depthFunc(514);
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.matrixMode(5890);
        for (CustomItems.Effect effect : effects) {
            Minecraft.getInstance().getTextureManager().bind(effect.texture());
            effect.blend().apply(1.0F);
            float textureScale = Minecraft.getInstance().cera$getCustomItems().glintWidth(effect.texture()) / 8.0F;
            GlStateManager.pushMatrix();
            GlStateManager.rotatef(effect.rotation(), 0.0F, 0.0F, 1.0F);
            GlStateManager.scalef(textureScale, textureScale / 2.0F, textureScale);
            GlStateManager.translatef(0.0F, effect.speed() * (Minecraft.getTime() % 3000L) / 24000.0F, 0.0F);
            GlStateManager.matrixMode(5888);
            model.render(entity, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale);
            GlStateManager.matrixMode(5890);
            GlStateManager.popMatrix();
        }
        GlStateManager.matrixMode(5888);
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(515);
        GlStateManager.disableBlend();
    }
}
