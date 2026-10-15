package dev.rdh.argentum.impl.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import pl.tomgirl.pylon.window.DisplaySdl;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.config.ArgentumConfig;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

public final class PylonFullscreen {
    public static final boolean SUPPORTED = FabricLoader.getInstance().isModLoaded("pylon");

    private PylonFullscreen() {
    }

    public static boolean sync() {
        if (!SUPPORTED) return false;
        boolean exclusive = Argentum.CONFIG.exclusiveFullscreen;
        DisplayMode mode = configuredMode(Argentum.CONFIG);
        DisplaySdl display = DisplaySdl.instance();
        boolean changed = display.isExclusiveFullscreen() != exclusive
                || exclusive && !Objects.equals(display.getFullscreenDisplayMode(), mode);
        display.setExclusiveFullscreen(exclusive);
        display.setFullscreenDisplayMode(mode);
        return changed;
    }

    public static void reenter() {
        Display.setFullscreen(false);
        Display.setFullscreen(true);
    }

    public static DisplayMode configuredMode(ArgentumConfig config) {
        return config.fullscreenWidth > 0
                ? new DisplayMode(config.fullscreenWidth, config.fullscreenHeight, 0, config.fullscreenRefreshRate)
                : null;
    }

    public static DisplayMode[] displayModes() {
        DisplayMode[] available;
        try {
            available = Display.getAvailableDisplayModes();
        } catch (IllegalStateException e) {
            available = new DisplayMode[0];
        }
        DisplayMode configured = configuredMode(Argentum.CONFIG);
        return Stream.concat(Arrays.stream(available), configured != null ? Stream.of(configured) : Stream.empty())
                .map(mode -> new DisplayMode(mode.getWidth(), mode.getHeight(), 0, mode.getFrequency()))
                .distinct()
                .sorted(Comparator.comparingInt(DisplayMode::getWidth)
                        .thenComparingInt(DisplayMode::getHeight)
                        .thenComparingInt(DisplayMode::getFrequency))
                .toArray(DisplayMode[]::new);
    }
}
