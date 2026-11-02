package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
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

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin implements CeraBlockEntityRenderDispatcherExtension {
    @Unique
    private final BlockEntityContext cera$blockEntityContext = new BlockEntityContext();

    @Override
    public BlockEntityContext cera$getBlockEntityContext() {
        return this.cera$blockEntityContext;
    }

    @WrapMethod(method = "render(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V")
    private void cera$blockEntityRender(BlockEntity blockEntity, double x, double y, double z, float tickDelta,
                                       int breakStage, Operation<Void> original) {
        var models = Minecraft.getInstance().cera$getCustomEntityModels();
        this.cera$blockEntityContext.begin(blockEntity);
        try {
            models.beginBlockEntity(blockEntity, tickDelta);
            original.call(blockEntity, x, y, z, tickDelta, breakStage);
        } finally {
            this.cera$blockEntityContext.end(blockEntity);
            models.endRender();
        }
    }

    @WrapOperation(method = "render(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;render(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V"))
    private void cera$emissivePass(BlockEntityRenderer<?> renderer, BlockEntity blockEntity, double x, double y, double z,
            float tickDelta, int breakStage, Operation<Void> original) {
        Minecraft.getInstance().getTextureManager().cera$getEmissiveTextures().render(EntityCapture.current() != null,
                () -> original.call(renderer, blockEntity, x, y, z, tickDelta, breakStage));
    }
}
