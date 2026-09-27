package dev.rdh.argentum.mixin.features.blockentity.baking;

import dev.rdh.argentum.impl.ext.ItemFrameEntityExtension;

import net.minecraft.entity.decoration.ItemFrameEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemFrameEntity.class)
public abstract class ItemFrameEntityMixin implements ItemFrameEntityExtension {
    @Unique
    private long argentum$bakedFrame;

    @Override
    public long argentum$getBakedFrame() {
        return this.argentum$bakedFrame;
    }

    @Override
    public void argentum$setBakedFrame(long state) {
        this.argentum$bakedFrame = state;
    }
}
