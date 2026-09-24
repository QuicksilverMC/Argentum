package dev.rdh.argentum.impl.render.gui.hud;

import dev.rdh.argentum.impl.Argentum;

import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import org.lwjgl.opengl.GL11;

public final class HudBatch {
    private static final BufferUploader UPLOADER = new BufferUploader();
    private static final HudRecorder RECORDER = new HudRecorder();

    private HudBatch() {
    }

    public static Colored colored() {
        return new Colored();
    }

    public static Textured textured(int capacityBytes) {
        return new Textured(capacityBytes);
    }

    public static Text text(TextRenderer renderer) {
        return new Text(renderer, null);
    }

    public static Text text(TextRenderer renderer, Runnable beforeText) {
        return new Text(renderer, beforeText);
    }

    public static final class Text {
        private final TextRenderer renderer;
        private final Runnable beforeText;
        private boolean drawing;

        private Text(TextRenderer renderer, Runnable beforeText) {
            this.renderer = renderer;
            this.beforeText = beforeText;
        }

        public void begin() {
            if (this.drawing) {
                warnUnbalanced();
                this.draw();
            }
            this.renderer.argentum$beginBatch(this.beforeText);
            this.drawing = true;
        }

        public boolean isDrawing() {
            return this.drawing;
        }

        public void draw() {
            this.draw(this.beforeText);
        }

        public void draw(Runnable beforeText) {
            if (!this.drawing) return;
            try {
                if (beforeText != null) beforeText.run();
            } finally {
                this.drawing = false;
                this.renderer.argentum$endBatch();
            }
        }
    }

    private static boolean warned;

    private static void warnUnbalanced() {
        if (warned) return;
        warned = true;
        Argentum.LOGGER.warn("A HUD text batch was still active when it was begun again, so it was flushed late. "
                + "Something rendered this HUD element twice, cancelled its render, or threw part way through.",
                new Throwable());
    }

    public static final class Colored implements Runnable {
        private boolean drawing;

        private Colored() {
        }

        public void fill(int left, int top, int right, int bottom, int color) {
            this.drawing = true;
            RECORDER.fill(HudRecorder.LAYER_BACKGROUND, left, top, right, bottom, color);
        }

        public void draw() {
            if (!this.drawing) {
                return;
            }

            RECORDER.flush();
            this.drawing = false;
        }

        @Override
        public void run() {
            this.draw();
        }
    }

    public static final class Textured {
        private final BufferBuilder buffer;
        private boolean drawing;

        private Textured(int capacityBytes) {
            this.buffer = new BufferBuilder(capacityBytes / Integer.BYTES);
        }

        public boolean isEmpty() {
            return !this.drawing;
        }

        public void quad(float x, float y, float u, float v, int sourceWidth, int sourceHeight, int width, int height, float textureWidth, float textureHeight, float z) {
            if (!this.drawing) {
                this.buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX);
                this.drawing = true;
            }

            float u0 = u / textureWidth;
            float v0 = v / textureHeight;
            float u1 = (u + sourceWidth) / textureWidth;
            float v1 = (v + sourceHeight) / textureHeight;
            this.buffer.vertex(x, y + height, z).texture(u0, v1).nextVertex();
            this.buffer.vertex(x + width, y + height, z).texture(u1, v1).nextVertex();
            this.buffer.vertex(x + width, y, z).texture(u1, v0).nextVertex();
            this.buffer.vertex(x, y, z).texture(u0, v0).nextVertex();
        }

        public void draw() {
            if (!this.drawing) {
                return;
            }

            this.buffer.end();
            UPLOADER.end(this.buffer);
            this.drawing = false;
        }
    }
}
