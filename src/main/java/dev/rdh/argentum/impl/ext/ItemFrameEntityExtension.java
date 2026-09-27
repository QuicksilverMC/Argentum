package dev.rdh.argentum.impl.ext;

public interface ItemFrameEntityExtension {
    default long argentum$getBakedFrame() {
        return 0L;
    }

    default void argentum$setBakedFrame(long state) {
    }
}
