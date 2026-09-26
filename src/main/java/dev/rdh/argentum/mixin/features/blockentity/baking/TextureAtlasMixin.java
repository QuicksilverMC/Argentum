package dev.rdh.argentum.mixin.features.blockentity.baking;

import com.llamalad7.mixinextras.sugar.Local;
import dev.rdh.argentum.impl.ext.TextureAtlasExtension;
import dev.rdh.argentum.impl.render.blockentity.BakedBlockEntities;
import dev.rdh.argentum.impl.render.blockentity.SlotSheet;

import net.minecraft.client.render.texture.Stitcher;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.manager.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin implements TextureAtlasExtension {
    @Shadow @Final
    private Map<String, TextureAtlasSprite> sourcedSprites;

    @Shadow @Final
    private String path;

    @Shadow
    private int maxMipLevel;

    @Unique
    private volatile Map<String, BakedBlockEntities.Region> argentum$entityTextureRegions = Map.of();

    @Unique
    private volatile SlotSheet argentum$playerHeads;

    @Unique
    private volatile SlotSheet argentum$fontPages;

    @Override
    public BakedBlockEntities.Region argentum$getEntityTextureRegion(String texture) {
        return this.argentum$entityTextureRegions.get(texture);
    }

    @Override
    public SlotSheet argentum$getPlayerHeads() {
        return this.argentum$playerHeads;
    }

    @Override
    public SlotSheet argentum$getFontPages() {
        return this.argentum$fontPages;
    }

    @Inject(method = "loadAndStitch", at = @At("HEAD"))
    private void argentum$registerBlockEntitySprites(ResourceManager resourceManager, CallbackInfo ci) {
        if ("textures".equals(this.path)) {
            BakedBlockEntities.registerSprites(this.sourcedSprites);
        }
    }

    @Inject(method = "loadAndStitch", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/Stitcher;stitch()V"))
    private void argentum$addPlayerHeadSheet(ResourceManager resourceManager, CallbackInfo ci, @Local Stitcher stitcher) {
        if ("textures".equals(this.path)) {
            BakedBlockEntities.slotSheets(this.maxMipLevel).forEach(stitcher::registerSprite);
        }
    }

    @Inject(method = "loadAndStitch", at = @At("RETURN"))
    private void argentum$captureBlockEntitySprites(ResourceManager resourceManager, CallbackInfo ci) {
        if ("textures".equals(this.path)) {
            this.argentum$entityTextureRegions = BakedBlockEntities.findRegions((TextureAtlas)(Object)this);
            this.argentum$playerHeads = BakedBlockEntities.playerHeads((TextureAtlas)(Object)this, this.maxMipLevel);
            this.argentum$fontPages = BakedBlockEntities.fontPages((TextureAtlas)(Object)this, this.maxMipLevel);
        }
    }
}
