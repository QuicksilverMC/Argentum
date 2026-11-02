package dev.rdh.cera.mixin.shaders;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.ventooth.swansong.shader.StateGraph.Node;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.FloatBuffer;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique
    private static boolean cera$active() {
        return Minecraft.getInstance().cera$getShaderEngine().isInitializedImpl();
    }

    @Unique
    private static void cera$moveTo(Node node) {
        if (cera$active()) {
            Minecraft.getInstance().cera$getShaderEngine().graph().moveTo(node);
        }
    }

    @Inject(method = "renderWorld(FJ)V", at = @At("HEAD"), require = 1)
    private void cera$beginRenderWorld(CallbackInfo ci) {
        if (cera$active()) {
            Minecraft.getInstance().cera$getShaderEngine().beginRenderWorld();
        }
    }

    @Inject(method = "renderWorld(FJ)V", at = @At("RETURN"), require = 1)
    private void cera$unmanaged(CallbackInfo ci) {
        cera$moveTo(Node.Unmanaged);
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=clear"), require = 1)
    private void cera$renderBegin(CallbackInfo ci) {
        cera$moveTo(Node.RenderBegin);
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;setupCamera(FI)V", shift = At.Shift.AFTER), require = 1)
    private void cera$updateCamera(CallbackInfo ci) {
        if (cera$active()) {
            Minecraft.getInstance().cera$getShaderEngine().state().updateCamera(true);
        }
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderSky(FI)V"), require = 1)
    private void cera$renderSky(CallbackInfo ci) {
        cera$moveTo(Node.RenderSkyBasic);
    }

    @Inject(method = "renderClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderClouds(FI)V"), require = 1)
    private void cera$renderClouds(CallbackInfo ci) {
        cera$moveTo(Node.RenderClouds);
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I", ordinal = 0), require = 1)
    private void cera$renderChunk0(CallbackInfo ci) {
        cera$moveTo(Node.RenderChunk0);
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;crosshairTarget:Lnet/minecraft/world/HitResult;", ordinal = 0, opcode = Opcodes.GETFIELD), require = 1)
    private void cera$renderSelectionBox(CallbackInfo ci) {
        cera$moveTo(Node.RenderSelectionBox);
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderMiningProgress(Lnet/minecraft/client/render/vertex/Tesselator;Lnet/minecraft/client/render/vertex/BufferBuilder;Lnet/minecraft/entity/Entity;F)V"), require = 1)
    private void cera$renderBlockDamage(CallbackInfo ci) {
        cera$moveTo(Node.RenderBlockDamage);
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;renderLit(Lnet/minecraft/entity/Entity;F)V"), require = 1)
    private void cera$renderParticlesLit(CallbackInfo ci) {
        cera$moveTo(Node.RenderParticlesLit);
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;render(Lnet/minecraft/entity/Entity;F)V"), require = 1)
    private void cera$renderParticles(CallbackInfo ci) {
        cera$moveTo(Node.RenderParticles);
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderSnowAndRain(F)V"), require = 1)
    private void cera$renderWeather(CallbackInfo ci) {
        if (cera$active()) {
            GlStateManager.enableDepthTest();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableAlphaTest();
            Minecraft.getInstance().cera$getShaderEngine().graph().moveTo(Node.RenderWeather);
        }
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;depthMask(Z)V", ordinal = 1, shift = At.Shift.AFTER), require = 1)
    private void cera$renderHand(CallbackInfo ci) {
        cera$moveTo(Node.RenderHand0);
    }

    @Inject(method = "render(IFJ)V", slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderSnowAndRain(F)V")), at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureManager;bind(Lnet/minecraft/resource/Identifier;)V", ordinal = 0), require = 1)
    private void cera$deferredPipeline(CallbackInfo ci) {
        if (cera$active()) {
            Minecraft.getInstance().cera$getShaderEngine().runDeferredPipeline();
        }
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I", ordinal = 3), require = 1)
    private void cera$renderChunk1(CallbackInfo ci) {
        if (cera$active()) {
            GlStateManager.depthMask(true);
            Minecraft.getInstance().cera$getShaderEngine().graph().moveTo(Node.RenderChunk1);
        }
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=hand"), require = 1)
    private void cera$renderLast(CallbackInfo ci) {
        if (cera$active()) {
            var engine = Minecraft.getInstance().cera$getShaderEngine();
            engine.graph()
                  .moveTo(Node.RenderLast);
            engine.finishRenderFinal();
        }
    }

    @WrapWithCondition(method = "renderItemInHand(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ItemInHandRenderer;renderInFirstPerson(F)V"), require = 1)
    private boolean cera$skipVanillaHand(ItemInHandRenderer instance, float tickDelta) {
        return !cera$active();
    }

    @WrapWithCondition(method = "setupFog(IF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;fogMode(I)V"), require = 1)
    private boolean cera$updateFogMode(int mode) {
        if (cera$active()) {
            Minecraft.getInstance().cera$getShaderEngine().state().updateFogMode(mode);
        }
        return true;
    }

    @Inject(method = "updateColorBuffer(FFFF)Ljava/nio/FloatBuffer;", at = @At("HEAD"), require = 1)
    private void cera$updateFogColor(float r, float g, float b, float a, CallbackInfoReturnable<FloatBuffer> cir) {
        if (cera$active()) {
            Minecraft.getInstance().cera$getShaderEngine().state().updateFogColor(r, g, b);
        }
    }
}
