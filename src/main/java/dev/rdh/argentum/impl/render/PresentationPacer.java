package dev.rdh.argentum.impl.render;

import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;

import dev.rdh.argentum.impl.Argentum;

public final class PresentationPacer {
    private static final int WARMUP_SWAPS = 10;
    private static final long PROBE_NANOS = 500_000_000L;

    public enum Mode { OFF, ON, AUTO }

    private enum State { PROBE, PACE, PASS }

    private State state = State.PROBE;
    private long display;
    private float refreshRate;
    private boolean fullscreen;
    private int width;
    private int height;

    private int warmup = WARMUP_SWAPS;
    private long probeStart;
    private int probeSwaps;
    private long probeSwapNanos;

    private long deadlineNanos = Long.MIN_VALUE;
    private long lastFrameNanos;

    public boolean present(long display, float refreshRate, BooleanSupplier swap) {
        Minecraft minecraft = Minecraft.getInstance();
        Mode mode = Argentum.CONFIG.decoupledPresentation;
        if (mode == Mode.OFF || minecraft.options.vsync || refreshRate <= 0.0F) {
            this.startProbe();
            return swap.getAsBoolean();
        }
        if (mode == Mode.ON) {
            this.startProbe();
            return this.pace(refreshRate, swap);
        }

        boolean fullscreen = minecraft.isFullscreen();
        if (display != this.display || refreshRate != this.refreshRate || fullscreen != this.fullscreen
                || minecraft.width != this.width || minecraft.height != this.height) {
            this.display = display;
            this.refreshRate = refreshRate;
            this.fullscreen = fullscreen;
            this.width = minecraft.width;
            this.height = minecraft.height;
            this.startProbe();
        }

        return switch (this.state) {
            case PROBE -> this.probe(refreshRate, swap);
            case PACE -> this.pace(refreshRate, swap);
            case PASS -> swap.getAsBoolean();
        };
    }

    private boolean pace(float refreshRate, BooleanSupplier swap) {
        long now = System.nanoTime();
        long frameNanos = now - this.lastFrameNanos;
        this.lastFrameNanos = now;
        if (now + frameNanos < this.deadlineNanos) {
            return true;
        }

        long periodNanos = (long) (1.0e9 / refreshRate);
        this.deadlineNanos += periodNanos;
        if (this.deadlineNanos <= now) {
            this.deadlineNanos = now + periodNanos;
        }
        return swap.getAsBoolean();
    }

    private void startProbe() {
        this.state = State.PROBE;
        this.warmup = WARMUP_SWAPS;
        this.probeSwaps = 0;
        this.probeSwapNanos = 0;
    }

    private boolean probe(float refreshRate, BooleanSupplier swap) {
        long start = System.nanoTime();
        boolean result = swap.getAsBoolean();
        long end = System.nanoTime();

        if (this.warmup > 0) {
            if (--this.warmup == 0) {
                this.probeStart = end;
            }
            return result;
        }

        this.probeSwaps++;
        this.probeSwapNanos += end - start;
        long elapsed = end - this.probeStart;
        if (elapsed < PROBE_NANOS) {
            return result;
        }

        double swapRate = this.probeSwaps * 1.0e9 / elapsed;
        if (swapRate > refreshRate * 1.1) {
            this.state = State.PASS;
        } else if (swapRate > refreshRate * 0.9 && this.probeSwapNanos > elapsed / 10) {
            this.state = State.PACE;
        } else {
            this.startProbe();
            return result;
        }
        Argentum.LOGGER.info("Presentation pacing: {} ({} swaps/s at {} Hz, {}% blocked in swap)",
                this.state, Math.round(swapRate), refreshRate, this.probeSwapNanos * 100 / elapsed);
        return result;
    }
}
