package dev.rdh.cera;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.cera.modules.BetterGrass;
import dev.rdh.cera.modules.ctm.ConnectedTextures;
import dev.rdh.cera.modules.DynamicLights;
import com.ventooth.swansong.resources.ShaderPackManager;

public class CeraConfig {
    public BetterGrass.Mode betterGrass = BetterGrass.Mode.FANCY;
    public ConnectedTextures.Mode connectedTextures = ConnectedTextures.Mode.FANCY;
    public DynamicLights.Mode dynamicLights = DynamicLights.Mode.FANCY;
    public boolean naturalTextures = true;
    public boolean animatedTextures = true;
    public boolean customColors = true;
    public boolean customLightmaps = true;
    public boolean randomEntities = true;
    public boolean customEntityModels = true;
    public boolean customSky = true;
    public boolean customGuis = true;
    public boolean customItems = true;
    public boolean optifineCosmetics = true;
    public boolean emissiveTextures = true;
    public boolean customBlockLayers = true;
    public boolean customLoadingScreens = true;
    public boolean customPanorama = true;
    public boolean hdFonts = true;
    public String shaderPack = ShaderPackManager.DISABLED_SHADER_PACK_NAME;
    public boolean referenceShaderPack = false;
    public float renderQuality = 1F;
    public float shadowQuality = 1F;
    public float handDepth = 1F;
    public boolean depthOfField = false;
    public boolean shaderErrorReports = true;

    public void validate() {
        if (this.betterGrass == null) this.betterGrass = BetterGrass.Mode.OFF;
        if (this.connectedTextures == null) this.connectedTextures = ConnectedTextures.Mode.OFF;
        if (this.dynamicLights == null) this.dynamicLights = DynamicLights.Mode.OFF;
        if (this.shaderPack == null) this.shaderPack = ShaderPackManager.DISABLED_SHADER_PACK_NAME;
        if (this.hdFonts && Argentum.CONFIG != null) {
            Argentum.CONFIG.fontBatching = true;
        }
    }
}
