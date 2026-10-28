package dev.rdh.cera.mixin.colors;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.embeddedt.embeddium.api.util.ColorARGB;
import org.embeddedt.embeddium.api.util.ColorU8;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow private float fogRed;
    @Shadow private float fogGreen;
    @Shadow private float fogBlue;
    @Shadow private boolean thiccFog;
    @Shadow @Final private int[] lightMapPixels;
    @Shadow private float lightMapFlicker;

    @Shadow protected abstract float getNightVisionScale(LivingEntity entity, float tickDelta);

    @Inject(method = "updateLightMap", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/DynamicTexture;upload()V"))
    private void cera$customLightmap(float tickDelta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        World world = mc.world;
        if (world == null || mc.player == null) return;
        boolean nightvision = mc.player.hasStatusEffect(StatusEffect.NIGHTVISION) && this.getNightVisionScale(mc.player, tickDelta) > 0.0F;
        mc.cera$getLightMaps().apply(this.lightMapPixels, world, this.lightMapFlicker, tickDelta, nightvision, mc.options.gamma);
    }

    // somehow mixin doesn't realize that it can localcapture this, so this seems to be necessary sadly
    @ModifyExpressionValue(method = "setupClearColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;getBlockInside(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;F)Lnet/minecraft/block/Block;"))
    private Block cera$storeBlock(Block b, @Share("block") LocalRef<Block> block) {
        block.set(b);
        return b;
    }

    @Inject(
            method = "setupClearColor",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/render/GameRenderer;lastFogBrightness:F",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0
            )
    )
    private void cera$submergedFog(float tickDelta, CallbackInfo ci, @Local Entity entity, @Share("block") LocalRef<Block> block) {
        if (this.thiccFog) return;
        Material material = block.get().getMaterial();
        var colormaps = Minecraft.getInstance().getBlocksAtlas().cera$getCustomColormaps();
        BlockPos pos = new BlockPos(entity);
        int color;
        if (material == Material.WATER) {
            color = colormaps.underwaterColor(entity.world.getBiome(pos), pos);
        } else if (material == Material.LAVA) {
            color = colormaps.underlavaColor(entity.world.getBiome(pos), pos);
        } else {
            return;
        }
        if (color < 0) return;
        this.fogRed = ColorU8.byteToNormalizedFloat(ColorARGB.unpackRed(color));
        this.fogGreen = ColorU8.byteToNormalizedFloat(ColorARGB.unpackGreen(color));
        this.fogBlue = ColorU8.byteToNormalizedFloat(ColorARGB.unpackBlue(color));
    }
}
