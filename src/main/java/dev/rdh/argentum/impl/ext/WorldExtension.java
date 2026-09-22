package dev.rdh.argentum.impl.ext;

import dev.rdh.argentum.impl.render.BlockEntityLight;

public interface WorldExtension {
    default BlockEntityLight argentum$getBlockEntityLight() {
        throw new UnsupportedOperationException();
    }
}
