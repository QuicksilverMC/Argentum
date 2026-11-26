package dev.rdh.argentum.mixin.core.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

import net.minecraft.client.render.Frustum;

import java.nio.FloatBuffer;

@Mixin(Frustum.class)
public abstract class FrustumMixin {
    @WrapOperation(method = "compute", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;getFloat(ILjava/nio/FloatBuffer;)V"))
    private void argentum$readCapturedMatrix(int name, FloatBuffer buffer, Operation<Void> original, @Share("captured") LocalRef<ChunkRenderMatrices> captured) {
        if (name == GL11.GL_PROJECTION_MATRIX) {
            ArgentumWorldRenderer renderer = ArgentumWorldRenderer.instanceNullable();
            captured.set(renderer == null ? null : renderer.takeFreshMatrices());
        }
        ChunkRenderMatrices matrices = captured.get();
        if (matrices == null) {
            original.call(name, buffer);
            return;
        }
        (name == GL11.GL_PROJECTION_MATRIX ? matrices.projection() : matrices.modelView()).get(buffer);
    }
}
