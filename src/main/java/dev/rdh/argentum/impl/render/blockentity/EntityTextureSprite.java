package dev.rdh.argentum.impl.render.blockentity;

import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.metadata.AnimationMetadata;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

public final class EntityTextureSprite extends TextureAtlasSprite {
    EntityTextureSprite(String name) {
        super(name);
    }

    static EntityTextureSprite blank(String name, int width, int height, int mipLevels) {
        EntityTextureSprite sprite = new EntityTextureSprite(name);
        sprite.width = width;
        sprite.height = height;
        int[][] levels = new int[mipLevels + 1][];
        levels[0] = new int[width * height];
        sprite.frames.add(levels);
        sprite.applyMipmaps(mipLevels);
        return sprite;
    }

    @Override
    public void load(BufferedImage[] images, AnimationMetadata animation) throws IOException {
        int width = images[0].getWidth();
        int height = images[0].getHeight();
        if (Math.min(Integer.lowestOneBit(width), Integer.lowestOneBit(height)) < 16) {
            throw new IOException("Not baking a " + width + "x" + height + " block entity texture; it would lower the block atlas mip level");
        }
        if (animation != null || width == height) {
            super.load(images, animation);
            return;
        }

        this.setFrames(new ArrayList<>());
        this.activeFrame = 0;
        this.frameTicks = 0;
        this.width = width;
        this.height = height;
        int[][] levels = new int[images.length][];
        for (int level = 0; level < images.length; level++) {
            BufferedImage image = images[level];
            if (image == null) continue;
            if (level > 0 && (image.getWidth() != width >> level || image.getHeight() != height >> level)) {
                throw new RuntimeException(String.format("Unable to load miplevel: %d, image is size: %dx%d, expected %dx%d",
                        level, image.getWidth(), image.getHeight(), width >> level, height >> level));
            }
            levels[level] = new int[image.getWidth() * image.getHeight()];
            image.getRGB(0, 0, image.getWidth(), image.getHeight(), levels[level], 0, image.getWidth());
        }
        this.frames.add(levels);
    }
}
