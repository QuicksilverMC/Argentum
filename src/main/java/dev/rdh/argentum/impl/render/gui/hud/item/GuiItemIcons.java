package dev.rdh.argentum.impl.render.gui.hud.item;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.ext.ItemRendererExtension;
import dev.rdh.argentum.impl.render.gui.hud.HudRecorder;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public final class GuiItemIcons {
    private static final int ICON_SIZE = 16;
    static final int PADDING = 4;

    private static final long ATLAS_SETTLE_MILLIS = 250L;
    private static final long ATLAS_IDLE_MILLIS = 30_000L;
    private static final long ATLAS_BUDGET_BYTES = 64L * 1024 * 1024;
    private static final int STACK_DEPTH = 64;

    private static final Int2ObjectMap<GuiItemAtlas> ATLASES = new Int2ObjectOpenHashMap<>();
    private static final Int2LongMap REQUESTED_SINCE = new Int2LongOpenHashMap();
    private static final IntSet REQUESTED = new IntOpenHashSet();
    private static final float[][] SCALES = new float[2][STACK_DEPTH * 2];
    private static final int[] DEPTHS = new int[2];
    private static final HudRecorder RECORDER = new HudRecorder();
    private static final GuiItemGlints GLINTS = new GuiItemGlints();

    private static boolean unsupported;
    private static int iconPixels;
    private static int matrixMode = GL11.GL_MODELVIEW;
    private static int viewportWidth;
    private static int viewportHeight;
    private static GuiItemAtlas atlas;
    private static GuiItemAtlas glintAtlas;
    private static boolean baking;
    private static boolean flushing;
    private static boolean warned;

    static {
        resetMatrices();
    }

    private GuiItemIcons() {
    }

    public static boolean enabled() {
        if (!Argentum.CONFIG.guiItemAtlas || unsupported) return false;
        int pixels = iconPixels();
        if (pixels != iconPixels) {
            iconPixels = pixels;
            atlas = ATLASES.get(pixels);
        }
        if (atlas == null) {
            if (iconPixels <= GuiItemAtlas.maxPixels()) REQUESTED.add(iconPixels);
            return false;
        }
        atlas.usedThisFrame = true;
        return atlas.isSupported();
    }

    private static int iconPixels() {
        float[] modelview = SCALES[0], projection = SCALES[1];
        int m = 2 * DEPTHS[0], p = 2 * DEPTHS[1];
        float extent = (ICON_SIZE + 2 * PADDING) / 2.0F * Math.max(
                modelview[m] * projection[p] * viewportWidth, modelview[m + 1] * projection[p + 1] * viewportHeight);
        return Math.max((int) Math.ceil(extent - 1.0E-3F), 1);
    }

    private static int stack() {
        return matrixMode == GL11.GL_MODELVIEW ? 0 : matrixMode == GL11.GL_PROJECTION ? 1 : -1;
    }

    public static void matrixModeChanged(int mode) {
        matrixMode = mode;
    }

    public static void matrixPushed() {
        int stack = stack();
        if (stack < 0 || DEPTHS[stack] == STACK_DEPTH - 1) return;
        float[] scale = SCALES[stack];
        int i = 2 * DEPTHS[stack]++;
        scale[i + 2] = scale[i];
        scale[i + 3] = scale[i + 1];
    }

    public static void matrixPopped() {
        int stack = stack();
        if (stack >= 0 && DEPTHS[stack] > 0) DEPTHS[stack]--;
    }

    public static void identityLoaded() {
        int stack = stack();
        if (stack < 0) return;
        int i = 2 * DEPTHS[stack];
        SCALES[stack][i] = SCALES[stack][i + 1] = 1.0F;
    }

    public static void scaled(double x, double y) {
        int stack = stack();
        if (stack < 0) return;
        int i = 2 * DEPTHS[stack];
        SCALES[stack][i] *= (float) Math.abs(x);
        SCALES[stack][i + 1] *= (float) Math.abs(y);
    }

    public static void orthoApplied(double left, double right, double bottom, double top) {
        scaled(2.0 / (right - left), 2.0 / (top - bottom));
    }

    public static void matrixMultiplied(FloatBuffer matrix) {
        int p = matrix.position();
        scaled(Math.hypot(matrix.get(p), matrix.get(p + 1)), Math.hypot(matrix.get(p + 4), matrix.get(p + 5)));
    }

    public static void viewportChanged(int width, int height) {
        viewportWidth = width;
        viewportHeight = height;
    }

    private static void resetMatrices() {
        matrixMode = GL11.GL_MODELVIEW;
        DEPTHS[0] = DEPTHS[1] = 0;
        SCALES[0][0] = SCALES[0][1] = SCALES[1][0] = SCALES[1][1] = 1.0F;
    }

    public static boolean canBake(ItemStack item) {
        return item != null && item.getItem() != null;
    }

    public static void invalidate() {
        for (GuiItemAtlas cached : ATLASES.values()) cached.invalidate();
    }

    public static int acquire(BakedModel model, ItemStack item, Runnable bake) {
        return atlas.acquire(GuiItemAtlas.keyFor(model, item), sourceVersion(model), () -> {
            // before the first push: pushMatrix/popMatrix flush pending icons, and the atlas is the render target here
            baking = true;
            GlStateManager.pushMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(-PADDING, ICON_SIZE + PADDING, ICON_SIZE + PADDING, -PADDING, 1000.0, 3000.0);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.translatef(0.0F, 0.0F, -2000.0F);

            try {
                bake.run();
            } finally {
                GlStateManager.matrixMode(GL11.GL_PROJECTION);
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                GlStateManager.popMatrix();
                baking = false;
            }
        });
    }

    public static boolean baking() {
        return baking;
    }

    private static int currentTick() {
        var world = Minecraft.getInstance().world;
        return world != null ? (int) world.getTime() : (int) (System.nanoTime() / 50_000_000L);
    }

    private static int sourceVersion(BakedModel model) {
        return ((ItemRendererExtension) Minecraft.getInstance().getItemRenderer()).argentum$getAnimatedSprites().of(model).length > 0 ? currentTick() : 0;
    }

    public static void draw(int slot, int x, int y, float zOffset) {
        float u0 = GuiItemAtlas.u0(slot);
        float v0 = GuiItemAtlas.v0(slot);
        float extent = GuiItemAtlas.UV_EXTENT;

        // the framebuffer's origin is bottom left, so v runs the opposite way to GUI space
        RECORDER.quad(HudRecorder.LAYER_CONTENT, HudRecorder.MATERIAL_PREMULTIPLIED, atlas.getTexture(),
                x - PADDING, y - PADDING, x + ICON_SIZE + PADDING, y + ICON_SIZE + PADDING,
                u0, v0 + extent, u0 + extent, v0,
                100.0F + zOffset, 0xFFFFFFFF, 0xFFFFFFFF);
    }

    public static void drawGlint(int slot, int x, int y, float zOffset, BakedModel model) {
        if (glintAtlas != atlas && !GLINTS.isEmpty()) flush();
        glintAtlas = atlas;
        GLINTS.quad(slot, x, y, 100.0F + zOffset, model.getParticleIcon());
    }

    public static void endFrame() {
        resetMatrices();
        iconPixels = 0;
        atlas = null;
        updateAtlases();
        if (RECORDER.isEmpty() && GLINTS.isEmpty() || warned) return;
        warned = true;
        Argentum.LOGGER.warn("GUI item icons were recorded but never flushed; they will draw late and misplaced. "
                + "A screen is rendering items past every GuiItemIcons.flush() call site.", new Throwable());
    }

    private static void updateAtlases() {
        long now = Minecraft.getTime();
        ATLASES.values().removeIf(cached -> {
            if (cached.usedThisFrame) {
                cached.usedThisFrame = false;
                cached.lastUsed = now;
                return false;
            }
            if (cached == glintAtlas || now - cached.lastUsed < ATLAS_IDLE_MILLIS) return false;
            cached.delete();
            return true;
        });

        REQUESTED_SINCE.keySet().retainAll(REQUESTED);
        for (int pixels : REQUESTED) {
            if (!REQUESTED_SINCE.containsKey(pixels)) {
                REQUESTED_SINCE.put(pixels, now);
            } else if (now - REQUESTED_SINCE.get(pixels) >= ATLAS_SETTLE_MILLIS && createAtlas(pixels, now)) {
                REQUESTED_SINCE.remove(pixels);
            }
        }
        REQUESTED.clear();
    }

    private static boolean createAtlas(int pixels, long now) {
        GuiItemAtlas created = new GuiItemAtlas(pixels);
        long bytes = created.estimatedBytes();
        for (GuiItemAtlas cached : ATLASES.values()) bytes += cached.estimatedBytes();
        while (bytes > ATLAS_BUDGET_BYTES) {
            Int2ObjectMap.Entry<GuiItemAtlas> oldest = null;
            for (Int2ObjectMap.Entry<GuiItemAtlas> entry : ATLASES.int2ObjectEntrySet()) {
                if (entry.getValue().lastUsed < now && (oldest == null || entry.getValue().lastUsed < oldest.getValue().lastUsed)) oldest = entry;
            }
            if (oldest == null) return false;
            GuiItemAtlas evicted = ATLASES.remove(oldest.getIntKey());
            bytes -= evicted.estimatedBytes();
            evicted.delete();
        }
        if (!created.initialize()) {
            unsupported = true;
            return false;
        }
        created.lastUsed = now;
        ATLASES.put(pixels, created);
        return true;
    }

    public static void flush() {
        if (baking || flushing || RECORDER.isEmpty() && GLINTS.isEmpty()) return;

        // the glint pass pushes the texture matrix, which lands back here
        flushing = true;
        int unit = GlStateManager.texture;
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        boolean alphaTest = GlStateManager.ALPHA_TEST.state.enabled;
        int alphaFunc = GlStateManager.ALPHA_TEST.func;
        float alphaRef = GlStateManager.ALPHA_TEST.ref;
        boolean blend = GlStateManager.BLEND.state.enabled;
        int srcRgb = GlStateManager.BLEND.sfactorRGB;
        int dstRgb = GlStateManager.BLEND.dfactorRGB;
        int srcAlpha = GlStateManager.BLEND.sfactorAlpha;
        int dstAlpha = GlStateManager.BLEND.dfactorAlpha;
        boolean lighting = GlStateManager.LIGHTING.enabled;
        boolean textureEnabled = GlStateManager.TEXTURES[0].state.enabled;
        boolean lightmapEnabled = GlStateManager.TEXTURES[1].state.enabled;
        int texture = GlStateManager.TEXTURES[0].texture;
        GlStateManager.Color color = GlStateManager.COLOR;
        float red = color.r, green = color.g, blue = color.b, alpha = color.a;
        int mode = matrixMode;
        boolean glints = !GLINTS.isEmpty();
        try {
            GlStateManager.disableLighting();
            GlStateManager.enableAlphaTest();
            GlStateManager.alphaFunc(516, 0.1F);
            RECORDER.flush();
            if (glints) {
                GLINTS.flush(glintAtlas.getTexture());
                glintAtlas = null;
            }
            GlStateManager.bindTexture(texture);
            if (red >= 0.0F) GlStateManager.color4f(red, green, blue, alpha);
            GlStateManager.alphaFunc(alphaFunc, alphaRef);
            if (!alphaTest) GlStateManager.disableAlphaTest();
            GlStateManager.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) GlStateManager.enableBlend();
            else GlStateManager.disableBlend();
            if (lighting) GlStateManager.enableLighting();
            if (textureEnabled) GlStateManager.enableTexture();
            else GlStateManager.disableTexture();
            if (lightmapEnabled) {
                GlStateManager.activeTexture(GLX.GL_TEXTURE1);
                GlStateManager.enableTexture();
            }
            if (glints) GlStateManager.matrixMode(mode);
            GlStateManager.activeTexture(GLX.GL_TEXTURE0 + unit);
        } finally {
            flushing = false;
        }
    }

}
