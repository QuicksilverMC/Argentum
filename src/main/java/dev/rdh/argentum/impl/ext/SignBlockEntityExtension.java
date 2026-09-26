package dev.rdh.argentum.impl.ext;

import dev.rdh.argentum.impl.render.blockentity.SignText;

public interface SignBlockEntityExtension {
    default SignText argentum$getBakedText() {
        return null;
    }

    default void argentum$setBakedText(SignText text) {
    }
}
