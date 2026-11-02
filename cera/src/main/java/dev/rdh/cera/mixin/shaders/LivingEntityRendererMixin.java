package dev.rdh.cera.mixin.shaders;

import dev.rdh.cera.modules.shaders.ShaderRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow
    protected abstract int getOverlayColor(LivingEntity entity, float brightness, float tickDelta);

    @Inject(method = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z", at = @At("HEAD"), cancellable = true)
    private void cera$entityColor(LivingEntity entity, float tickDelta, boolean alwaysRender, CallbackInfoReturnable<Boolean> cir) {
        if (!ShaderRender.isManaged()) {
            return;
        }
        var color = getOverlayColor(entity, entity.getBrightness(tickDelta), tickDelta);
        var tinted = (color >> 24 & 0xFF) > 0;
        var hurt = entity.damagedTimer > 0 || entity.deathTicks > 0;
        var state = Minecraft.getInstance().cera$getShaderEngine().state();
        if (hurt && (tinted || alwaysRender)) {
            state.updateEntityColor(1F, 0F, 0F, 0.3F);
        } else if (tinted) {
            state.updateEntityColor((color >> 16 & 0xFF) / 255F,
                                    (color >> 8 & 0xFF) / 255F,
                                    (color & 0xFF) / 255F,
                                    1F - (color >> 24 & 0xFF) / 255F);
        } else {
            state.resetEntityColor();
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "tearDownOverlayColor", at = @At("HEAD"), cancellable = true)
    private void cera$resetEntityColor(CallbackInfo ci) {
        if (ShaderRender.isManaged()) {
            ShaderRender.resetEntityColor();
            ci.cancel();
        }
    }
}
