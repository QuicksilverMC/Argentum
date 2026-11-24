package dev.rdh.argentum.mixin.core.state;

import java.nio.IntBuffer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import dev.rdh.argentum.impl.render.GlStateMirror;
import net.minecraft.client.render.Camera;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetInteger(ILjava/nio/IntBuffer;)V"))
    private static void argentum$mirroredViewport(int parameter, IntBuffer out, Operation<Void> original) {
        if (parameter != GL11.GL_VIEWPORT || !GlStateMirror.readViewport(out)) original.call(parameter, out);
    }
}
