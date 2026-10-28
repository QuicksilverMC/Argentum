package dev.rdh.argentum.impl.ext;

public interface MinecraftExtension {
    default boolean argentum$isRenderingDirect() {
        throw new UnsupportedOperationException();
    }

    default void argentum$requestFramebuffer() {
        throw new UnsupportedOperationException();
    }
}
