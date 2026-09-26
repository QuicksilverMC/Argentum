package dev.rdh.argentum.impl.ext;

import dev.rdh.argentum.impl.render.blockentity.SlotSheet;

public interface SkullBlockEntityExtension {
    default SlotSheet.Entry argentum$getBakedHead() {
        return null;
    }

    default void argentum$setBakedHead(SlotSheet.Entry region) {
    }
}
