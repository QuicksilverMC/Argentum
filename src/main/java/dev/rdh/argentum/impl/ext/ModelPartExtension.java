package dev.rdh.argentum.impl.ext;

import net.minecraft.client.render.model.ModelPart;
import net.minecraft.resource.Identifier;

import org.joml.Vector3f;

import java.util.function.Consumer;

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

    default void argentum$setRenderListener(Consumer<ModelPart> listener) {
        throw new UnsupportedOperationException();
    }

    default boolean argentum$hasCustomState() {
        throw new UnsupportedOperationException();
    }
}
