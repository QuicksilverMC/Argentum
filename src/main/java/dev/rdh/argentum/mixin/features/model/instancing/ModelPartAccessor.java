package dev.rdh.argentum.mixin.features.model.instancing;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("model")
    Model argentum$getModel();
}
