package dev.rdh.argentum.mixin.core.render;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.living.player.PlayerEntity;

import java.nio.FloatBuffer;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    @Final
    private static FloatBuffer glFloatBuffer1;

    @Shadow
    @Final
    private static FloatBuffer glFloatBuffer2;

    @Inject(method = "setup", at = @At("TAIL"))
    private static void argentum$captureTerrainMatrices(PlayerEntity player, boolean thirdPerson, CallbackInfo ci) {
        ArgentumWorldRenderer renderer = ArgentumWorldRenderer.instanceNullable();
        if (renderer != null) {
            renderer.captureMatrices(glFloatBuffer2, glFloatBuffer1);
        }
    }
}
