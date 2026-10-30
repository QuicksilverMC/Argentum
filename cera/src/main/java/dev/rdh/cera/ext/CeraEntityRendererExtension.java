package dev.rdh.cera.ext;

import net.minecraft.resource.Identifier;

public interface CeraEntityRendererExtension {
    default Identifier cera$getCustomTexture() {
        throw new UnsupportedOperationException();
    }

    default void cera$setCustomTexture(Identifier texture) {
        throw new UnsupportedOperationException();
    }
}
