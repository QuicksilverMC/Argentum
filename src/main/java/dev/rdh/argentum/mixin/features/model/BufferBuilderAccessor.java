package dev.rdh.argentum.mixin.features.model;

import net.minecraft.client.render.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.IntBuffer;

@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
    @Accessor("building")
    boolean celeritas$isBuilding();

    @Accessor("intBuffer")
    IntBuffer argentum$getIntBuffer();
}
