package dev.rdh.argentum.impl.ext;

import dev.rdh.argentum.impl.render.entity.instancing.InstanceRenderPass;

public interface BlockEntityRenderState {
    /** {@return the cached light, or -1 if nothing valid is held for this generation} */
    default int argentum$getCachedLight(int generation) {
        return -1;
    }

    default void argentum$cacheLight(int light, int generation) {
    }

    default InstanceRenderPass argentum$getPass() {
        return null;
    }

    default void argentum$setPass(InstanceRenderPass pass) {
    }
}
