package dev.rdh.argentum.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;

import dev.rdh.argentum.impl.render.gui.hud.HudBatch;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11;

public class GuiBatchBenchmarkScreen extends Screen {
    private static final int COLUMNS = 48;
    private static final int ROWS = 32;
    private static final int ITEMS = COLUMNS * ROWS;
    private static final int WARMUP_FRAMES = 120;
    private static final int MEASURED_FRAMES = 240;
    private static final String[] LABELS = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"
    };

    private final boolean batched = Boolean.getBoolean("argentum.guiBenchmarkBatched");
    private final HudBatch.Colored backgrounds = HudBatch.colored();
    private final HudBatch.Textured icons = HudBatch.textured(256 * 1024);
    private HudBatch.Text textBatch;
    private final long[] samples = new long[MEASURED_FRAMES];
    private int frames;

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        GL11.glFinish();
        long start = System.nanoTime();
        this.renderContents();
        GL11.glFinish();

        if (this.frames >= WARMUP_FRAMES) {
            this.samples[this.frames - WARMUP_FRAMES] = System.nanoTime() - start;
        }
        if (++this.frames == WARMUP_FRAMES + MEASURED_FRAMES) {
            this.report();
            this.minecraft.stop();
        }
    }

    private void renderContents() {
        if (this.batched) {
            this.renderBatched();
        } else {
            this.renderUnbatched();
        }
    }

    private void renderUnbatched() {
        for (int i = 0; i < ITEMS; i++) {
            int x = i % COLUMNS * 18;
            int y = i / COLUMNS * 14;
            fill(x, y, x + 17, y + 13, 0xA0000000 | i * 0x30507);
        }

        for (int i = 0; i < ITEMS; i++) {
            this.textRenderer.drawWithShadow(LABELS[i & 15], i % COLUMNS * 18 + 1, i / COLUMNS * 14 + 2, 0xFFFFFFFF);
        }

        this.minecraft.getTextureManager().bind(ICONS_LOCATION);
        GlStateManager.enableBlend();
        for (int i = 0; i < ITEMS; i++) {
            this.drawTexture(i % COLUMNS * 18 + 8, i / COLUMNS * 14 + 2, 16, 0, 9, 9);
        }
        GlStateManager.disableBlend();
    }

    private void renderBatched() {
        for (int i = 0; i < ITEMS; i++) {
            int x = i % COLUMNS * 18;
            int y = i / COLUMNS * 14;
            this.backgrounds.fill(x, y, x + 17, y + 13, 0xA0000000 | i * 0x30507);
        }
        this.backgrounds.draw();

        if (this.textBatch == null) this.textBatch = HudBatch.text(this.textRenderer);
        this.textBatch.begin();
        for (int i = 0; i < ITEMS; i++) {
            this.textRenderer.drawWithShadow(LABELS[i & 15], i % COLUMNS * 18 + 1, i / COLUMNS * 14 + 2, 0xFFFFFFFF);
        }
        this.textBatch.draw();

        this.minecraft.getTextureManager().bind(ICONS_LOCATION);
        GlStateManager.enableBlend();
        for (int i = 0; i < ITEMS; i++) {
            int x = i % COLUMNS * 18 + 8;
            int y = i / COLUMNS * 14 + 2;
            this.icons.quad(x, y, 16, 0, 9, 9, 9, 9, 256, 256, this.drawOffset);
        }
        this.icons.draw();
        GlStateManager.disableBlend();
    }

    private void report() {
        Arrays.sort(this.samples);
        long total = 0;
        for (long sample : this.samples) {
            total += sample;
        }

        String variant = this.batched ? "batched" : "unbatched";
        long median = this.samples[this.samples.length / 2];
        String result = String.format(Locale.ROOT,
                "variant=%s%nitems=%d%nframes=%d%nmeanNanos=%d%nmedianNanos=%d%np95Nanos=%d%nminNanos=%d%n",
                variant, ITEMS, MEASURED_FRAMES, total / this.samples.length, median,
                this.samples[this.samples.length * 95 / 100], this.samples[0]
        );
        Path output = this.minecraft.gameDir.toPath().resolve("gui-benchmark-" + variant + ".txt");
        try {
            Path baselinePath = this.minecraft.gameDir.toPath().resolve("gui-benchmark-unbatched.txt");
            if (this.batched && Files.exists(baselinePath)) {
                Properties baseline = new Properties();
                try (var input = Files.newInputStream(baselinePath)) {
                    baseline.load(input);
                }
                long unbatchedMedian = Long.parseLong(baseline.getProperty("medianNanos"));
                result += String.format(Locale.ROOT, "medianSpeedup=%.2f%nmedianReductionPercent=%.1f%n",
                        (double)unbatchedMedian / median, 100.0 * (unbatchedMedian - median) / unbatchedMedian
                );
            }
            Files.writeString(output, result);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.print("Argentum GUI benchmark\n" + result);
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
