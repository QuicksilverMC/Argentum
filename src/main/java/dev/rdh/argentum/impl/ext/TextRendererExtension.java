package dev.rdh.argentum.impl.ext;

import net.minecraft.resource.Identifier;

import dev.rdh.argentum.impl.render.gui.TextBatcher;

public interface TextRendererExtension {
    default TextBatcher argentum$getBatcher() {
        throw new UnsupportedOperationException();
    }

    default void argentum$beginBatch(Runnable beforeImmediateText) {
        throw new UnsupportedOperationException();
    }

    default void argentum$endBatch() {
        throw new UnsupportedOperationException();
    }

    default Identifier argentum$getFontLocation() {
        throw new UnsupportedOperationException();
    }

    default byte[] argentum$getGlyphSizes() {
        throw new UnsupportedOperationException();
    }

    /** Drops cached text geometry so it re-bakes (e.g. after the §-code color palette changes). */
    default void argentum$invalidateTextCache() {
        throw new UnsupportedOperationException();
    }
}
