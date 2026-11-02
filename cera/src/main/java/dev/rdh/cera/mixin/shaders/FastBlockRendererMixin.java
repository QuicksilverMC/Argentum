package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ventooth.swansong.shader.ShaderEntityData;
import dev.rdh.argentum.impl.render.terrain.compile.PrimitiveBuiltRenderSectionData;
import dev.rdh.argentum.impl.render.terrain.compile.pipeline.FastBlockRenderer;
import dev.rdh.argentum.impl.world.cloned.ChunkRenderContext;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.util.math.BlockPos;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = FastBlockRenderer.class, remap = false)
abstract class FastBlockRendererMixin {
    @WrapMethod(method = "render")
    private void cera$blockContext(BlockState state,
                                   BlockPos pos,
                                   ChunkRenderContext world,
                                   BlockLayer layer,
                                   ChunkBuildBuffers buffers,
                                   PrimitiveBuiltRenderSectionData renderData,
                                   Operation<Void> original) {
        if (!Minecraft.getInstance().cera$getShaderEngine().pushBlock(state)) {
            original.call(state, pos, world, layer, buffers, renderData);
            return;
        }
        try {
            original.call(state, pos, world, layer, buffers, renderData);
        } finally {
            ShaderEntityData.get().popEntity();
        }
    }
}
