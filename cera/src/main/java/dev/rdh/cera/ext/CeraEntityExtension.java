package dev.rdh.cera.ext;

import java.util.Map;

public interface CeraEntityExtension {
    default float[] cera$getAnimationVars() {
        throw new UnsupportedOperationException();
    }

    default void cera$setAnimationVars(float[] vars) {
        throw new UnsupportedOperationException();
    }

    default Map<Object, float[]> cera$getAnimationPoses() {
        throw new UnsupportedOperationException();
    }

    default void cera$clearAnimationPoses() {
        throw new UnsupportedOperationException();
    }
}
