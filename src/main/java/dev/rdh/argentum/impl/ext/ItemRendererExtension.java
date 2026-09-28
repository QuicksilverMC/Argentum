package dev.rdh.argentum.impl.ext;

import dev.rdh.argentum.impl.render.AnimatedModelSprites;

public interface ItemRendererExtension {
    default AnimatedModelSprites argentum$getAnimatedSprites() {
        throw new UnsupportedOperationException();
    }
}
