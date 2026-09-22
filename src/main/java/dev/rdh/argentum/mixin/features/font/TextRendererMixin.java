package dev.rdh.argentum.mixin.features.font;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.vertex.VertexFormat;
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
import dev.rdh.argentum.impl.render.text.TextBatcher;

@Mixin(TextRenderer.class)
public abstract class TextRendererMixin implements TextRendererExtension {
    @Shadow
    private int[] characterWidths;

    @Shadow
    private byte[] glyphSizes;

    // Not @Final: cera makes this mutable to swap in an HD font, and a @Final shadow would then
    // mismatch the target. Argentum only ever reads it.
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
    private Identifier getFontPage(int page) {
        return null;
    }

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
        cir.setReturnValue(this.argentum$batcher.stringWidth(text, this.unicode, this.glyphSizes));
    }

    @Inject(method = "getWidth(C)I", at = @At("HEAD"), cancellable = true)
    private void argentum$charWidth(char chr, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Math.round(this.argentum$batcher.charWidth(chr, this.unicode, this.glyphSizes)));
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

    @ModifyExpressionValue(method = "drawLayer(Ljava/lang/String;Z)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;charAt(I)C", ordinal = 0))
    private char argentum$fixZeroWidthChars(char c) {
        return TextBatcher.normalizeSpace(c);
    }

    @Inject(method = "drawLayer(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void argentum$beginLayer(String text, boolean shadow, CallbackInfo ci) {
        float advance = this.argentum$batcher.begin(text, shadow, this.x, this.y, this.textureManager);
        if (!Float.isNaN(advance)) {
            this.x += advance;
            ci.cancel();
        }
    }

    @Inject(method = "drawLayer(Ljava/lang/String;Z)V", at = @At("RETURN"))
    private void argentum$endLayer(String text, boolean shadow, CallbackInfo ci) {
        this.argentum$batcher.end(this.x);
    }

    @Inject(method = "drawBasicGlyph", at = @At("HEAD"), cancellable = true)
    private void argentum$drawBasicGlyph(int character, boolean italic, CallbackInfoReturnable<Float> cir) {
        float advance = this.argentum$batcher.drawBasicGlyph(character, italic, this.x, this.y,
                this.textureManager, this.fontLocation);
        if (!Float.isNaN(advance)) cir.setReturnValue(advance);
    }

    @Inject(method = "drawUnicodeGlyph", at = @At("HEAD"), cancellable = true)
    private void argentum$drawUnicodeGlyph(char character, boolean italic, CallbackInfoReturnable<Float> cir) {
        float advance = this.argentum$batcher.drawUnicodeGlyph(character, italic, this.x, this.y,
                this.textureManager, this.getFontPage(character / 256), this.glyphSizes);
        if (!Float.isNaN(advance)) cir.setReturnValue(advance);
    }

    @WrapWithCondition(
            method = {"drawLayer(Ljava/lang/String;FFIZ)I", "drawLayer(Ljava/lang/String;Z)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;color4f(FFFF)V")
    )
    private boolean argentum$captureColor(float red, float green, float blue, float alpha) {
        this.argentum$batcher.setColor(red, green, blue, alpha);
        return true;
    }

    @WrapOperation(
            method = "drawLayer(Ljava/lang/String;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;getBuffer()Lnet/minecraft/client/render/vertex/BufferBuilder;"),
            require = 2
    )
    private BufferBuilder argentum$decorationBuffer(Tesselator tesselator, Operation<BufferBuilder> original) {
        return this.argentum$batcher.isBatching() ? this.argentum$batcher.decorationBuffer() : original.call(tesselator);
    }

    @WrapWithCondition(
            method = "drawLayer(Ljava/lang/String;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;begin(ILnet/minecraft/client/render/vertex/VertexFormat;)V"),
            require = 2
    )
    private boolean argentum$beginDecorations(BufferBuilder buffer, int mode, VertexFormat format) {
        return this.argentum$batcher.beginDecorations(mode);
    }

    @WrapWithCondition(
            method = "drawLayer(Ljava/lang/String;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;nextVertex()V"),
            require = 8
    )
    private boolean argentum$colorDecoration(BufferBuilder buffer) {
        this.argentum$batcher.colorDecoration(buffer);
        return true;
    }

    @WrapWithCondition(
            method = "drawLayer(Ljava/lang/String;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V"),
            require = 2
    )
    private boolean argentum$deferDecorations(Tesselator tesselator) {
        return !this.argentum$batcher.isBatching();
    }

    @WrapWithCondition(
            method = "drawLayer(Ljava/lang/String;Z)V",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;disableTexture()V"),
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;enableTexture()V")
            },
            require = 4
    )
    private boolean argentum$deferTextureState() {
        return !this.argentum$batcher.isBatching();
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
