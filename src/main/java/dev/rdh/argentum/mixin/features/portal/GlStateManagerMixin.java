package dev.rdh.argentum.mixin.features.portal;

import dev.rdh.argentum.impl.render.environment.EndPortalRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {
    @Inject(method = "blendFunc", at = @At("HEAD"))
    private static void argentum$changePortalBlend(int source, int destination, CallbackInfo ci) {
        if (GlStateManager.BLEND.sfactorRGB != source || GlStateManager.BLEND.dfactorRGB != destination) argentum$flushPortal(ci);
    }

    @Inject(method = "blendFuncSeparate", at = @At("HEAD"))
    private static void argentum$changePortalBlendSeparate(int sourceRgb, int destinationRgb,
            int sourceAlpha, int destinationAlpha, CallbackInfo ci) {
        GlStateManager.Blend blend = GlStateManager.BLEND;
        if (blend.sfactorRGB != sourceRgb || blend.dfactorRGB != destinationRgb
                || blend.sfactorAlpha != sourceAlpha || blend.dfactorAlpha != destinationAlpha) argentum$flushPortal(ci);
    }

    @Inject(method = "texGenMode", at = @At("TAIL"))
    private static void argentum$recordPortalTexGenMode(GlStateManager.TexGenMode coordinate, int mode, CallbackInfo ci) {
        EndPortalRenderer capture = EndPortalRenderer.capturing();
        if (capture != null) capture.texGenMode(coordinate, mode);
    }

    @Inject(method = "texGenParam", at = @At("TAIL"))
    private static void argentum$recordPortalTexGenPlane(GlStateManager.TexGenMode coordinate, int parameter,
            java.nio.FloatBuffer values, CallbackInfo ci) {
        EndPortalRenderer capture = EndPortalRenderer.capturing();
        if (capture != null) capture.texGenParam(coordinate, parameter, values);
    }

    @Inject(method = {"disableBlend", "enableAlphaTest", "disableAlphaTest", "alphaFunc",
            "enableLighting", "enableFog", "disableFog", "fogMode", "fogDensity", "fogStart", "fogEnd",
            "enableDepthTest", "disableDepthTest", "depthFunc", "depthMask", "enableCull", "disableCull", "cullFace",
            "enablePolygonOffset", "disablePolygonOffset", "polygonOffset", "colorMask", "enableColorLogicOp",
            "disableColorLogicOp", "logicOp", "clear", "callList", "viewport", "shadeModel",
            "activeTexture", "enableTexture", "disableTexture"}, at = @At("HEAD"))
    private static void argentum$flushPortal(CallbackInfo ci) {
        EndPortalRenderer capture = EndPortalRenderer.capturing();
        if (capture != null) capture.flush();
    }
}
