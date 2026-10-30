package dev.rdh.cera.ext;

public interface CeraEntityExtension {
    default float[] cera$getAnimationVars() {
        throw new UnsupportedOperationException();
    }

    default void cera$setAnimationVars(float[] vars) {
        throw new UnsupportedOperationException();
    }
}
