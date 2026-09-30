package dev.rdh.argentum.mixin.features.texture;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.rdh.argentum.impl.render.texture.AtlasUploads;
import net.minecraft.client.render.texture.TextureUtil;
import org.embeddedt.embeddium.impl.texture.MipmapHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.IntBuffer;

@Mixin(TextureUtil.class)
public class TextureUtilMixin {
    @WrapWithCondition(method = "upload(I[IIIIIZZZ)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glTexSubImage2D(IIIIIIIILjava/nio/IntBuffer;)V", remap = false))
    private static boolean argentum$deferAnimatedUpload(int target, int level, int x, int y, int width, int height,
                                                         int format, int type, IntBuffer pixels) {
        return !AtlasUploads.collect(target, level, x, y, width, height, format, type, pixels);
    }

    /**
     * @reason better texture blending
     * @author rdh, embeddedt
     */
    @Overwrite
    private static int blendPixels(int one, int two, int three, int four, boolean checkAlpha) {
        return MipmapHelper.weightedAverageColor(MipmapHelper.weightedAverageColor(one, two), MipmapHelper.weightedAverageColor(three, four));
    }
}
