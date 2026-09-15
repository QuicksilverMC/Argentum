package dev.rdh.cera.mixin.colors;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.embeddedt.embeddium.api.util.ColorARGB;
import org.embeddedt.embeddium.api.util.ColorU8;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(World.class)
public class WorldMixin {
    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3d cera$skyColor(Vec3d original, Entity entity, float tickDelta) {
        World world = (World) (Object) this;
        int dimension = world.dimension.getId();
        if (dimension == 1) return cera$toVec(Minecraft.getInstance().cera$getCustomColors().getEndSkyColor(-1), original);
        if (dimension != 0) return original;
        BlockPos pos = new BlockPos(entity);
        int color = Minecraft.getInstance().getBlocksAtlas().cera$getCustomColormaps().skyColor(world.getBiome(pos), pos);
        return cera$scale(color, original, 0.5F, 0.66275F);
    }

    @ModifyReturnValue(method = "getFogColor", at = @At("RETURN"))
    private Vec3d cera$fogColor(Vec3d original, float tickDelta) {
        World world = (World) (Object) this;
        int dimension = world.dimension.getId();
        var colors = Minecraft.getInstance().cera$getCustomColors();
        if (dimension == -1) return cera$toVec(colors.getNetherFogColor(-1), original);
        if (dimension == 1) return cera$toVec(colors.getEndFogColor(-1), original);
        if (dimension != 0) return original;
        Entity camera = Minecraft.getInstance().getCamera();
        if (camera == null) return original;
        BlockPos pos = new BlockPos(camera);
        int color = Minecraft.getInstance().getBlocksAtlas().cera$getCustomColormaps().fogColor(world.getBiome(pos), pos);
        return cera$scale(color, original, 0.753F, 0.8471F);
    }

    // OptiFine treats the colormap as the daytime color and keeps vanilla's time-of-day/weather darkening
    // by dividing out vanilla's daytime base color. Without this, fog stays at full brightness at night.
    @Unique
    private static Vec3d cera$scale(int color, Vec3d original, float dayRed, float dayGreen) {
        if (color < 0) return original;
        return new Vec3d(
                Math.min(1.0, ColorU8.byteToNormalizedFloat(ColorARGB.unpackRed(color)) * original.x / dayRed),
                Math.min(1.0, ColorU8.byteToNormalizedFloat(ColorARGB.unpackGreen(color)) * original.y / dayGreen),
                Math.min(1.0, ColorU8.byteToNormalizedFloat(ColorARGB.unpackBlue(color)) * original.z)
        );
    }

    @Unique
    private static Vec3d cera$toVec(int color, Vec3d fallback) {
        return color >= 0 ? new Vec3d(
                ColorU8.byteToNormalizedFloat(ColorARGB.unpackRed(color)),
                ColorU8.byteToNormalizedFloat(ColorARGB.unpackGreen(color)),
                ColorU8.byteToNormalizedFloat(ColorARGB.unpackBlue(color))
        ) : fallback;
    }
}
