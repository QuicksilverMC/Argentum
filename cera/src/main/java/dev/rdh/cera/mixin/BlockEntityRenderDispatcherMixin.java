package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;
import dev.rdh.cera.entity.BlockEntityContext;
import dev.rdh.cera.ext.CeraBlockEntityRenderDispatcherExtension;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin implements CeraBlockEntityRenderDispatcherExtension {
    @Unique
    private final BlockEntityContext cera$blockEntityContext = new BlockEntityContext();

    @Override
    public BlockEntityContext cera$getBlockEntityContext() {
        return this.cera$blockEntityContext;
    }

    @Inject(
            method = "render(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V",
            at = @At("HEAD")
    )
    private void cera$beginBlockEntityRender(BlockEntity blockEntity, double x, double y, double z, float tickDelta, int breakStage, CallbackInfo ci) {
        this.cera$blockEntityContext.begin(blockEntity);
        Minecraft.getInstance().cera$getCustomEntityModels().beginBlockEntity(blockEntity, tickDelta);
    }

    @Inject(
            method = "render(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V",
            at = @At("RETURN")
    )
    private void cera$endBlockEntityRender(BlockEntity blockEntity, double x, double y, double z, float tickDelta, int breakStage, CallbackInfo ci) {
        this.cera$blockEntityContext.end(blockEntity);
        Minecraft.getInstance().cera$getCustomEntityModels().endRender();
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;render(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V")
    )
    private void cera$emissivePass(BlockEntityRenderer<?> renderer, BlockEntity blockEntity, double x, double y, double z,
            float tickDelta, int breakStage, Operation<Void> original) {
        Minecraft.getInstance().getTextureManager().cera$getEmissiveTextures().render(EntityCapture.current() != null,
                () -> original.call(renderer, blockEntity, x, y, z, tickDelta, breakStage));
    }
}
