package dev.rdh.argentum.impl.render;

import net.minecraft.client.Minecraft;

import dev.rdh.argentum.impl.Argentum;

public final class PresentationPacer {
    private long nextPresentNanos = Long.MIN_VALUE;

    public boolean shouldPresent(float refreshRate) {
        if (!Argentum.CONFIG.decoupledPresentation || Minecraft.getInstance().options.vsync || refreshRate <= 0.0F) {
            return true;
        }

        long now = System.nanoTime();
        if (now < this.nextPresentNanos) {
            return false;
        }
        this.nextPresentNanos = Math.max(this.nextPresentNanos + (long) (1.0e9 / refreshRate), now);
        return true;
    }
}
