package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.argentum.impl.ext.MinecraftExtension;
import dev.rdh.cera.modules.shaders.CeraShaderEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.world.WorldRenderer;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Unique
    private CeraShaderEngine cera$engine() {
        return ((Minecraft)(Object)this).cera$getShaderEngine();
    }

    @Inject(method = "runGame", at = @At("HEAD"))
    private void cera$keepFramebuffer(CallbackInfo ci) {
        if (cera$engine().isInitializedImpl()) {
            ((MinecraftExtension) this).argentum$requestFramebuffer();
        }
    }

    @WrapOperation(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(FJ)V"), require = 1)
    private void cera$frame(GameRenderer instance, float tickDelta, long nanoTime, Operation<Void> original) {
        var engine = cera$engine();
        engine.beginRenderAllPre();
        if (engine.isInitializedImpl()) {
            engine.state()
                  .updateSubTick(tickDelta);
            engine.beginRenderAll();
        }
        original.call(instance, tickDelta, nanoTime);
        if (engine.isInitializedImpl()) {
            engine.endRenderAll();
        }
    }

    @Redirect(method = "reloadResources", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;reload()V"), require = 1)
    private void cera$reloadShaderPack(WorldRenderer worldRenderer) {
        cera$engine().scheduleShaderPackReload();
    }

    @Inject(method = "onResolutionChanged", at = @At("RETURN"), require = 1)
    private void cera$resize(CallbackInfo ci) {
        if (cera$engine().isInitializedImpl()) {
            cera$engine().scheduleFramebufferResize();
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/options/KeyBinding;set(IZ)V"))
    private void cera$reloadKey(int key, boolean pressed, Operation<Void> original) {
        original.call(key, pressed);
        if (pressed && key == Keyboard.KEY_R && Keyboard.isKeyDown(Keyboard.KEY_F3) && !Keyboard.isRepeatEvent()) {
            cera$engine().scheduleShaderPackReload();
        }
    }
}
