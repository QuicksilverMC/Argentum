package dev.rdh.argentum.mixin.features.blockentity.baking;

import dev.rdh.argentum.impl.render.blockentity.BakedBlockEntities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextRenderer.class)
public abstract class TextRendererMixin {
    @Shadow
    private boolean unicode;

    @Inject(method = "setUnicode", at = @At("HEAD"))
    private void argentum$rebakeSignText(boolean unicode, CallbackInfo ci) {
        WorldRenderer worldRenderer = Minecraft.getInstance().worldRenderer;
        if (unicode != this.unicode && worldRenderer != null && BakedBlockEntities.enabled()) {
            worldRenderer.reload();
        }
    }
}
