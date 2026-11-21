package dev.rdh.argentum.extras.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.argentum.extras.ArgentumExtras;
import dev.rdh.argentum.extras.ArgentumExtrasConfig;
import dev.rdh.argentum.extras.CeraCompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.HitResult;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private ClientWorld world;

    // with everything in the sky hidden, vanilla still pays for the sky pass state changes
    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true)
    private void argentumExtras$skipEmptySky(float tickDelta, int anaglyphRenderPass, CallbackInfo ci) {
        ArgentumExtrasConfig config = ArgentumExtras.CONFIG;
        if (config.sky || config.sunAndMoon || config.stars) return;
        if (this.world.dimension.getId() == 1 || !this.world.dimension.isNatural()) return;
        // the black void box below the horizon still draws with the sky off
        if (this.minecraft.player.getEyePosition(tickDelta).y < this.world.getHorizonHeight()) return;
        if (CeraCompat.shadersActive()) return;

        GlStateManager.disableBlend();
        GlStateManager.enableAlphaTest();
        GlStateManager.enableFog();
        GlStateManager.enableTexture();
        GlStateManager.depthMask(true);
        Lighting.turnOff();
        ci.cancel();
    }

    @ModifyExpressionValue(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/Dimension;getSunriseColor(FF)[F"))
    private float[] argentumExtras$hideSunrise(float[] color) {
        return ArgentumExtras.CONFIG.sky ? color : null;
    }

    @WrapWithCondition(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/VertexBuffer;draw(I)V", ordinal = 0))
    private boolean argentumExtras$drawSky(VertexBuffer buffer, int mode) {
        return ArgentumExtras.CONFIG.sky;
    }

    @WrapWithCondition(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;callList(I)V", ordinal = 0))
    private boolean argentumExtras$drawSky(int list) {
        return ArgentumExtras.CONFIG.sky;
    }

    @WrapWithCondition(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/VertexBuffer;draw(I)V", ordinal = 2))
    private boolean argentumExtras$drawDarkSky(VertexBuffer buffer, int mode) {
        return ArgentumExtras.CONFIG.sky;
    }

    @WrapOperation(method = "renderSky", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 1),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 2)
    })
    private void argentumExtras$drawSunAndMoon(Tesselator tesselator, Operation<Void> original) {
        if (ArgentumExtras.CONFIG.sunAndMoon) {
            original.call(tesselator);
        } else {
            tesselator.getBuffer().end();
            tesselator.getBuffer().clear();
        }
    }

    @WrapWithCondition(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/VertexBuffer;draw(I)V", ordinal = 1))
    private boolean argentumExtras$drawStars(VertexBuffer buffer, int mode) {
        return ArgentumExtras.CONFIG.stars;
    }

    @WrapWithCondition(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;callList(I)V", ordinal = 1))
    private boolean argentumExtras$drawStars(int list) {
        return ArgentumExtras.CONFIG.stars;
    }

    @Inject(method = "renderBlockOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;disableTexture()V"))
    private void enableSmoothLines(PlayerEntity camera, HitResult hit, int i, float tickDelta, CallbackInfo ci) {
        if (ArgentumExtras.CONFIG.smoothBlockOutlines) {
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        }
    }

    @ModifyArg(method = "renderBlockOutline", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLineWidth(F)V"))
    private float argentumExtras$blockOutlineWidth(float original, @Share("originalWidth") LocalFloatRef originalWidth) {
        originalWidth.set(original);
        float f = ArgentumExtras.CONFIG.blockOutlineWidth;
        if (ArgentumExtras.CONFIG.scaledBlockOutlineWidth) {
            return Math.max(f, this.minecraft.width / 1920.0f * f);
        } else {
            return f;
        }
    }

    @Inject(method = "renderBlockOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;enableTexture()V"))
    private void disableSmoothLines(PlayerEntity camera, HitResult hit, int i, float tickDelta, CallbackInfo ci,
                                    @Share("originalWidth") LocalFloatRef originalWidth) {
        if (ArgentumExtras.CONFIG.smoothBlockOutlines) {
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_DONT_CARE);
        }
        GL11.glLineWidth(originalWidth.get());
    }

    // this is somewhat cursed since we wrap a method that argentum redirects...
    // but it seems to work
    @WrapWithCondition(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;callList(I)V", ordinal = 3))
    private boolean argentumExtras$drawLowerSky(int list) {
        return ArgentumExtras.CONFIG.lowerSky && ArgentumExtras.CONFIG.sky;
    }

    @ModifyExpressionValue(method = "renderFancyClouds", at = @At(value = "CONSTANT", args = "doubleValue=0.029999999329447746")) // lmao
    private double argentumExtras$vanillaCloudSpeed(double vanilla) {
        return vanilla * ArgentumExtras.CONFIG.cloudSpeed / 100.0D;
    }

    @ModifyExpressionValue(method = "renderFancyClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/Dimension;getCloudHeight()F"))
    private float argentumExtras$vanillaCloudHeight(float vanilla) {
        return vanilla + ArgentumExtras.CONFIG.cloudHeightOffset;
    }

    @ModifyExpressionValue(method = "renderFancyClouds", at = @At(value = "CONSTANT", args = "intValue=-3"))
    private int argentumExtras$vanillaCloudStart(int vanilla) {
        int distance = ArgentumExtras.CONFIG.cloudRenderDistance;
        return distance == 0 ? vanilla : -distance / 96;
    }

    @ModifyExpressionValue(method = "renderFancyClouds", at = {
            @At(value = "CONSTANT", args = "intValue=4", ordinal = 1),
            @At(value = "CONSTANT", args = "intValue=4", ordinal = 2)
    })
    private int argentumExtras$vanillaCloudEnd(int vanilla) {
        int distance = ArgentumExtras.CONFIG.cloudRenderDistance;
        return distance == 0 ? vanilla : distance / 96 - 1;
    }
}
