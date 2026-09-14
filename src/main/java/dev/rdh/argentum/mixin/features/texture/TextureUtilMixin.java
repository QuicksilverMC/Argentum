package dev.rdh.argentum.mixin.features.texture;

import net.minecraft.client.render.texture.TextureUtil;
import org.embeddedt.embeddium.impl.texture.MipmapHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(TextureUtil.class)
public class TextureUtilMixin {
    /**
     * @reason better texture blending
     * @author rdh, embeddedt
     */
    @Overwrite
    private static int blendPixels(int one, int two, int three, int four, boolean checkAlpha) {
        return MipmapHelper.weightedAverageColor(MipmapHelper.weightedAverageColor(one, two), MipmapHelper.weightedAverageColor(three, four));
    }
}
