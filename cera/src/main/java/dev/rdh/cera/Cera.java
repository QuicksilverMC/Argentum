package dev.rdh.cera;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.ornithemc.osl.resource.loader.api.client.ClientResourceLoaderEvents;
import net.ornithemc.osl.resource.loader.api.resource.repository.ResourcePackRepository;
import dev.rdh.argentum.impl.config.JsonOptionStorage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.celeritas.api.OptionGUIConstructionEvent;
import net.minecraft.util.math.BlockPos;

public class Cera implements ClientModInitializer {
    public static CeraConfig CONFIG = new CeraConfig();
    static JsonOptionStorage<CeraConfig> CONFIG_STORAGE;

	public static final Logger LOGGER = LogManager.getLogger("Cera");

    @Override
    public void onInitializeClient() {
        ResourcePackRepository.registerBundledModResourcePack("cera-default", "Cera Default Connected Textures",
                FabricLoader.getInstance().getModContainer("cera").orElseThrow(), "resourcepacks/default"
        );
        CONFIG_STORAGE = JsonOptionStorage.load(FabricLoader.getInstance().getConfigDir().resolve("cera.json"), CeraConfig.class, CeraConfig::new, CeraConfig::validate);
        CONFIG = CONFIG_STORAGE.getData();
        OptionGUIConstructionEvent.BUS.addListener(event -> CeraOptionPages.create().forEach(event::addPage));
        ClientResourceLoaderEvents.INIT_RESOURCE_MANAGER.register(resources -> {
            Minecraft minecraft = Minecraft.getInstance();
            resources.addReloader(minecraft.getTextureManager().cera$getAnimatedTextures());
            resources.addReloader(minecraft.getTextureManager().cera$getRandomEntities());
            resources.addReloader(minecraft.getBlocksAtlas().cera$getNaturalTextures());
            resources.addReloader(minecraft.cera$getDynamicLightRules());
            resources.addReloader(minecraft.getTextureManager().cera$getCustomGuis());
            resources.addReloader(minecraft.worldRenderer.cera$getCustomSky());
            resources.addReloader(minecraft.getBlocksAtlas().cera$getCustomColormaps());
            resources.addReloader(minecraft.cera$getCustomColors());
            resources.addReloader(minecraft.cera$getLightMaps());
            resources.addReloader(minecraft.cera$getCustomBlockLayers());
            resources.addReloader(minecraft.cera$getCustomLoadingScreens());
            resources.addReloader(minecraft.cera$getCustomPanorama());
        });
    }

    // From Config.getRandom(BlockPos, int) and Config.intHash(int) in OptiFine 1.8.9 HD U M6 pre2.
    public static int random(BlockPos pos, int face) {
        int hash = intHash(face + 37);
        hash = intHash(hash + pos.getX());
        hash = intHash(hash + pos.getZ());
        return intHash(hash + pos.getY());
    }

    public static int intHash(int value) {
        value = value ^ 61 ^ value >> 16;
        value += value << 3;
        value ^= value >> 4;
        value *= 668265261;
        return value ^ value >> 15;
    }
}
