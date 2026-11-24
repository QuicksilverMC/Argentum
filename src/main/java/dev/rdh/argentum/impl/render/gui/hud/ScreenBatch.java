package dev.rdh.argentum.impl.render.gui.hud;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.gui.hud.item.GuiItemIcons;

import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class ScreenBatch {
    private static final HudRecorder RECORDER = new HudRecorder();

    private static boolean active;
    private static boolean flushing;

    private static int material;
    private static int texture;
    private static boolean alphaTest;
    private static int alphaFunc;
    private static float alphaRef;
    private static boolean depthTest;
    private static boolean depthMask;
    private static int depthFunc;
    private static boolean lighting;
    private static boolean fog;
    private static boolean cull;
    private static boolean lightmap;
    private static boolean maskR, maskG, maskB, maskA;

    private ScreenBatch() {
    }

    public static void begin() {
        active = Argentum.CONFIG.screenBatching;
    }

    public static void end() {
        try {
            flush();
        } finally {
            active = false;
        }
    }

    public static boolean fill(int left, int top, int right, int bottom, int color) {
        if (!canRecord()) return false;

        record(HudRecorder.MATERIAL_ALPHA, HudRecorder.UNTEXTURED);
        RECORDER.fill(HudRecorder.LAYER_CONTENT, left, top, right, bottom, color);

        GlStateManager.enableTexture();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableBlend();
        GlStateManager.color4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F, (color >>> 24) / 255.0F);
        return true;
    }

    public static boolean texture(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, float z) {
        if (!canRecord() || !GlStateManager.TEXTURES[0].state.enabled) return false;

        GlStateManager.Color color = GlStateManager.COLOR;
        if (color.r < 0.0F) return false;

        int material;
        if (!GlStateManager.BLEND.state.enabled) {
            material = HudRecorder.MATERIAL_OPAQUE;
        } else if (GlStateManager.BLEND.sfactorRGB == GL11.GL_SRC_ALPHA && GlStateManager.BLEND.dfactorRGB == GL11.GL_ONE_MINUS_SRC_ALPHA
                && GlStateManager.BLEND.sfactorAlpha == GL11.GL_ONE && GlStateManager.BLEND.dfactorAlpha == GL11.GL_ZERO) {
            material = HudRecorder.MATERIAL_ALPHA;
        } else {
            return false;
        }

        int packed = channel(color.a) << 24 | channel(color.r) << 16 | channel(color.g) << 8 | channel(color.b);
        record(material, GlStateManager.TEXTURES[0].texture);
        RECORDER.quad(HudRecorder.LAYER_CONTENT, material, GlStateManager.TEXTURES[0].texture,
                x0, y0, x1, y1, u0, v0, u1, v1, z, packed, packed);
        return true;
    }

    private static int channel(float value) {
        return (int) (Math.max(0.0F, Math.min(1.0F, value)) * 255.0F + 0.5F);
    }

    private static boolean canRecord() {
        return active && !flushing && !GuiItemIcons.baking() && GlStateManager.texture == 0;
    }

    private static void record(int material, int texture) {
        GuiItemIcons.flush();
        if (!RECORDER.isEmpty() && !matches(material, texture)) flush();
        if (!RECORDER.isEmpty()) return;

        ScreenBatch.material = material;
        ScreenBatch.texture = texture;
        alphaTest = GlStateManager.ALPHA_TEST.state.enabled;
        alphaFunc = GlStateManager.ALPHA_TEST.func;
        alphaRef = GlStateManager.ALPHA_TEST.ref;
        depthTest = GlStateManager.DEPTH.state.enabled;
        depthMask = GlStateManager.DEPTH.mask;
        depthFunc = GlStateManager.DEPTH.func;
        lighting = GlStateManager.LIGHTING.enabled;
        fog = GlStateManager.FOG.state.enabled;
        cull = GlStateManager.CULL.state.enabled;
        lightmap = GlStateManager.TEXTURES[1].state.enabled;
        GlStateManager.ColorMask mask = GlStateManager.COLOR_MASK;
        maskR = mask.r;
        maskG = mask.g;
        maskB = mask.b;
        maskA = mask.a;
    }

    private static boolean matches(int material, int texture) {
        return ScreenBatch.material == material && ScreenBatch.texture == texture
                && alphaTest == GlStateManager.ALPHA_TEST.state.enabled
                && alphaFunc == GlStateManager.ALPHA_TEST.func
                && alphaRef == GlStateManager.ALPHA_TEST.ref
                && depthTest == GlStateManager.DEPTH.state.enabled
                && depthMask == GlStateManager.DEPTH.mask
                && depthFunc == GlStateManager.DEPTH.func
                && lighting == GlStateManager.LIGHTING.enabled
                && fog == GlStateManager.FOG.state.enabled
                && cull == GlStateManager.CULL.state.enabled
                && lightmap == GlStateManager.TEXTURES[1].state.enabled
                && maskR == GlStateManager.COLOR_MASK.r && maskG == GlStateManager.COLOR_MASK.g
                && maskB == GlStateManager.COLOR_MASK.b && maskA == GlStateManager.COLOR_MASK.a;
    }

    public static void flush() {
        if (flushing || RECORDER.isEmpty()) return;

        flushing = true;
        int unit = GlStateManager.texture;
        boolean currentAlphaTest = GlStateManager.ALPHA_TEST.state.enabled;
        int currentAlphaFunc = GlStateManager.ALPHA_TEST.func;
        float currentAlphaRef = GlStateManager.ALPHA_TEST.ref;
        boolean currentDepthTest = GlStateManager.DEPTH.state.enabled;
        boolean currentDepthMask = GlStateManager.DEPTH.mask;
        int currentDepthFunc = GlStateManager.DEPTH.func;
        boolean currentLighting = GlStateManager.LIGHTING.enabled;
        boolean currentFog = GlStateManager.FOG.state.enabled;
        boolean currentCull = GlStateManager.CULL.state.enabled;
        boolean currentLightmap = GlStateManager.TEXTURES[1].state.enabled;
        boolean blend = GlStateManager.BLEND.state.enabled;
        int srcRgb = GlStateManager.BLEND.sfactorRGB;
        int dstRgb = GlStateManager.BLEND.dfactorRGB;
        int srcAlpha = GlStateManager.BLEND.sfactorAlpha;
        int dstAlpha = GlStateManager.BLEND.dfactorAlpha;
        boolean textureEnabled = GlStateManager.TEXTURES[0].state.enabled;
        int boundTexture = GlStateManager.TEXTURES[0].texture;
        GlStateManager.ColorMask mask = GlStateManager.COLOR_MASK;
        boolean currentR = mask.r, currentG = mask.g, currentB = mask.b, currentA = mask.a;
        GlStateManager.Color color = GlStateManager.COLOR;
        float red = color.r, green = color.g, blue = color.b, alpha = color.a;
        try {
            GlStateManager.activeTexture(GLX.GL_TEXTURE0);
            apply(alphaTest, alphaFunc, alphaRef, depthTest, depthMask, depthFunc, lighting, fog, cull, lightmap);
            GlStateManager.colorMask(maskR, maskG, maskB, maskA);
            RECORDER.flush();
            GlStateManager.colorMask(currentR, currentG, currentB, currentA);

            apply(currentAlphaTest, currentAlphaFunc, currentAlphaRef, currentDepthTest, currentDepthMask, currentDepthFunc,
                    currentLighting, currentFog, currentCull, currentLightmap);
            GlStateManager.bindTexture(boundTexture);
            if (textureEnabled) GlStateManager.enableTexture();
            else GlStateManager.disableTexture();
            GlStateManager.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) GlStateManager.enableBlend();
            else GlStateManager.disableBlend();
            if (red >= 0.0F) GlStateManager.color4f(red, green, blue, alpha);
            GlStateManager.activeTexture(GLX.GL_TEXTURE0 + unit);
        } finally {
            flushing = false;
        }
    }

    private static void apply(boolean alphaTest, int alphaFunc, float alphaRef, boolean depthTest, boolean depthMask, int depthFunc,
            boolean lighting, boolean fog, boolean cull, boolean lightmap) {
        if (alphaTest) GlStateManager.enableAlphaTest();
        else GlStateManager.disableAlphaTest();
        GlStateManager.alphaFunc(alphaFunc, alphaRef);
        if (depthTest) GlStateManager.enableDepthTest();
        else GlStateManager.disableDepthTest();
        GlStateManager.depthMask(depthMask);
        GlStateManager.depthFunc(depthFunc);
        if (lighting) GlStateManager.enableLighting();
        else GlStateManager.disableLighting();
        if (fog) GlStateManager.enableFog();
        else GlStateManager.disableFog();
        if (cull) GlStateManager.enableCull();
        else GlStateManager.disableCull();
        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        if (lightmap) GlStateManager.enableTexture();
        else GlStateManager.disableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
    }
}
