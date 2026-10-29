package dev.rdh.argentum.test;

import dev.rdh.argentum.api.render.gui.hud.HudBatch;

import net.minecraft.client.gui.screen.Screen;

import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.Locale;

public class FontBenchmarkScreen extends Screen {
    private static final int WARMUP_FRAMES = 60;
    private static final int MEASURED_FRAMES = 300;
    private static final String[] LABELS = "0123456789abcdef".split("");
    private static final String[] SCENARIOS = {"labels", "f3", "chat", "chatNew", "widths"};
    private static final String[] CHAT = {
            "§7[§bVIP§7] §fSteve§7: anyone up for §lbedwars§r?",
            "§eПривет всем, кто хочет играть?",
            "§a日本語のチャット §nunderlined§r text",
            "§c§mstruck out§r and Ελληνικά with ASCII 12345",
            "§6Ğğ Şş İı ŒœÀÁÂ §oitalic accents",
            "plain ascii chat line with nothing special"
    };

    private final String variant = System.getProperty("argentum.fontBenchmark");
    private final boolean vanilla = "vanilla".equals(this.variant);
    private final long[] samples = new long[MEASURED_FRAMES];
    private final String[] lines = new String[500];
    private HudBatch.Text batch;
    private int scenario;
    private int frames;
    private long sink;

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        if (this.scenario == SCENARIOS.length) return;
        fill(0, 0, this.width, this.height, 0xFF202020);
        int count = this.prepare(this.frames);

        GL11.glFinish();
        long start = System.nanoTime();
        if (!this.vanilla) {
            if (this.batch == null) this.batch = HudBatch.text(this.textRenderer);
            this.batch.begin();
        }
        this.run(count);
        if (!this.vanilla) this.batch.draw();
        GL11.glFinish();
        long elapsed = System.nanoTime() - start;

        if (this.frames >= WARMUP_FRAMES) this.samples[this.frames - WARMUP_FRAMES] = elapsed;
        if (++this.frames == WARMUP_FRAMES + MEASURED_FRAMES) {
            this.report();
            this.frames = 0;
            if (++this.scenario == SCENARIOS.length) this.minecraft.stop();
        }
    }

    private int prepare(int frame) {
        switch (SCENARIOS[this.scenario]) {
            case "f3" -> {
                for (int i = 0; i < 40; i++) {
                    this.lines[i] = String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f  C: %d/%d",
                            frame * 0.37 + i, 64 + frame * 0.01, -frame * 1.3 - i, frame * 7 % 900, i * 13);
                }
                return 40;
            }
            case "chat" -> {
                for (int i = 0; i < 60; i++) this.lines[i] = CHAT[i % CHAT.length] + " #" + i;
                return 60;
            }
            case "chatNew" -> {
                for (int i = 0; i < 60; i++) this.lines[i] = CHAT[i % CHAT.length] + " #" + (frame * 60 + i);
                return 60;
            }
            case "widths" -> {
                for (int i = 0; i < 500; i++) this.lines[i] = CHAT[i % CHAT.length] + " #" + (frame * 500 + i);
                return 500;
            }
            default -> {
                return 0;
            }
        }
    }

    private void run(int count) {
        switch (SCENARIOS[this.scenario]) {
            case "labels" -> {
                for (int i = 0; i < 48 * 32; i++) {
                    this.textRenderer.drawWithShadow(LABELS[i & 15], i % 48 * 18 + 1, i / 48 * 14 + 2, 0xFFFFFFFF);
                }
            }
            case "f3", "chat", "chatNew" -> {
                for (int i = 0; i < count; i++) {
                    this.textRenderer.drawWithShadow(this.lines[i], 2 + i / 30 * 280, 2 + i % 30 * 10, 0xFFE0E0E0);
                }
            }
            case "widths" -> {
                for (int i = 0; i < count; i++) this.sink += this.textRenderer.getWidth(this.lines[i]);
            }
        }
    }

    private void report() {
        Arrays.sort(this.samples);
        long total = 0;
        for (long sample : this.samples) total += sample;
        System.out.printf(Locale.ROOT, "FONTBENCH variant=%s scenario=%s medianUs=%.1f p95Us=%.1f meanUs=%.1f sink=%d%n",
                this.variant, SCENARIOS[this.scenario], this.samples[MEASURED_FRAMES / 2] / 1000.0,
                this.samples[MEASURED_FRAMES * 95 / 100] / 1000.0, total / 1000.0 / MEASURED_FRAMES, this.sink);
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
