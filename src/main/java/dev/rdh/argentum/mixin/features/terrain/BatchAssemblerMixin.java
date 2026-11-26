package dev.rdh.argentum.mixin.features.terrain;

import org.embeddedt.embeddium.impl.gl.device.IndirectMultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.rdh.argentum.impl.Argentum;

@Mixin(value = BatchAssembler.class, remap = false)
public class BatchAssemblerMixin {
    @Inject(method = "createBatch", at = @At("HEAD"), cancellable = true)
    private static void argentum$useIndirectDraws(int capacity, CallbackInfoReturnable<MultiDrawBatch> cir) {
        if (Argentum.indirectChunkDraws()) cir.setReturnValue(new IndirectMultiDrawBatch(capacity));
    }
}
