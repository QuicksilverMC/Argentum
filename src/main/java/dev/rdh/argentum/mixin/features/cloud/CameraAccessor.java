package dev.rdh.argentum.mixin.features.cloud;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.FloatBuffer;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("glFloatBuffer1")
    static FloatBuffer argentum$getModelView() {
        throw new AssertionError();
    }

}
