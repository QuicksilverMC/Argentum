package dev.rdh.argentum.mixin.features.font;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.rdh.argentum.impl.ext.TextRendererExtension;
import dev.rdh.argentum.impl.render.gui.TextBatcher;

@Mixin(TextRenderer.class)
public abstract class TextRendererMixin implements TextRendererExtension {
    @Shadow
    private int[] characterWidths;

    @Final
    @Shadow
    private Identifier fontLocation;

    @Shadow
    @Final
    private TextureManager textureManager;

    @Shadow
    private float x;

    @Shadow
    private float y;

    @Shadow
    private boolean unicode;

    @Shadow
    private int color;

    @Shadow
    private int[] colors;

    @Shadow
    private boolean obfuscated;

    @Shadow
    private boolean bold;

    @Shadow
    private boolean italic;

    @Shadow
    private boolean underlined;

    @Shadow
    private boolean strikethrough;

    @Unique
    private final TextBatcher argentum$batcher = new TextBatcher();

    @Override
    public TextBatcher argentum$getBatcher() {
        return this.argentum$batcher;
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void argentum$readWidths(CallbackInfo ci) {
        this.argentum$batcher.readWidths(this.fontLocation, this.characterWidths);
    }

    @Inject(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void argentum$stringWidth(String text, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(this.argentum$batcher.stringWidth(text, this.unicode));
    }

    @Inject(method = "getWidth(C)I", at = @At("HEAD"), cancellable = true)
    private void argentum$charWidth(char chr, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Math.round(this.argentum$batcher.charWidth(chr, this.unicode)));
    }

    @Inject(method = {"reload", "setUnicode"}, at = @At("RETURN"))
    private void argentum$clearCaches(CallbackInfo ci) {
        this.argentum$batcher.clearCaches();
    }

    @Inject(method = "drawLayer(Ljava/lang/String;FFIZ)I", at = @At("HEAD"))
    private void argentum$flushBeforeImmediateText(String text, float x, float y, int color, boolean shadow,
            CallbackInfoReturnable<Integer> cir) {
        if (this.argentum$batcher.shouldFlushBeforeImmediate(text)) {
            this.argentum$batcher.runBeforeImmediateText();
        }
    }

    @Inject(method = "drawLayer(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void argentum$beginLayer(String text, boolean shadow, CallbackInfo ci) {
        float advance = this.argentum$batcher.begin(text, shadow, this.argentum$style(), this.x, this.y, this.textureManager,
                this.colors, this.unicode);
        if (!Float.isNaN(advance)) {
            this.x += advance;
            this.argentum$setStyle(this.argentum$batcher.endStyle());
            if (this.argentum$batcher.endColor() != -1) this.color = this.argentum$batcher.endColor();
            ci.cancel();
        }
    }

    @Unique
    private int argentum$style() {
        return (this.obfuscated ? TextBatcher.OBFUSCATED : 0)
                | (this.bold ? TextBatcher.BOLD : 0)
                | (this.strikethrough ? TextBatcher.STRIKETHROUGH : 0)
                | (this.underlined ? TextBatcher.UNDERLINED : 0)
                | (this.italic ? TextBatcher.ITALIC : 0);
    }

    @Unique
    private void argentum$setStyle(int style) {
        this.obfuscated = (style & TextBatcher.OBFUSCATED) != 0;
        this.bold = (style & TextBatcher.BOLD) != 0;
        this.strikethrough = (style & TextBatcher.STRIKETHROUGH) != 0;
        this.underlined = (style & TextBatcher.UNDERLINED) != 0;
        this.italic = (style & TextBatcher.ITALIC) != 0;
    }

    @WrapWithCondition(
            method = "drawLayer(Ljava/lang/String;FFIZ)I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;color4f(FFFF)V")
    )
    private boolean argentum$captureColor(float red, float green, float blue, float alpha) {
        this.argentum$batcher.setColor(red, green, blue, alpha);
        return true;
    }

    @Override
    public Identifier argentum$getFontLocation() {
        return this.fontLocation;
    }

    @Override
    public void argentum$invalidateTextCache() {
        this.argentum$batcher.invalidateGeometry();
    }

    @Override
    public void argentum$beginBatch(Runnable beforeImmediateText) {
        this.argentum$batcher.beginElementBatch(beforeImmediateText);
    }

    @Override
    public void argentum$endBatch() {
        this.argentum$batcher.endElementBatch(this.textureManager);
    }
}
