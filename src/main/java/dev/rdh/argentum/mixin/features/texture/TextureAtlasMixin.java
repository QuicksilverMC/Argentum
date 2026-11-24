package dev.rdh.argentum.mixin.features.texture;

import com.google.common.collect.Iterators;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.AbstractTexture;
import net.minecraft.client.render.texture.Stitcher;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.metadata.AnimationMetadata;
import net.minecraft.client.resource.metadata.TextureMetadata;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.HashCommon;
import org.embeddedt.embeddium.impl.util.collections.quadtree.QuadTree;
import org.embeddedt.embeddium.impl.util.collections.quadtree.Rect2i;
import org.apache.logging.log4j.Logger;
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
import dev.rdh.argentum.impl.render.texture.ParallelSpriteLoading;
import dev.rdh.argentum.impl.ext.TextureAtlasExtension;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin extends AbstractTexture implements TextureAtlasExtension {
    @Shadow @Final
    private static Logger LOGGER;

    @Shadow @Final
    private Map<String, TextureAtlasSprite> stitchedSprites;

    @Shadow @Final
    private Map<String, TextureAtlasSprite> sourcedSprites;

    @Shadow
    private int maxMipLevel;

    @Shadow
    private Identifier getResourceId(Identifier textureId, int mipmaps) {
        throw new AssertionError();
    }

    @Unique
    private Map<TextureAtlasSprite, Exception> argentum$decoded;

    @Unique
    private Map<TextureAtlasSprite, Throwable> argentum$mipmapped;

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

    @Inject(method = "loadAndStitch", at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;", ordinal = 0))
    private void argentum$decodeSprites(ResourceManager resourceManager, CallbackInfo ci) {
        this.argentum$decoded = null;
        if (!Argentum.CONFIG.parallelTextureLoading) return;
        List<TextureAtlasSprite> sprites = new ArrayList<>(this.sourcedSprites.values());
        List<Exception> outcomes = ParallelSpriteLoading.map(sprites, sprite -> this.argentum$decode(resourceManager, sprite));
        Map<TextureAtlasSprite, Exception> decoded = new IdentityHashMap<>(sprites.size());
        for (int i = 0; i < sprites.size(); i++) {
            decoded.put(sprites.get(i), outcomes.get(i));
        }
        this.argentum$decoded = decoded;
    }

    @Unique
    private Exception argentum$decode(ResourceManager resourceManager, TextureAtlasSprite sprite) {
        Identifier identifier = new Identifier(sprite.getName());
        Identifier location = this.getResourceId(identifier, 0);
        try {
            Resource resource = resourceManager.getResource(location);
            BufferedImage[] images = new BufferedImage[1 + this.maxMipLevel];
            images[0] = TextureUtil.readImage(resource.asStream());
            TextureMetadata textureMetadata = resource.getMetadata("texture");
            if (textureMetadata != null) {
                List<Integer> mipmaps = textureMetadata.getMipmaps();
                if (!mipmaps.isEmpty()) {
                    int width = images[0].getWidth();
                    int height = images[0].getHeight();
                    if (MathHelper.smallestEncompassingPowerOfTwo(width) != width || MathHelper.smallestEncompassingPowerOfTwo(height) != height) {
                        throw new RuntimeException("Unable to load extra miplevels, source-texture is not power of two");
                    }
                }
                for (int level : mipmaps) {
                    if (level > 0 && level < images.length - 1 && images[level] == null) {
                        Identifier mipLocation = this.getResourceId(identifier, level);
                        try {
                            images[level] = TextureUtil.readImage(resourceManager.getResource(mipLocation).asStream());
                        } catch (IOException e) {
                            LOGGER.error("Unable to load miplevel {} from: {}", level, mipLocation, e);
                        }
                    }
                }
            }
            AnimationMetadata animationMetadata = resource.getMetadata("animation");
            sprite.load(images, animationMetadata);
            return null;
        } catch (RuntimeException | IOException e) {
            return e;
        }
    }

    @WrapOperation(method = "loadAndStitch", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resource/manager/ResourceManager;getResource(Lnet/minecraft/resource/Identifier;)Lnet/minecraft/client/resource/Resource;", ordinal = 0))
    private Resource argentum$useDecodedResource(ResourceManager resourceManager, Identifier location, Operation<Resource> original,
                                                 @Local TextureAtlasSprite sprite) throws IOException {
        Map<TextureAtlasSprite, Exception> decoded = this.argentum$decoded;
        if (decoded == null) return original.call(resourceManager, location);
        if (decoded.get(sprite) instanceof IOException e) throw e;
        return ParallelSpriteLoading.DECODED;
    }

    @WrapOperation(method = "loadAndStitch", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureUtil;readImage(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;", ordinal = 0))
    private BufferedImage argentum$skipDecodedImage(InputStream stream, Operation<BufferedImage> original) {
        return this.argentum$decoded == null ? original.call(stream) : null;
    }

    @WrapOperation(method = "loadAndStitch", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureAtlasSprite;load([Ljava/awt/image/BufferedImage;Lnet/minecraft/client/resource/metadata/AnimationMetadata;)V"))
    private void argentum$skipDecodedLoad(TextureAtlasSprite sprite, BufferedImage[] images, AnimationMetadata animation, Operation<Void> original) {
        Map<TextureAtlasSprite, Exception> decoded = this.argentum$decoded;
        if (decoded == null) {
            original.call(sprite, images, animation);
        } else if (decoded.get(sprite) instanceof RuntimeException e) {
            throw e;
        }
    }

    @Inject(method = "loadAndStitch", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;", ordinal = 0))
    private void argentum$mipmapSprites(ResourceManager resourceManager, CallbackInfo ci) {
        this.argentum$decoded = null;
        this.argentum$mipmapped = null;
        if (!Argentum.CONFIG.parallelTextureLoading) return;
        int mipLevel = this.maxMipLevel;
        List<TextureAtlasSprite> sprites = new ArrayList<>(this.sourcedSprites.values());
        List<Throwable> outcomes = ParallelSpriteLoading.map(sprites, sprite -> {
            try {
                sprite.applyMipmaps(mipLevel);
                return null;
            } catch (Throwable t) {
                return t;
            }
        });
        Map<TextureAtlasSprite, Throwable> mipmapped = new IdentityHashMap<>(sprites.size());
        for (int i = 0; i < sprites.size(); i++) {
            mipmapped.put(sprites.get(i), outcomes.get(i));
        }
        this.argentum$mipmapped = mipmapped;
    }

    @WrapOperation(method = "loadAndStitch", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureAtlasSprite;applyMipmaps(I)V", ordinal = 0))
    private void argentum$skipMipmappedSprite(TextureAtlasSprite sprite, int mipLevel, Operation<Void> original) throws Throwable {
        Map<TextureAtlasSprite, Throwable> mipmapped = this.argentum$mipmapped;
        if (mipmapped == null) {
            original.call(sprite, mipLevel);
            return;
        }
        Throwable failure = mipmapped.get(sprite);
        if (failure != null) throw failure;
    }

    @Inject(method = "loadAndStitch", at = @At("RETURN"))
    private void argentum$clearParallelState(CallbackInfo ci) {
        this.argentum$mipmapped = null;
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
