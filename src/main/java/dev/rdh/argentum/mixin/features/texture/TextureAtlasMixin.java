package dev.rdh.argentum.mixin.features.texture;

import com.google.common.collect.Iterators;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.AbstractTexture;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;

import it.unimi.dsi.fastutil.HashCommon;
import org.embeddedt.embeddium.impl.util.collections.quadtree.QuadTree;
import org.embeddedt.embeddium.impl.util.collections.quadtree.Rect2i;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.ext.TextureAtlasExtension;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin extends AbstractTexture implements TextureAtlasExtension {
    @Shadow @Final
    private Map<String, TextureAtlasSprite> stitchedSprites;

    @Unique
    private QuadTree<TextureAtlasSprite> celeritas$quadTree;

    @Unique private int celeritas$width;
    @Unique private int celeritas$height;

    @Inject(method = "loadAndStitch", at = @At("RETURN"))
    private void celeritas$buildLookup(CallbackInfo ci) {
        int width = 0;
        int height = 0;
        int minSize = Integer.MAX_VALUE;
        for (TextureAtlasSprite sprite : this.stitchedSprites.values()) {
            width = Math.max(width, sprite.getX() + sprite.getWidth());
            height = Math.max(height, sprite.getY() + sprite.getHeight());
            int maxDimension = Math.max(sprite.getWidth(), sprite.getHeight());
            minSize = Math.min(minSize, maxDimension);
        }
        this.celeritas$width = HashCommon.nextPowerOfTwo(width);
        this.celeritas$height = HashCommon.nextPowerOfTwo(height);
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

    @Redirect(method = "bindAndTick", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<TextureAtlasSprite> celeritas$visibleAnimations(List<TextureAtlasSprite> sprites) {
        Iterator<TextureAtlasSprite> iterator = sprites.iterator();
        return Argentum.CONFIG.animateOnlyVisibleTextures
                ? Iterators.filter(iterator, TextureAtlasSprite::argentum$shouldUpdate)
                : iterator;
    }

    @Override
    public TextureAtlasSprite argentum$findFromUV(float u, float v) {
        QuadTree<TextureAtlasSprite> quadTree = this.celeritas$quadTree;
        return quadTree == null ? null
                : quadTree.find(Math.round(u * this.celeritas$width), Math.round(v * this.celeritas$height));
    }

}
