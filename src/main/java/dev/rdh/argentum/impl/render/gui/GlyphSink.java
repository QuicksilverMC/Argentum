package dev.rdh.argentum.impl.render.gui;

import net.minecraft.resource.Identifier;

public interface GlyphSink {
    void texture(Identifier page);

    void vertex(float x, float y, float u, float v);
}
