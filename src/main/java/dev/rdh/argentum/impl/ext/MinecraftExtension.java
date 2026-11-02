package dev.rdh.argentum.impl.ext;

import dev.rdh.argentum.api.render.instancing.InstanceDrawTarget;
import dev.rdh.argentum.api.render.terrain.TerrainDrawTarget;

public interface MinecraftExtension {
    default boolean argentum$isRenderingDirect() {
        throw new UnsupportedOperationException();
    }

    default void argentum$requestFramebuffer() {
        throw new UnsupportedOperationException();
    }

    default InstanceDrawTarget argentum$getInstanceDrawTarget() {
        throw new UnsupportedOperationException();
    }

    default void argentum$setInstanceDrawTarget(InstanceDrawTarget target) {
        throw new UnsupportedOperationException();
    }

    default TerrainDrawTarget argentum$getTerrainDrawTarget() {
        throw new UnsupportedOperationException();
    }

    default void argentum$setTerrainDrawTarget(TerrainDrawTarget target) {
        throw new UnsupportedOperationException();
    }
}
