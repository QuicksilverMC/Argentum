package dev.rdh.argentum.impl.compat;

import net.fabricmc.loader.api.FabricLoader;

import dev.rdh.argentum.impl.Argentum;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public final class ScreencapperCompat {
    private static final MethodHandle BUSY = findBusy();

    private ScreencapperCompat() {
    }

    public static boolean isBusy() {
        if (BUSY == null) return false;
        try {
            return (boolean) BUSY.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static MethodHandle findBusy() {
        if (!FabricLoader.getInstance().isModLoaded("screencapper")) return null;
        try {
            Class<?> input = Class.forName("dev.deftu.screencapper.screenshot.capture.input.CaptureInput");
            Method busy = input.getDeclaredMethod("busy");
            busy.setAccessible(true);
            return MethodHandles.lookup().unreflect(busy).bindTo(input.getField("INSTANCE").get(null));
        } catch (ReflectiveOperationException e) {
            Argentum.LOGGER.warn("Failed to find ScreenCapper capture state", e);
            return null;
        }
    }
}
