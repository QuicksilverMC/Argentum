package dev.rdh.argentum.impl.render.entity.instancing;


import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import net.minecraft.client.render.entity.layer.CapeLayer;
import net.minecraft.client.render.entity.layer.CreeperChargeLayer;
import net.minecraft.client.render.entity.layer.Deadmou5Layer;
import net.minecraft.client.render.entity.layer.ItemInHandLayer;
import net.minecraft.client.render.entity.layer.EnderDragonEyesLayer;
import net.minecraft.client.render.entity.layer.EndermanEyesLayer;
import net.minecraft.client.render.entity.layer.EndermanCarriedBlockLayer;
import net.minecraft.client.render.entity.layer.IronGolemFlowerInHandLayer;
import net.minecraft.client.render.entity.layer.SpiderEyesLayer;
import net.minecraft.client.render.entity.layer.PigSaddleLayer;
import net.minecraft.client.render.entity.layer.MushroomLayer;
import net.minecraft.client.render.entity.layer.SheepFurLayer;
import net.minecraft.client.render.entity.layer.SlimeOuterLayer;
import net.minecraft.client.render.entity.layer.SnowGolemHeadLayer;
import net.minecraft.client.render.entity.layer.StuckArrowLayer;
import net.minecraft.client.render.entity.layer.WitchItemInHandLayer;
import net.minecraft.client.render.entity.layer.WornSkullLayer;
import net.minecraft.client.render.entity.layer.WolfCollarLayer;
import net.minecraft.client.render.entity.layer.WitherChargeLayer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ItemStack;

final class EntityLayerSupport {
    private EntityLayerSupport() {
    }

    static InstanceRenderPass pass(Object layer, LivingEntity entity) {
        if (layer instanceof EnderDragonEyesLayer
                || layer instanceof EndermanEyesLayer
                || layer instanceof SpiderEyesLayer) {
            return InstanceRenderPass.EMISSIVE;
        }
        if (layer instanceof CreeperChargeLayer) {
            return InstanceRenderPass.CREEPER_CHARGE;
        }
        if (layer instanceof WitherChargeLayer) {
            return InstanceRenderPass.WITHER_CHARGE;
        }
        if (layer instanceof SlimeOuterLayer) {
            return InstanceRenderPass.TRANSLUCENT;
        }
        if (layer instanceof MushroomLayer) {
            return InstanceRenderPass.CULL_FRONT;
        }
        if (layer instanceof StuckArrowLayer) {
            return InstanceRenderPass.NO_CULL;
        }
        if (layer instanceof AbstractArmorLayer) {
            return InstanceRenderPass.NORMAL;
        }
        return layer instanceof CapeLayer
                || layer instanceof Deadmou5Layer
                || layer instanceof EndermanCarriedBlockLayer
                || layer instanceof IronGolemFlowerInHandLayer
                || layer instanceof PigSaddleLayer
                || layer instanceof SheepFurLayer
                || layer instanceof SnowGolemHeadLayer
                || layer instanceof WolfCollarLayer
                || layer instanceof WornSkullLayer
                || layer instanceof ItemInHandLayer && supportsHeldItem(entity)
                || layer instanceof WitchItemInHandLayer && supportsHeldItem(entity)
                ? InstanceRenderPass.NORMAL : null;
    }

    private static boolean supportsHeldItem(LivingEntity entity) {
        ItemStack item = entity.getDisplayItemInHand();
        return item != null;
    }
}
