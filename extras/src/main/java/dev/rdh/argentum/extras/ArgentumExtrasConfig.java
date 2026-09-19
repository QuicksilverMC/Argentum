package dev.rdh.argentum.extras;

public class ArgentumExtrasConfig {
    public boolean explosionParticles = true;
    public boolean spellParticles = true;
    public boolean portalParticles = true;
    public boolean smokeAndFlameParticles = true;
    public boolean redstoneParticles = true;
    public boolean waterParticles = true;
    public boolean miscellaneousParticles = true;
    public boolean steadyDebugHud = true;
    public int debugHudRefreshIntervalMs = 250;
    public int debugHudScale = 0;
    public boolean fpsHud = false;
    public int dynamicFovStrength = 100;
    public int portalDistortionStrength = 100;
    public int viewBobbingStrength = 100;
    public int hurtCameraStrength = 100;
    public int cloudRenderDistance = 0;
    public int cloudHeightOffset = 0;
    public int cloudSpeed = 100;
    public boolean cloudFog = true;
    public int terrainFogDensity = 100;
    public int fluidFogDensity = 100;
    public int entityShadowDistance = 16;
    public boolean armor = true;
    public boolean armorGlint = true;
    public boolean heldItems = true;
    public boolean wornHeads = true;
    public boolean capes = true;
    public boolean playerEars = true;
    public boolean stuckArrows = true;
    public boolean leashes = true;
    public boolean nameTags = true;
    public boolean secondNameTagLayer = true;
    public boolean sky = true;
    public boolean lowerSky = false;
    public boolean sunAndMoon = true;
    public boolean stars = true;
    public int vignetteStrength = 100;
    public int weatherRenderDistance = 0;
    public int weatherDensity = 100;
    public LeafQuality leafQuality = LeafQuality.FANCY;
    public boolean disableTextShadows = false;
    public boolean fxaa = false;
    public boolean smoothBlockOutlines = false;
    public float blockOutlineWidth = 2.0f;
    public boolean scaledBlockOutlineWidth = false;
    public boolean macosSmoothScrolling = true;
    public boolean macosRightClickEmulation = false;
    public boolean highDpiScreen = false;

    public void validate() {
        this.debugHudRefreshIntervalMs = Math.max(0, this.debugHudRefreshIntervalMs);
        this.debugHudScale = Math.clamp(this.debugHudScale, 0, 4);
        this.dynamicFovStrength = Math.clamp(this.dynamicFovStrength, 0, 100);
        this.portalDistortionStrength = Math.clamp(this.portalDistortionStrength, 0, 100);
        this.viewBobbingStrength = Math.clamp(this.viewBobbingStrength, 0, 100);
        this.hurtCameraStrength = Math.clamp(this.hurtCameraStrength, 0, 100);
        this.cloudRenderDistance = Math.clamp(this.cloudRenderDistance, 0, 1536);
        this.cloudHeightOffset = Math.clamp(this.cloudHeightOffset, -128, 128);
        this.cloudSpeed = Math.clamp(this.cloudSpeed, 0, 200);
        this.terrainFogDensity = Math.clamp(this.terrainFogDensity, 0, 100);
        this.fluidFogDensity = Math.clamp(this.fluidFogDensity, 0, 100);
        this.entityShadowDistance = Math.clamp(this.entityShadowDistance, 8, 128);
        this.vignetteStrength = Math.clamp(this.vignetteStrength, 0, 100);
        this.weatherRenderDistance = Math.clamp(this.weatherRenderDistance, 0, 15);
        this.weatherDensity = Math.clamp(this.weatherDensity, 0, 100);
        this.blockOutlineWidth = Math.clamp(this.blockOutlineWidth, 1.0f, 5.0f);
    }

}
