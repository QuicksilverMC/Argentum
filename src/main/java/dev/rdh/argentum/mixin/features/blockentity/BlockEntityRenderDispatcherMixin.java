package dev.rdh.argentum.mixin.features.blockentity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.rdh.argentum.impl.ext.BlockEntityRenderState;
import dev.rdh.argentum.impl.ext.WorldExtension;
import dev.rdh.argentum.impl.render.entity.instancing.EntityCapture;
import dev.rdh.argentum.impl.render.entity.instancing.EntityInstancing;
import dev.rdh.argentum.impl.render.entity.instancing.InstanceRenderPass;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    @Shadow
    public World world;

    @Unique
    private int argentum$pendingLight = -1;

    @WrapMethod(method = "render(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V")
    private void argentum$instanceBlockEntity(BlockEntity blockEntity, double x, double y, double z, float tickDelta, int blockMiningProgress, Operation<Void> original) {
        int light = this.argentum$pendingLight;
        this.argentum$pendingLight = -1;
        EntityInstancing instancing = EntityInstancing.current();
        if (instancing == null || blockMiningProgress >= 0 || light == -1) {
            original.call(blockEntity, x, y, z, tickDelta, blockMiningProgress);
            return;
        }
        BlockEntityRenderState state = (BlockEntityRenderState)blockEntity;
        if (state.argentum$getPass() == null) {
            state.argentum$setPass(instancing.passFor(((BlockEntityRenderDispatcher)(Object)this).getRenderer(blockEntity)));
        }
        InstanceRenderPass pass = state.argentum$getPass();
        if (pass == null) {
            original.call(blockEntity, x, y, z, tickDelta, blockMiningProgress);
            return;
        }
        try (EntityCapture _ = instancing.beginBlockEntity(pass, light)) {
            original.call(blockEntity, x, y, z, tickDelta, blockMiningProgress);
        }
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/block/entity/BlockEntity;FI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getLightColor(Lnet/minecraft/util/math/BlockPos;I)I")
    )
    private int argentum$cacheLight(World world, BlockPos pos, int minLight, Operation<Integer> original,
            @Local(argsOnly = true) BlockEntity blockEntity) {
        int generation = ((WorldExtension)world).argentum$getBlockEntityLight().generation(pos);
        BlockEntityRenderState state = (BlockEntityRenderState)blockEntity;
        int light = state.argentum$getCachedLight(generation);
        if (light == -1) {
            light = original.call(world, pos, minLight);
            state.argentum$cacheLight(light, generation);
        }
        this.argentum$pendingLight = light;
        return light;
    }
}
