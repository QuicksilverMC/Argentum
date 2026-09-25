package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.rdh.argentum.impl.world.cloned.ChunkRenderContext;
import dev.rdh.cera.modules.DynamicLights;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkRenderContext.class)
public abstract class ChunkRenderContextMixin {
    @Unique
    private DynamicLights cera$dynamicLights;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cera$setDynamicLights(CallbackInfo ci, @Local(argsOnly = true) World world) {
        this.cera$dynamicLights = ((ClientWorld)world).cera$getDynamicLights();
    }

    @Shadow
    public abstract BlockState getBlockState(int x, int y, int z);

    @ModifyReturnValue(method = "getLightColor(IIII)I", at = @At("RETURN"))
    private int cera$applyDynamicLight(int packedLight, int x, int y, int z, int ambientLight) {
		if(!this.cera$dynamicLights.active() || this.getBlockState(x, y, z).getBlock().isOpaque()) {
            return packedLight;
        } else {
            return this.cera$dynamicLights.combine(x, y, z, packedLight);
        }
	}
}
