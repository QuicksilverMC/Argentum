package dev.rdh.argentum.mixin.features.text;

import dev.rdh.argentum.impl.render.entity.NameTagBatch;

import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.client.render.block.entity.BeaconRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconRenderer.class)
public class BeaconRendererMixin {
    @Inject(method = "render(Lnet/minecraft/block/entity/BeaconBlockEntity;DDDFI)V", at = @At("HEAD"))
    private void argentum$flushTextBeforeBeam(BeaconBlockEntity beacon, double x, double y, double z, float tickDelta, int breakProgress, CallbackInfo ci) {
        NameTagBatch batch = NameTagBatch.active();
        if (batch != null) batch.flush();
    }
}
