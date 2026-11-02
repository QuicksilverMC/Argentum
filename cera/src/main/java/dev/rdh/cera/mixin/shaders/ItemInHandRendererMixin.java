package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.block.Block;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow
    private ItemStack itemInHand;

    @Shadow
    protected abstract boolean isTranslucent(Block block);

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;depthMask(Z)V"), require = 2)
    private boolean cera$skipDepthMask(boolean mask) {
        return !Minecraft.getInstance().cera$getShaderEngine().isInitializedImpl();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void cera$heldItemTranslucent(CallbackInfo ci) {
        if (Minecraft.getInstance().cera$getShaderEngine().isInitializedImpl()) {
            Minecraft.getInstance().cera$getShaderEngine().state().setHeldItemTranslucent(itemInHand != null && isTranslucent(Block.byItem(itemInHand.getItem())));
        }
    }
}
