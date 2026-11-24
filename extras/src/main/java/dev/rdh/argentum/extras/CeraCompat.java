package dev.rdh.argentum.extras;

import dev.rdh.cera.Cera;
import dev.rdh.cera.ext.CeraWorldRendererExtension;
import dev.rdh.cera.modules.shaders.ShaderRender;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;

public final class CeraCompat {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("cera");

    private CeraCompat() {
    }

    public static boolean hasCustomSky(WorldRenderer renderer, ClientWorld world) {
        return LOADED && Impl.hasCustomSky(renderer, world);
    }

    public static boolean shadersActive() {
        return LOADED && Impl.shadersActive();
    }

    // kept in its own class so Cera's classes only load when Cera is installed
    private static final class Impl {
        static boolean hasCustomSky(WorldRenderer renderer, ClientWorld world) {
            return Cera.CONFIG.customSky && ((CeraWorldRendererExtension) renderer).cera$getCustomSky().hasLayers(world);
        }

        static boolean shadersActive() {
            return ShaderRender.isManaged();
        }
    }
}
