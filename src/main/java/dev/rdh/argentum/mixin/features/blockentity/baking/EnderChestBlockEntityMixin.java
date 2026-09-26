package dev.rdh.argentum.mixin.features.blockentity.baking;

import dev.rdh.argentum.impl.render.blockentity.BakedBlockEntities;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderChestBlockEntity.class)
public abstract class EnderChestBlockEntityMixin {
    @Shadow
    public float animationProgress;

    @Shadow
    public float lastAnimationProgress;

    @Shadow
    public int viewerCount;

    @Unique
    private boolean argentum$open;

    @Inject(method = "tick", at = @At("TAIL"))
    private void argentum$rebuildWhenOpened(CallbackInfo ci) {
        boolean open = BakedBlockEntities.isOpen(this.animationProgress, this.lastAnimationProgress, this.viewerCount);
        if (open != this.argentum$open) {
            this.argentum$open = open;
            BlockEntity self = (BlockEntity)(Object)this;
            BakedBlockEntities.rebuild(self.getWorld(), self.getPos());
        }
    }
}
