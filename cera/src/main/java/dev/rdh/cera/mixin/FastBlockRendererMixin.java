package dev.rdh.cera.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import dev.rdh.cera.modules.BetterGrass;
import dev.rdh.cera.modules.EmissiveTextures;
import dev.rdh.cera.modules.colors.CustomColormaps;
import dev.rdh.cera.modules.ctm.ConnectedTextures;
import dev.rdh.cera.modules.ctm.CtmRenderContext;
import dev.rdh.cera.modules.NaturalTextures;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import dev.rdh.argentum.impl.render.terrain.compile.PrimitiveBuiltRenderSectionData;
import dev.rdh.argentum.impl.render.terrain.compile.pipeline.FastBlockRenderer;
import dev.rdh.argentum.impl.world.biome.BiomeColorCache;
import dev.rdh.argentum.impl.world.cloned.ChunkRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.embeddedt.embeddium.impl.model.light.LightPipeline;
import org.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import org.embeddedt.embeddium.impl.model.quad.BakedQuadView;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadOrientation;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = FastBlockRenderer.class, remap = false)
public class FastBlockRendererMixin {
    @Unique
    private final BetterGrass cera$betterGrass = Minecraft.getInstance().getBlocksAtlas().cera$getBetterGrass();
    @Unique
    private final ConnectedTextures cera$connectedTextures = Minecraft.getInstance().getBlocksAtlas().cera$getConnectedTextures();
    @Unique
    private final NaturalTextures cera$naturalTextures = Minecraft.getInstance().getBlocksAtlas().cera$getNaturalTextures();
    @Unique
    private final List<ConnectedTextures.Overlay> cera$overlays = new ObjectArrayList<>();
    @Unique
    private final CtmRenderContext cera$ctmContext = new CtmRenderContext(this.cera$betterGrass);
    @Unique
    private final CustomColormaps cera$customColormaps = Minecraft.getInstance().getBlocksAtlas().cera$getCustomColormaps();
    @Unique
    private final EmissiveTextures cera$emissiveTextures = Minecraft.getInstance().getTextureManager().cera$getEmissiveTextures();
    @Unique
    private final List<BakedQuad> cera$emissiveQuads = new ObjectArrayList<>();
    @Unique
    private boolean cera$emissive = false;
    @Unique
    private BlockState cera$colorState = null;

    @Shadow @Final
    private QuadLightData quadLight;

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Ldev/rdh/argentum/impl/render/terrain/compile/pipeline/FastBlockRenderer;renderQuads(Ljava/util/List;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/BlockState;Ldev/rdh/argentum/impl/world/cloned/ChunkRenderContext;Lorg/embeddedt/embeddium/impl/model/light/LightPipeline;Lnet/minecraft/util/math/Direction;ILdev/rdh/argentum/impl/world/biome/BiomeColorCache$BiomeColorSource;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers;Ldev/rdh/argentum/impl/render/terrain/compile/PrimitiveBuiltRenderSectionData;)V"))
    private void cera$renderQuads(FastBlockRenderer renderer, List<BakedQuad> quads, BlockPos pos,
            BlockState colorState, ChunkRenderContext world, LightPipeline lighter, Direction cullFace,
            int flags, BiomeColorCache.BiomeColorSource colorType, Material material,
            ChunkBuildBuffers buffers, PrimitiveBuiltRenderSectionData renderData, Operation<Void> original) {
        this.cera$overlays.clear();
        List<BakedQuad> transformed = this.cera$connectedTextures.transform(world, colorState, pos,
                this.cera$betterGrass.getFaceQuads(world, colorState, pos, cullFace, quads),
                this.cera$overlays, this.cera$ctmContext
        );
        this.cera$colorState = colorState;
        BiomeColorCache.BiomeColorSource resolver = this.cera$customColormaps.resolverFor(colorState);
        if (resolver != null) colorType = resolver;
        original.call(renderer, transformed, pos, colorState, world, lighter, cullFace, flags, colorType, material, buffers, renderData);
        for (ConnectedTextures.Overlay overlay : this.cera$overlays) {
            Material overlayMaterial = buffers.getRenderPassConfiguration()
                    .getMaterialForRenderType(overlay.layer());
            this.cera$colorState = overlay.tintState();
            BiomeColorCache.BiomeColorSource overlayType = this.cera$customColormaps.resolverFor(overlay.tintState());
            if (overlayType == null) overlayType = cera$getBiomeColorType(overlay.tintState());
            original.call(renderer, List.of(overlay.quad()), pos, overlay.tintState(), world, lighter,
                    cullFace, flags, overlayType,
                    overlayMaterial, buffers, renderData
            );
        }
        if (this.cera$emissiveTextures.active()) {
            this.cera$emissiveQuads.clear();
            for (BakedQuad quad : transformed) {
                var sprite = (TextureAtlasSprite) BakedQuadView.of(quad).celeritas$getSprite();
                TextureAtlasSprite emissive = this.cera$emissiveTextures.emissiveSprite(sprite);
                if (emissive != null) this.cera$emissiveQuads.add(EmissiveTextures.resprite(quad, sprite, emissive));
            }
            if (!this.cera$emissiveQuads.isEmpty()) {
                Material emissiveMaterial = buffers.getRenderPassConfiguration()
                        .getMaterialForRenderType(BlockLayer.CUTOUT_MIPPED);
                this.cera$emissive = true;
                this.cera$colorState = colorState;
                original.call(renderer, this.cera$emissiveQuads, pos, colorState, world, lighter,
                        cullFace, flags, colorType, emissiveMaterial, buffers, renderData);
                this.cera$emissive = false;
            }
        }
    }

