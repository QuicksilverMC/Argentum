package dev.rdh.argentum.api.config;

import dev.rdh.argentum.impl.Argentum;

public final class FullscreenSettings {
    private FullscreenSettings() {
    }

    public static boolean isExclusive() {
        return Argentum.config().exclusiveFullscreen;
    }

    public static void setExclusive(boolean exclusive) {
        if (Argentum.config().exclusiveFullscreen == exclusive) return;
        Argentum.CONFIG.exclusiveFullscreen = exclusive;
        Argentum.CONFIG_STORAGE.save();
    }
}
