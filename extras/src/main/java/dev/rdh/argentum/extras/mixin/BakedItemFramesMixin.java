package dev.rdh.argentum.extras.mixin;

import dev.rdh.argentum.extras.ArgentumExtras;
import dev.rdh.argentum.impl.render.blockentity.BakedItemFrames;
import net.minecraft.world.World;
import org.embeddedt.embeddium.impl.util.position.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = BakedItemFrames.class, remap = false)
public class BakedItemFramesMixin {
    @Inject(method = "collect", at = @At("HEAD"), cancellable = true)
    private static void argentumExtras$skipHiddenFrames(World world, SectionPos section, CallbackInfoReturnable<List<BakedItemFrames.Snapshot>> cir) {
        if (!ArgentumExtras.CONFIG.itemFrames) {
            cir.setReturnValue(List.of());
        }
    }
}
