package dev.rdh.cera.ext;

import dev.rdh.cera.modules.CustomBlockLayers;
import dev.rdh.cera.modules.CustomLoadingScreens;
import dev.rdh.cera.modules.CustomPanorama;
import dev.rdh.cera.modules.colors.CustomColors;
import dev.rdh.cera.modules.DynamicLights;
import dev.rdh.cera.modules.OptifineCosmetics;
import dev.rdh.cera.modules.cem.CustomEntityModels;
import dev.rdh.cera.modules.cit.CustomItems;
import dev.rdh.cera.modules.colors.LightMaps;

public interface CeraMinecraftExtension {
    default DynamicLights.Rules cera$getDynamicLightRules() {
        throw new UnsupportedOperationException();
    }

    default CustomBlockLayers cera$getCustomBlockLayers() {
        throw new UnsupportedOperationException();
    }

    default CustomLoadingScreens cera$getCustomLoadingScreens() {
        throw new UnsupportedOperationException();
    }

    default CustomPanorama cera$getCustomPanorama() {
        throw new UnsupportedOperationException();
    }

    default CustomColors cera$getCustomColors() {
        throw new UnsupportedOperationException();
    }

    default LightMaps cera$getLightMaps() {
        throw new UnsupportedOperationException();
    }

    default CustomItems cera$getCustomItems() {
        throw new UnsupportedOperationException();
    }

    default OptifineCosmetics cera$getOptifineCosmetics() {
        throw new UnsupportedOperationException();
    }

    default CustomEntityModels cera$getCustomEntityModels() {
        throw new UnsupportedOperationException();
    }
}
