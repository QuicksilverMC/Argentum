package dev.rdh.argentum.mixin.features.texture;

import com.google.common.collect.Iterators;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.AbstractTexture;
import net.minecraft.client.render.texture.Stitcher;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import org.embeddedt.embeddium.impl.util.collections.quadtree.QuadTree;
import org.embeddedt.embeddium.impl.util.collections.quadtree.Rect2i;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.blockentity.EntityTextureSprite;
import dev.rdh.argentum.impl.render.texture.AtlasUploads;
import dev.rdh.argentum.impl.ext.TextureAtlasExtension;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin extends AbstractTexture implements TextureAtlasExtension {
    @Shadow @Final
    private Map<String, TextureAtlasSprite> stitchedSprites;

    @Shadow
    private int maxMipLevel;

    @Unique
    private QuadTree<TextureAtlasSprite> celeritas$quadTree;

    @Unique private int celeritas$width;
    @Unique private int celeritas$height;

    @Inject(method = "loadAndStitch", at = @At("RETURN"))
    private void celeritas$buildLookup(CallbackInfo ci, @Local Stitcher stitcher) {
        AtlasUploads.reset();
        int minSize = Integer.MAX_VALUE;
        for (TextureAtlasSprite sprite : this.stitchedSprites.values()) {
            int maxDimension = Math.max(sprite.getWidth(), sprite.getHeight());
            minSize = Math.min(minSize, maxDimension);
        }
        this.celeritas$width = stitcher.getWidth();
        this.celeritas$height = stitcher.getHeight();
        Rect2i bounds = new Rect2i(0, 0, this.celeritas$width, this.celeritas$height);
        this.celeritas$quadTree = new QuadTree<>(bounds, minSize, this.stitchedSprites.values(),
                sprite -> new Rect2i(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight())
        );
    }

    @Inject(method = "loadAndStitch", at = @At("RETURN"))
    private void argentum$resetAnisotropy(CallbackInfo ci) {
        var capabilities = GL.getCapabilities();
        if (capabilities.GL_EXT_texture_filter_anisotropic || capabilities.GL_ARB_texture_filter_anisotropic) {
            GlStateManager.bindTexture(this.getGlId());
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, 1.0f);
        }
    }

    @WrapMethod(method = "bindAndTick")
    private void argentum$bindAndTick(Operation<Void> original) {
        AtlasUploads.begin();
        try {
            original.call();
        } finally {
            AtlasUploads.flush();
        }
    }

    @ModifyExpressionValue(method = "bindAndTick", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<TextureAtlasSprite> celeritas$visibleAnimations(@NotNull Iterator<TextureAtlasSprite> original) {
        return Argentum.CONFIG.animateOnlyVisibleTextures
                ? Iterators.filter(original, TextureAtlasSprite::argentum$shouldUpdate)
                : original;
    }

    @Override
    public TextureAtlasSprite argentum$findFromUV(float u, float v) {
        QuadTree<TextureAtlasSprite> quadTree = this.celeritas$quadTree;
        return quadTree == null ? null
                : quadTree.find(Math.round(u * this.celeritas$width), Math.round(v * this.celeritas$height));
    }

    @Override
    public int argentum$getWidth() {
        return this.celeritas$width;
    }

    @Override
    public int argentum$getHeight() {
        return this.celeritas$height;
    }

    @Override
    public int argentum$getMipLevel() {
        return this.maxMipLevel;
    }

    @Override
    public int[] argentum$getSpriteRects() {
        int[] rects = new int[this.stitchedSprites.size() * 4];
        int i = 0;
        for (TextureAtlasSprite sprite : this.stitchedSprites.values()) {
            if (sprite instanceof EntityTextureSprite) {
                continue;
            }
            rects[i++] = sprite.getX();
            rects[i++] = sprite.getY();
            rects[i++] = sprite.getWidth();
            rects[i++] = sprite.getHeight();
        }
        return i == rects.length ? rects : Arrays.copyOf(rects, i);
    }

}
