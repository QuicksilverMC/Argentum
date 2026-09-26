package dev.rdh.argentum.mixin.features.blockentity.baking;

import dev.rdh.argentum.impl.ext.SkullBlockEntityExtension;
import dev.rdh.argentum.impl.render.blockentity.BakedBlockEntities;
import dev.rdh.argentum.impl.render.blockentity.SlotSheet;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkullBlockEntity.class)
public abstract class SkullBlockEntityMixin implements SkullBlockEntityExtension {
    @Unique
    private SlotSheet.Entry argentum$bakedHead;

    @Override
    public SlotSheet.Entry argentum$getBakedHead() {
        return this.argentum$bakedHead;
    }

    @Override
    public void argentum$setBakedHead(SlotSheet.Entry region) {
        this.argentum$bakedHead = region;
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void argentum$rebuildWithNewType(NbtCompound nbt, CallbackInfo ci) {
        BlockEntity self = (BlockEntity)(Object)this;
        BakedBlockEntities.rebuild(self.getWorld(), self.getPos());
    }
}
