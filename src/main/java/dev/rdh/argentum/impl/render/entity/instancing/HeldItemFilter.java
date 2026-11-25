package dev.rdh.argentum.impl.render.entity.instancing;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.Texture;

/**
 * Lazy block-atlas filter push for held items rendered while an entity is captured.
 *
 * <p>ItemRenderer.renderItemInHand switches the atlas to nearest/no-mipmap filtering (pushFilter: two
 * glTexParameteri) and back (popFilter: two more) around every held item. A captured item is drawn later by the
 * instancing batcher, which sets the filter itself once per batch, so the push only matters if the item falls back
 * to a vanilla immediate draw. {@link #defer} records the push, {@link #flush} applies it right before such a
 * draw, and {@link #pop} undoes it only if it was applied. Render thread only.
 */
public final class HeldItemFilter {
    private static Texture pending;
    private static boolean blur, mipmap, applied;

    private HeldItemFilter() {
    }

    /** Replaces texture.pushFilter(blur, mipmap) while capturing. */
    public static void defer(Texture texture, boolean blur, boolean mipmap) {
        pending = texture;
        HeldItemFilter.blur = blur;
        HeldItemFilter.mipmap = mipmap;
        applied = false;
    }

    /** Before a vanilla (non-instanced) held-item draw: apply the deferred push. */
    public static void flush() {
        if (pending == null || applied) return;
        GlStateManager.bindTexture(pending.getGlId());
        pending.pushFilter(blur, mipmap);
        applied = true;
    }

    /** Replaces texture.popFilter(); {@return true if a deferred push was handled} and the vanilla pop must be skipped. */
    public static boolean pop(Texture texture) {
        if (pending == null) return false;
        if (applied) texture.popFilter();
        pending = null;
        applied = false;
        return true;
    }
}
