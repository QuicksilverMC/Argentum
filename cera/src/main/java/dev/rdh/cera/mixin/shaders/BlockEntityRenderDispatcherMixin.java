package dev.rdh.cera.mixin.shaders;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FI)V", at = @At("HEAD"))
    private void cera$blockEntityId(BlockEntity blockEntity, float tickDelta, int destroyProgress, CallbackInfo ci) {
        Minecraft.getInstance().cera$getShaderEngine().nextBlockEntity(blockEntity);
    }
}
