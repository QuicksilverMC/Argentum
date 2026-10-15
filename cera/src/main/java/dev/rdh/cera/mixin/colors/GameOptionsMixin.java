package dev.rdh.cera.mixin.colors;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.rdh.argentum.impl.Argentum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameOptions.class)
public class GameOptionsMixin {
    @Shadow
    public int viewDistance;

    @ModifyReturnValue(method = "getCloudRenderMode", at = @At("RETURN"))
    private int cera$cloudRenderMode(int original) {
        if (!Argentum.CONFIG.defaultClouds || this.viewDistance < 4) return original;
        return switch (Minecraft.getInstance().cera$getCustomColors().getCloudMode()) {
            case OFF -> 0;
            case FAST -> 1;
            case FANCY -> 2;
            case DEFAULT -> original;
        };
    }
}
