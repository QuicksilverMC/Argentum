package dev.rdh.cera.mixin;

import dev.rdh.cera.ext.CeraMinecraftExtension;
import dev.rdh.cera.modules.CustomBlockLayers;
import dev.rdh.cera.modules.CustomLoadingScreens;
import dev.rdh.cera.modules.CustomPanorama;
import dev.rdh.cera.modules.colors.CustomColors;
import dev.rdh.cera.modules.colors.LightMaps;
import dev.rdh.cera.modules.DynamicLights;
import dev.rdh.cera.modules.OptifineCosmetics;
import dev.rdh.cera.modules.cem.CustomEntityModels;
import dev.rdh.cera.modules.cit.CustomItems;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin implements CeraMinecraftExtension {
    @Unique
    private final DynamicLights.Rules cera$dynamicLightRules = new DynamicLights.Rules();

    @Unique
    private final CustomItems cera$customItems = new CustomItems();
    @Unique
    private final OptifineCosmetics cera$optifineCosmetics = new OptifineCosmetics();
    @Unique
    private final CustomColors cera$customColors = new CustomColors();
    @Unique
    private final LightMaps cera$lightMaps = new LightMaps();
    @Unique
    private final CustomBlockLayers cera$customBlockLayers = new CustomBlockLayers();
    @Unique
    private final CustomLoadingScreens cera$customLoadingScreens = new CustomLoadingScreens();
    @Unique
    private final CustomPanorama cera$customPanorama = new CustomPanorama();
    @Unique
    private final CustomEntityModels cera$customEntityModels = new CustomEntityModels();

    @Override
    public DynamicLights.Rules cera$getDynamicLightRules() {
        return this.cera$dynamicLightRules;
    }

    @Override
    public CustomBlockLayers cera$getCustomBlockLayers() {
        return this.cera$customBlockLayers;
    }

    @Override
    public CustomLoadingScreens cera$getCustomLoadingScreens() {
        return this.cera$customLoadingScreens;
    }

    @Override
    public CustomPanorama cera$getCustomPanorama() {
        return this.cera$customPanorama;
    }

    @Override
    public CustomColors cera$getCustomColors() {
        return this.cera$customColors;
    }

    @Override
    public LightMaps cera$getLightMaps() {
        return this.cera$lightMaps;
    }

    @Override
    public CustomItems cera$getCustomItems() {
        return this.cera$customItems;
    }

    @Override
    public OptifineCosmetics cera$getOptifineCosmetics() {
        return this.cera$optifineCosmetics;
    }

    @Override
    public CustomEntityModels cera$getCustomEntityModels() {
        return this.cera$customEntityModels;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cera$installCustomEntityModels(CallbackInfo ci) {
        this.cera$customEntityModels.install();
    }

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void cera$clearCustomSkyTextures(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft)(Object)this;
        if (minecraft.worldRenderer != null) {
            minecraft.worldRenderer.cera$getCustomSky().texturesReloading(minecraft.getTextureManager());
        }
    }
}
