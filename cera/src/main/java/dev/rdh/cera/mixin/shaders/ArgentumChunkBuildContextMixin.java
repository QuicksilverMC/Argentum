package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ventooth.swansong.shader.ShaderEntityData;
import dev.rdh.argentum.impl.render.terrain.compile.ArgentumChunkBuildContext;
import dev.rdh.argentum.impl.world.cloned.ChunkRenderContext;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.util.math.BlockPos;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ArgentumChunkBuildContext.class, remap = false)
abstract class ArgentumChunkBuildContextMixin {
    @Shadow
    public abstract void finishLayer(BlockLayer layer, ChunkBuildBuffers buffers);

    @WrapMethod(method = "renderFluid")
    private void cera$fluidContext(BlockState state,
                                   BlockPos pos,
                                   ChunkRenderContext world,
                                   BlockLayer layer,
                                   Operation<Void> original) {
        if (!Minecraft.getInstance().cera$getShaderEngine().pushBlock(state)) {
            original.call(state, pos, world, layer);
            return;
        }
        try {
            original.call(state, pos, world, layer);
            this.finishLayer(layer, ((ChunkBuildContext)(Object)this).buffers);
        } finally {
            ShaderEntityData.get().popEntity();
        }
    }
}
