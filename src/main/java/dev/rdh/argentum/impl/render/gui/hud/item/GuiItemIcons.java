package dev.rdh.argentum.impl.render.gui.hud.item;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.AnimatedModelSprites;
import dev.rdh.argentum.impl.render.gui.hud.HudRecorder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

public final class GuiItemIcons {
    private static final int ICON_SIZE = 16;
    static final int PADDING = 4;

    private static final GuiItemAtlas ATLAS = new GuiItemAtlas();
    private static final HudRecorder RECORDER = new HudRecorder();
    private static final GuiItemGlints GLINTS = new GuiItemGlints();

    private static boolean initialized;
    private static boolean ready;
    private static int iconPixels;
    private static boolean baking;
    private static boolean flushing;
    private static boolean warned;

    private GuiItemIcons() {
    }

    public static boolean enabled() {
        if (!Argentum.CONFIG.guiItemAtlas) return false;
        if (!initialized) {
            initialized = true;
            ATLAS.initialize();
        }
        // the atlas is created mid-frame, so leave that frame on the vanilla path
        return ATLAS.isSupported() && ready && iconPixels <= GuiItemAtlas.maxPixels();
    }

    public static boolean canBake(ItemStack item) {
        return item != null && item.getItem() != null;
    }

    public static void invalidate() {
        ATLAS.invalidate();
    }

    public static int acquire(BakedModel model, ItemStack item, Runnable bake) {
        return ATLAS.acquire(GuiItemAtlas.keyFor(model, item), sourceVersion(model), iconPixels(), () -> {
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

    private static int iconPixels() {
        return iconPixels;
    }

    private static int currentTick() {
        var world = Minecraft.getInstance().world;
        return world != null ? (int) world.getTime() : (int) (System.nanoTime() / 50_000_000L);
    }

    private static int sourceVersion(BakedModel model) {
        return AnimatedModelSprites.of(model).length > 0 ? currentTick() : 0;
    }

    public static void draw(int slot, int x, int y, float zOffset) {
        float u0 = GuiItemAtlas.u0(slot);
        float v0 = GuiItemAtlas.v0(slot);
        float extent = ATLAS.uvExtent();

        // the framebuffer's origin is bottom left, so v runs the opposite way to GUI space
        RECORDER.quad(HudRecorder.LAYER_CONTENT, HudRecorder.MATERIAL_PREMULTIPLIED, ATLAS.getTexture(),
                x - PADDING, y - PADDING, x + ICON_SIZE + PADDING, y + ICON_SIZE + PADDING,
                u0, v0 + extent, u0 + extent, v0,
                100.0F + zOffset, 0xFFFFFFFF, 0xFFFFFFFF);
    }

    public static void drawGlint(int slot, int x, int y, float zOffset, BakedModel model) {
        GLINTS.quad(slot, x, y, 100.0F + zOffset, ATLAS.uvExtent(), model.getParticleIcon());
    }

    public static void endFrame() {
        ready = initialized;
        if (initialized) iconPixels = (ICON_SIZE + 2 * PADDING) * Math.max(new Window(Minecraft.getInstance()).getScale(), 1);
        if (RECORDER.isEmpty() && GLINTS.isEmpty() || warned) return;
        warned = true;
        Argentum.LOGGER.warn("GUI item icons were recorded but never flushed; they will draw late and misplaced. "
                + "A screen is rendering items past every GuiItemIcons.flush() call site.", new Throwable());
    }

    public static void flush() {
        if (baking || flushing || RECORDER.isEmpty() && GLINTS.isEmpty()) return;

        // the glint pass pushes the texture matrix, which lands back here
        flushing = true;
        boolean alphaTest = GlStateManager.ALPHA_TEST.state.enabled;
        int alphaFunc = GlStateManager.ALPHA_TEST.func;
        float alphaRef = GlStateManager.ALPHA_TEST.ref;
        boolean blend = GlStateManager.BLEND.state.enabled;
        int srcRgb = GlStateManager.BLEND.sfactorRGB;
        int dstRgb = GlStateManager.BLEND.dfactorRGB;
        int srcAlpha = GlStateManager.BLEND.sfactorAlpha;
        int dstAlpha = GlStateManager.BLEND.dfactorAlpha;
        boolean lighting = GlStateManager.LIGHTING.enabled;
        try {
            GlStateManager.disableLighting();
            GlStateManager.enableAlphaTest();
            GlStateManager.alphaFunc(516, 0.1F);
            RECORDER.flush();
            GLINTS.flush(ATLAS.getTexture());
            Minecraft.getInstance().getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
            GlStateManager.alphaFunc(alphaFunc, alphaRef);
            if (!alphaTest) GlStateManager.disableAlphaTest();
            GlStateManager.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) GlStateManager.enableBlend();
            else GlStateManager.disableBlend();
            if (lighting) GlStateManager.enableLighting();
        } finally {
            flushing = false;
        }
    }

}
