package dev.rdh.argentum.mixin.features.nametag;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.entity.NameTagBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.ArmorStandRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow
    protected boolean solidRender;

    @Shadow
    public abstract void renderNameTag(LivingEntity entity, double dx, double dy, double dz);

    @WrapMethod(method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V")
    private void argentum$batchNameTag(LivingEntity entity, double x, double y, double z, Operation<Void> original) {
        NameTagBatch batch = NameTagBatch.active();
        if (batch == null) {
            original.call(entity, x, y, z);
            return;
        }
        batch.enter();
        try {
            original.call(entity, x, y, z);
        } finally {
            batch.exit();
        }
    }

    @Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void argentum$renderHologram(LivingEntity entity, double x, double y, double z, float yaw, float tickDelta,
            CallbackInfo ci) {
        if (!Argentum.CONFIG.hologramFastPath || this.solidRender || !(entity instanceof ArmorStandEntity)
                || ((Object)this).getClass() != ArmorStandRenderer.class
                || !entity.isInvisibleTo(Minecraft.getInstance().player) || !argentum$isEmpty(entity.getEquipment())) {
            return;
        }
        ci.cancel();
        GlStateManager.enableAlphaTest();
        GlStateManager.depthMask(true);
        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        GlStateManager.enableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        GlStateManager.enableCull();
        this.renderNameTag(entity, x, y, z);
    }

    @Unique
    private static boolean argentum$isEmpty(ItemStack[] equipment) {
        for (ItemStack stack : equipment) {
            if (stack != null) {
                return false;
            }
        }
        return true;
    }
}
