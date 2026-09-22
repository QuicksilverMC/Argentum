package dev.rdh.argentum.impl.config;

import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;

import dev.rdh.argentum.impl.render.terrain.fog.ArgentumFogService.FogShape;

public class ArgentumConfig {
    public int chunkBuilderThreads = 0;
    public boolean deferChunkUpdates = true;
    public AsyncOcclusionMode asyncOcclusion = AsyncOcclusionMode.EVERYTHING;
    public boolean fogCulling = true;
    public boolean entityCulling = true;
    public boolean entityInstancing = true;
    public int entityOcclusionIntervalMs = 50;
    public boolean particleCulling = true;
    public boolean blockFaceCulling = true;
    public boolean rasterOcclusionCulling = false;
    public boolean translucencySorting = true;
    public boolean renderPassOptimization = true;
    public boolean animateOnlyVisibleTextures = true;
    public int chunkFadeInDuration = 0;
    public FogShape fogShape = FogShape.SPHERICAL;
    public boolean fasterClouds = true;
    public int cpuRenderAheadLimit = 3;
    public int biomeBlendRadius = 3;
    public boolean safeChunkEdges = true;
    public boolean compactVertexFormat = false;
    public boolean checkGlErrors = false;
    public boolean fontBatching = true;
    public boolean guiItemAtlas = true;
    public boolean greedyRenderThread = false;
    public boolean explicitVsyncOption = true; // file only

	public void validate() {
        this.chunkBuilderThreads = Math.max(0, this.chunkBuilderThreads);
        this.entityOcclusionIntervalMs = Math.max(0, this.entityOcclusionIntervalMs);
        this.chunkFadeInDuration = Math.clamp(this.chunkFadeInDuration, 0, 2000);
        this.cpuRenderAheadLimit = Math.clamp(this.cpuRenderAheadLimit, 0, 9);
        this.biomeBlendRadius = Math.clamp(this.biomeBlendRadius, 0, 14);
        if (this.asyncOcclusion == null) {
            this.asyncOcclusion = AsyncOcclusionMode.EVERYTHING;
        }
        if (this.fogShape == null) {
            this.fogShape = FogShape.SPHERICAL;
        }
    }
}
