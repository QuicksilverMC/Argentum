package dev.rdh.argentum.mixin.features.terrain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.rdh.argentum.impl.render.terrain.compile.pipeline.FastBlockRenderer;

@Mixin(BlockModelRenderer.class)
public abstract class BlockModelRendererMixin {
    @WrapOperation(
            method = "tesselateFaceWithAmbientOcclusion",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;hashCode(Lnet/minecraft/util/math/Vec3i;)J")
    )
    private long argentum$doublePlantOffset(Vec3i vec, Operation<Long> original, @Local(argsOnly = true) WorldView world, @Local(argsOnly = true) BlockPos pos) {
        return original.call(FastBlockRenderer.offsetPos(world.getBlockState(pos), pos));
    }
}
