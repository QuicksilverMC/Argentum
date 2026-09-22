package dev.rdh.argentum.impl.render.hud;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import net.minecraft.client.render.vertex.DefaultVertexFormat;

import org.lwjgl.opengl.GL11;

public final class GradientBatch {
    private static final BufferBuilder BUFFER = new BufferBuilder(4 * 1024);
    private static final BufferUploader UPLOADER = new BufferUploader();

    private static boolean accepting;
    private static boolean started;

    private GradientBatch() {
    }

    public static void begin() {
        accepting = true;
    }

    /** Takes over a gradient inside a run, in vanilla's vertex order. False means draw it the normal way. */
    public static boolean record(int left, int top, int right, int bottom, int colorTop, int colorBottom, float z) {
        if (!accepting) {
            return false;
        }

        if (!started) {
            BUFFER.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR);
            started = true;
        }

        vertex(right, top, z, colorTop);
        vertex(left, top, z, colorTop);
        vertex(left, bottom, z, colorBottom);
        vertex(right, bottom, z, colorBottom);
        return true;
    }

    private static void vertex(int x, int y, float z, int color) {
        BUFFER.vertex(x, y, z)
                .color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24)
                .nextVertex();
    }

    public static void flush() {
        accepting = false;
        if (!started) {
            return;
        }
        started = false;

        BUFFER.end();

        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        UPLOADER.end(BUFFER);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableAlphaTest();
        GlStateManager.enableTexture();
    }
}
