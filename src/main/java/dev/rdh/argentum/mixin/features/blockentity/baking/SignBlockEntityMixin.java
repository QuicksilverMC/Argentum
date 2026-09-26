package dev.rdh.argentum.mixin.features.blockentity.baking;

import dev.rdh.argentum.impl.ext.SignBlockEntityExtension;
import dev.rdh.argentum.impl.render.blockentity.SignText;

import net.minecraft.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SignBlockEntity.class)
public abstract class SignBlockEntityMixin implements SignBlockEntityExtension {
    @Unique
    private volatile SignText argentum$bakedText;

    @Override
    public SignText argentum$getBakedText() {
        return this.argentum$bakedText;
    }

    @Override
    public void argentum$setBakedText(SignText text) {
        this.argentum$bakedText = text;
    }
}
