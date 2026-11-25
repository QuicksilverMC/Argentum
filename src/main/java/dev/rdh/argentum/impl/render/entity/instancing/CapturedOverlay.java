package dev.rdh.argentum.impl.render.entity.instancing;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;

import dev.rdh.argentum.mixin.features.model.instancing.LivingEntityRendererInvoker;

/**
 * Tracks the hurt/tint overlay's fixed-function state while an entity is captured.
 *
 * <p>setupOverlayColor/tearDownOverlayColor reprogram three texture units (~30 glTexEnvi calls plus unit
 * switches, a bind and texture toggles) for every model pass. Geometry captured for instancing is drawn later by
 * the instancing shader, which applies the overlay colour itself, so those calls are skipped during a capture
 * (LivingEntityRendererOverlayMixin). On drivers that rebuild pipeline state on texture-combiner changes (Apple's
 * GL-on-Metal) this is a large saving.
 *
 * <p>Some draws inside a capture are still immediate: layers that can't be captured (other mods' cosmetics, layers
 * a mod refuses to let us capture) and a main model that wasn't captured. LivingEntityRendererMixin brackets them
 * with {@link #beginImmediate}/{@link #endImmediate}: a skipped overlay is applied for real on entry, and nothing
 * is skipped inside (mods may tear the overlay down and set it up again mid-layer). Teardown runs for real whenever
 * a real overlay is active, so GL state always ends where vanilla leaves it. Render thread only.
 */
public final class CapturedOverlay {
    private static LivingEntityRenderer<?> renderer;
    private static LivingEntity entity;
    private static float tickDelta;
    private static boolean combine;
    private static boolean pending;     // an overlay is active whose GL setup was skipped
    private static boolean realActive;  // the overlay's GL setup really ran and has not been torn down
    private static boolean forcing, inTeardown, setupSkipped;
    private static int immediate;

    private CapturedOverlay() {
    }

    /** Whether a GL call inside setupOverlayColor/tearDownOverlayColor should be skipped right now. */
    public static boolean skipGl() {
        if (forcing) return false;
        if (inTeardown) return !realActive;
        return setupSkipped;
    }

    public static void beginSetup(LivingEntityRenderer<?> renderer, LivingEntity entity, float tickDelta, boolean combine) {
        if (forcing) return;
        CapturedOverlay.renderer = renderer;
        CapturedOverlay.entity = entity;
        CapturedOverlay.tickDelta = tickDelta;
        CapturedOverlay.combine = combine;
        setupSkipped = immediate == 0 && EntityCapture.current() != null;
    }

    public static void endSetup(boolean overlay) {
        if (forcing) return;
        if (setupSkipped) {
            pending = overlay;
        } else {
            realActive |= overlay;
            pending = false;
        }
        setupSkipped = false;
    }

    public static void beginTeardown() {
        inTeardown = true;
    }

    public static void endTeardown() {
        inTeardown = false;
        pending = false;
        realActive = false;
    }

    /** Before an immediate (uncaptured) draw inside a capture: make a skipped overlay real and stop skipping. */
    public static void beginImmediate() {
        immediate++;
        if (!pending || realActive || renderer == null) return;
        forcing = true;
        try {
            ((LivingEntityRendererInvoker) renderer).argentum$setupOverlayColor(entity, tickDelta, combine);
        } finally {
            forcing = false;
        }
        pending = false;
        realActive = true;
    }

    public static void endImmediate() {
        immediate--;
    }
}
