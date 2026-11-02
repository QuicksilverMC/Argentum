package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.cera.modules.shaders.ShaderRender;
import dev.rdh.cera.modules.shaders.ShaderSkyBox;
import com.ventooth.swansong.shader.MCRenderStage;
import com.ventooth.swansong.shader.StateGraph.Node;
import com.ventooth.swansong.shader.StateGraph.Stack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    private static boolean cera$active() {
        return Minecraft.getInstance().cera$getShaderEngine().isInitializedImpl();
    }

    @Unique
    private static void cera$moveTo(Node render, Node shadow) {
        if (cera$active()) {
            var graph = Minecraft.getInstance().cera$getShaderEngine().graph();
            graph.moveTo(graph.isShadowPass() ? shadow : render);
        }
    }

    @Unique
    private static void cera$renderStage(MCRenderStage stage) {
        if (cera$active()) {
            Minecraft.getInstance().cera$getShaderEngine().state().updateRenderStage(stage);
        }
    }

    @WrapOperation(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;enableTexture()V"))
    private void cera$skyTextured(Operation<Void> original) {
        if (cera$active() && Minecraft.getInstance().cera$getShaderEngine().graph().isSky()) {
            Minecraft.getInstance().cera$getShaderEngine().graph().moveTo(Node.RenderSkyTextured);
        }
        original.call();
    }

    @WrapOperation(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;disableTexture()V"))
    private void cera$skyBasic(Operation<Void> original) {
        if (cera$active() && Minecraft.getInstance().cera$getShaderEngine().graph().isSky()) {
            Minecraft.getInstance().cera$getShaderEngine().graph().moveTo(Node.RenderSkyBasic);
        }
        original.call();
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/world/WorldRenderer;useVbo:Z", ordinal = 0), require = 1)
    private void cera$preSkyList(CallbackInfo ci) {
        if (cera$active()) {
            ShaderSkyBox.preSkyList();
        }
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;rotatef(FFFF)V", ordinal = 4), require = 1)
    private void cera$preCelestialRotate(CallbackInfo ci) {
        if (cera$active()) {
            Minecraft.getInstance().cera$getShaderEngine().state().preCelestialRotate();
        }
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;rotatef(FFFF)V", ordinal = 4, shift = At.Shift.AFTER), require = 1)
    private void cera$postCelestialRotate(CallbackInfo ci) {
        if (cera$active()) {
            Minecraft.getInstance().cera$getShaderEngine().state().postCelestialRotate();
        }
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureManager;bind(Lnet/minecraft/resource/Identifier;)V", ordinal = 0), require = 1)
    private void cera$sun(CallbackInfo ci) {
        cera$renderStage(MCRenderStage.SUN);
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureManager;bind(Lnet/minecraft/resource/Identifier;)V", ordinal = 1), require = 1)
    private void cera$moon(CallbackInfo ci) {
        cera$renderStage(MCRenderStage.MOON);
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;color4f(FFFF)V", ordinal = 1), require = 1)
    private void cera$stars(CallbackInfo ci) {
        cera$renderStage(MCRenderStage.STARS);
    }

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=global"), require = 1)
    private void cera$weatherEntities(CallbackInfo ci) {
        cera$moveTo(Node.RenderWeatherEntities0, Node.ShadowEntities0);
    }

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=entities"), require = 1)
    private void cera$entities(CallbackInfo ci) {
        cera$moveTo(Node.RenderEntities0, Node.ShadowEntities0);
    }

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=blockentities"), require = 1)
    private void cera$blockEntities(CallbackInfo ci) {
        cera$moveTo(Node.RenderBlockEntities0, Node.ShadowBlockEntities0);
    }

    @WrapMethod(method = "renderOutlineShape(Lnet/minecraft/util/math/Box;)V")
    private static void cera$outline(Box box, Operation<Void> original) {
        boolean pushed = ShaderRender.isManaged();
        if (pushed && ShaderRender.isShadowPass()) return;
        if (pushed) ShaderRender.push(Stack.AABBOutline);
        try {
            original.call(box);
        } finally {
            if (pushed) ShaderRender.pop(Stack.AABBOutline);
        }
    }

    @WrapMethod(method = "renderOutlineShape(Lnet/minecraft/util/math/Box;IIII)V")
    private static void cera$outline(Box box, int red, int green, int blue, int alpha, Operation<Void> original) {
        boolean pushed = ShaderRender.isManaged();
        if (pushed && ShaderRender.isShadowPass()) return;
        if (pushed) ShaderRender.push(Stack.AABBOutline);
        try {
            original.call(box, red, green, blue, alpha);
        } finally {
            if (pushed) ShaderRender.pop(Stack.AABBOutline);
        }
    }

    @Inject(method = "renderBlockOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderOutlineShape(Lnet/minecraft/util/math/Box;)V"))
    private void cera$blockOutlineState(CallbackInfo ci) {
        if (cera$active()) {
            ShaderRender.bindWhiteTexture();
            ShaderRender.fullBrightLightmap();
        }
    }
}
