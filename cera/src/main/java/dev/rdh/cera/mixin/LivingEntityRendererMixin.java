package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.argentum.impl.render.entity.instancing.EntityInstancing;
import dev.rdh.cera.modules.EmissiveTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity> {
    @Shadow
    protected boolean solidRender;

    @WrapOperation(
            method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;renderModel(Lnet/minecraft/entity/living/LivingEntity;FFFFFF)V")
    )
    private void cera$emissivePass(LivingEntityRenderer<T> self, LivingEntity entity, float limbAngle, float limbDistance,
            float tickDelta, float headYaw, float headPitch, float scale, Operation<Void> original) {
        EmissiveTextures emissive = Minecraft.getInstance().getTextureManager().cera$getEmissiveTextures();
        EntityInstancing instancing = EntityInstancing.current();
        boolean instanced = instancing != null && instancing.isBatchActive();
        if (this.solidRender || instanced || !emissive.active()) {
            original.call(self, entity, limbAngle, limbDistance, tickDelta, headYaw, headPitch, scale);
            return;
        }
        int previous = emissive.beginRender();
        try {
            GlStateManager.pushMatrix();
            original.call(self, entity, limbAngle, limbDistance, tickDelta, headYaw, headPitch, scale);
            GlStateManager.popMatrix();
            if (emissive.hasEmissive()) {
                long brightness = emissive.beginRenderEmissive();
                GlStateManager.pushMatrix();
                try {
                    original.call(self, entity, limbAngle, limbDistance, tickDelta, headYaw, headPitch, scale);
                } finally {
                    GlStateManager.popMatrix();
                    emissive.endRenderEmissive(brightness);
                }
            }
        } finally {
            emissive.endRender(previous);
        }
    }
}
