package dev.rdh.cera.mixin;

import dev.rdh.argentum.impl.ext.TextRendererExtension;
import dev.rdh.cera.modules.HdFonts;

import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.resource.Identifier;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextRenderer.class)
public abstract class TextRendererMixin implements TextRendererExtension {
    @Mutable
    @Shadow
    @Final
    private Identifier fontLocation;

    @Unique
    private Identifier cera$vanillaFontLocation;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cera$rememberFontLocation(GameOptions options, Identifier fontLocation, @Coerce Object textureManager, boolean unicode, CallbackInfo ci) {
        this.cera$vanillaFontLocation = fontLocation;
    }

    @Inject(method = "reload", at = @At("HEAD"))
    private void cera$resolveHdFont(@Coerce Object resources, CallbackInfo ci) {
        this.fontLocation = HdFonts.resolve(ResourceManager.client(), this.cera$vanillaFontLocation);
    }

    @Inject(method = "reload", at = @At("RETURN"))
    private void cera$applyWidths(@Coerce Object resources, CallbackInfo ci) {
        var batcher = this.argentum$getBatcher();
        batcher.setBlend(HdFonts.apply(ResourceManager.client(), this.fontLocation, batcher));
    }
}
