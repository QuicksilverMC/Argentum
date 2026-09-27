package dev.rdh.argentum.mixin.features.blockentity.baking;

import dev.rdh.argentum.impl.render.blockentity.BakedItemFrames;

import net.minecraft.client.render.MapRenderer;
import net.minecraft.world.map.SavedMapData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MapRenderer.class)
public abstract class MapRendererMixin {
    @Inject(method = "updateTexture", at = @At("TAIL"))
    private void argentum$refreshBakedMap(SavedMapData data, CallbackInfo ci) {
        BakedItemFrames.onMapUpdated(data);
    }
}
