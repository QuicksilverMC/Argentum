package dev.rdh.argentum.test.mixin;

import dev.rdh.argentum.test.LobbyBenchmark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.ArmorStandRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.ZombieRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
abstract class LobbyFastHologramMixin {
    @Shadow
    public abstract void renderNameTag(LivingEntity entity, double dx, double dy, double dz);

    @Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void lobbyBenchmark$renderOnlyName(LivingEntity entity, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
        if (!LobbyBenchmark.variant("fastHolograms") || !this.lobbyBenchmark$rendersNothing(entity)) return;
        GlStateManager.enableAlphaTest();
        GlStateManager.depthMask(true);
        GlStateManager.disableRescaleNormal();
        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        GlStateManager.enableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        GlStateManager.enableCull();
        this.renderNameTag(entity, x, y, z);
        ci.cancel();
    }

    private boolean lobbyBenchmark$rendersNothing(LivingEntity entity) {
        Class<?> renderer = this.getClass();
        boolean stock = entity.getClass() == ArmorStandEntity.class && renderer == ArmorStandRenderer.class
                || entity.getClass() == ZombieEntity.class && renderer == ZombieRenderer.class;
        if (!stock || !entity.isInvisibleTo(Minecraft.getInstance().player) || entity.shouldRenderOnFire() || entity.damagedTimer != 0 || entity.deathTicks != 0) return false;
        for (ItemStack stack : entity.getEquipment()) {
            if (stack != null) return false;
        }
        return true;
    }
}
