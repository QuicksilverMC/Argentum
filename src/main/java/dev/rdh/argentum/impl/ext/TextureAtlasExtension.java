package dev.rdh.argentum.impl.ext;

import dev.rdh.argentum.impl.render.blockentity.BakedBlockEntities;
import dev.rdh.argentum.impl.render.blockentity.SlotSheet;

import net.minecraft.client.render.texture.TextureAtlasSprite;

public interface TextureAtlasExtension {
    default TextureAtlasSprite argentum$findFromUV(float u, float v) {
        throw new UnsupportedOperationException();
    }

    default BakedBlockEntities.Region argentum$getEntityTextureRegion(String texture) {
        throw new UnsupportedOperationException();
    }

    default SlotSheet argentum$getPlayerHeads() {
        throw new UnsupportedOperationException();
    }

    default SlotSheet argentum$getFontPages() {
        throw new UnsupportedOperationException();
    }
}
