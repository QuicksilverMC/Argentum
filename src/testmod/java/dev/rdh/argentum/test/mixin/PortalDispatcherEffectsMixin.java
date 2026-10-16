package dev.rdh.argentum.test.mixin;

import dev.rdh.argentum.test.PortalBenchmark;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EndPortalBlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
abstract class PortalDispatcherEffectsMixin {
    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FI)V", at = @At("HEAD"), cancellable = true)
    private void portalBenchmark$dispatch(BlockEntity entity, float tickDelta, int stage, CallbackInfo ci) {
        if (!(entity instanceof EndPortalBlockEntity)) return;
        PortalBenchmark.recordPortalDispatch();
        if (PortalBenchmark.effect("cancel") && entity.getPos().getX() == 0) ci.cancel();
    }

    @ModifyVariable(method = "render(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double portalBenchmark$position(double x) { return PortalBenchmark.effect("dispatcher") ? x + 0.25 : x; }
}
