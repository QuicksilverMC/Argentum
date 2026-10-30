package dev.rdh.argentum.impl.ext;

import net.minecraft.resource.Identifier;

import org.joml.Vector3f;

public interface ModelPartExtension {
    default Vector3f argentum$getScale() {
        throw new UnsupportedOperationException();
    }

    default Identifier argentum$getTexture() {
        throw new UnsupportedOperationException();
    }

    default void argentum$setTexture(Identifier texture) {
        throw new UnsupportedOperationException();
    }

    default boolean argentum$areBoxesVisible() {
        throw new UnsupportedOperationException();
    }

    default void argentum$setBoxesVisible(boolean visible) {
        throw new UnsupportedOperationException();
    }

    default boolean argentum$hasCustomState() {
        throw new UnsupportedOperationException();
    }
}
