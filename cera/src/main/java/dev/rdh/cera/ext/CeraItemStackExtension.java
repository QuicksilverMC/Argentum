package dev.rdh.cera.ext;

import dev.rdh.cera.modules.cit.CustomItems;

public interface CeraItemStackExtension {
    default CustomItems.StackCache cera$getCitCache() {
        throw new UnsupportedOperationException();
    }

    default void cera$setCitCache(CustomItems.StackCache cache) {
        throw new UnsupportedOperationException();
    }
}
