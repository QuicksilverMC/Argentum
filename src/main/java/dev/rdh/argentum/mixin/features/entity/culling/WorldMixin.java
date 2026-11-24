package dev.rdh.argentum.mixin.features.entity.culling;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.entity.living.mob.monster.boss.Boss;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

@Mixin(World.class)
public abstract class WorldMixin {
    @Unique
    private static final double ARGENTUM$ALWAYS_TICK_DISTANCE_SQ = 6.0 * 6.0;

    @Shadow
    @Final
    public boolean isClient;

    @WrapOperation(method = "tickEntity(Lnet/minecraft/entity/Entity;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V"))
    private void argentum$skipCulledTick(Entity entity, Operation<Void> original) {
        if (this.isClient && Argentum.CONFIG.tickCulling && entity instanceof LivingEntity living && argentum$canSkip(living)) {
            argentum$culledTick(living);
        } else {
            original.call(entity);
        }
    }

    @Unique
    private boolean argentum$canSkip(LivingEntity entity) {
        if (entity.rider != null || entity.vehicle != null || entity instanceof Boss || entity instanceof PlayerEntity || entity instanceof CreeperEntity
                || entity.getHealth() <= 0.0F) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCamera();
        if (entity == minecraft.player || camera == null || entity == camera
                || entity.squaredDistanceTo(camera) < ARGENTUM$ALWAYS_TICK_DISTANCE_SQ) {
            return false;
        }
        ArgentumWorldRenderer renderer = ArgentumWorldRenderer.instanceNullable();
        return renderer != null && renderer.isRenderingWorld((World)(Object)this) && renderer.isEntityHidden(entity);
    }

    @Unique
    private static void argentum$culledTick(LivingEntity entity) {
        entity.lastWalkDistance = entity.walkDistance;
        entity.lastX = entity.x;
        entity.lastY = entity.y;
        entity.lastZ = entity.z;
        entity.lastAttackAnimationProgress = entity.attackAnimationProgress;
        entity.lastTilt = entity.tilt;
        entity.lastWalkAnimationSpeed = entity.walkAnimationSpeed;
        entity.lastTotalWalkDistance = entity.totalWalkDistance;
        entity.lastBodyYaw = entity.bodyYaw;
        entity.lastHeadYaw = entity.headYaw;
        if (entity.damagedTimer > 0) {
            entity.damagedTimer--;
        }
        if (entity.lerpSteps > 0) {
            double x = entity.x + (entity.lerpX - entity.x) / entity.lerpSteps;
            double y = entity.y + (entity.lerpY - entity.y) / entity.lerpSteps;
            double z = entity.z + (entity.lerpZ - entity.z) / entity.lerpSteps;
            entity.yaw = (float)(entity.yaw + MathHelper.wrapDegrees(entity.lerpYaw - entity.yaw) / entity.lerpSteps) % 360.0F;
            entity.pitch = (float)(entity.pitch + (entity.lerpPitch - entity.pitch) / entity.lerpSteps) % 360.0F;
            entity.lerpSteps--;
            entity.setPosition(x, y, z);
        }
    }
}
