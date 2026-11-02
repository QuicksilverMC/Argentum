package dev.rdh.argentum.impl.ext;

import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;

import net.minecraft.client.render.Culler;

public interface WorldRendererExtension {
    default ArgentumWorldRenderer argentum$getWorldRenderer() {
        throw new UnsupportedOperationException();
    }

    default void argentum$setupShadowTerrain(Culler camera, Viewport shadowViewport, float tick) {
        throw new UnsupportedOperationException();
    }
}
