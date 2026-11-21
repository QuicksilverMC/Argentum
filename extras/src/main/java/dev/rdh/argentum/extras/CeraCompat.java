package dev.rdh.argentum.extras;

import dev.rdh.cera.modules.shaders.ShaderRender;

import net.fabricmc.loader.api.FabricLoader;

public final class CeraCompat {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("cera");

    private CeraCompat() {
    }

    public static boolean shadersActive() {
        return LOADED && Impl.shadersActive();
    }

    // kept in its own class so Cera's classes only load when Cera is installed
    private static final class Impl {
        static boolean shadersActive() {
            return ShaderRender.isManaged();
        }
    }
}