    @Inject(method = "renderQuads", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/model/light/LightPipeline;calculate(Lorg/embeddedt/embeddium/impl/model/quad/ModelQuadView;IIILorg/embeddedt/embeddium/impl/model/light/data/QuadLightData;Lorg/embeddedt/embeddium/impl/model/quad/properties/ModelQuadFacing;Lorg/embeddedt/embeddium/impl/model/quad/properties/ModelQuadFacing;ZZ)V", shift = At.Shift.AFTER))
    private void cera$fullbrightEmissive(CallbackInfo ci) {
        if (!this.cera$emissive) return;
        for (int i = 0; i < 4; i++) this.quadLight.lm[i] |= 0xF0;
    }

    @WrapOperation(method = "renderQuads", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getColor(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;I)I", remap = true))
    private int cera$blockColormap(Block block, WorldView world, BlockPos pos, int tint, Operation<Integer> original) {
        int color = this.cera$customColormaps.getColor(this.cera$colorState, (ChunkRenderContext) world, pos);
        return color != -1 ? color : original.call(block, world, pos, tint);
    }

    @ModifyExpressionValue(method = "renderQuads", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resource/model/BakedQuad;hasTint()Z", remap = true))
    private boolean cera$forceTint(boolean original) {
        return original || this.cera$customColormaps.hasBlockColormap(this.cera$colorState);
    }

    @Unique
    private static BiomeColorCache.ColorType cera$getBiomeColorType(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.GRASS || block == Blocks.TALLGRASS || block == Blocks.REEDS) {
            return BiomeColorCache.ColorType.GRASS;
        }
        if (block == Blocks.DOUBLE_PLANT) {
            DoublePlantBlock.Variant variant = state.get(DoublePlantBlock.VARIANT);
            return variant == DoublePlantBlock.Variant.GRASS || variant == DoublePlantBlock.Variant.FERN
                    ? BiomeColorCache.ColorType.GRASS : null;
        }
        if (block == Blocks.LEAVES) {
            PlanksBlock.Variant variant = state.get(LeavesBlock.VARIANT);
            return variant == PlanksBlock.Variant.SPRUCE || variant == PlanksBlock.Variant.BIRCH
                    ? null : BiomeColorCache.ColorType.FOLIAGE;
        }
        return block == Blocks.LEAVES2 || block == Blocks.VINE
                ? BiomeColorCache.ColorType.FOLIAGE : null;
    }

    @Inject(method = "writeQuad", at = @At("HEAD"))
    private void cera$prepareNaturalTexture(BakedQuadView quad, BlockPos pos, Material material,
                                            ModelQuadOrientation orientation, ChunkBuildBuffers buffers, CallbackInfo ci,
                                            @Share("transform") LocalIntRef transform) {
        transform.set(this.cera$naturalTextures.getTransform(quad, pos));
    }

    @ModifyArg(method = "writeQuad", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/model/quad/BakedQuadView;getTexU(I)F"))
    private int cera$rotateNaturalTextureU(int u, @Share("transform") LocalIntRef transform) {
        return NaturalTextures.transformVertex(transform.get(), u);
    }

    @ModifyArg(method = "writeQuad", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/model/quad/BakedQuadView;getTexV(I)F"))
    private int cera$rotateNaturalTextureV(int v, @Share("transform") LocalIntRef transform) {
        return NaturalTextures.transformVertex(transform.get(), v);
    }
}
