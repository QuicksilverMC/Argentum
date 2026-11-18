package dev.rdh.argentum.mixin.features.text;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.rdh.argentum.impl.ext.SignTextCache;
import dev.rdh.argentum.impl.render.entity.NameTagBatch;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.block.entity.SignRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;

import java.util.List;

@Mixin(SignRenderer.class)
public class SignRendererMixin {
    @Unique
    private TextRenderer argentum$signTextRenderer;
    @Unique
    private NameTagBatch argentum$textBatch;
    @Unique
    private NameTagBatch argentum$shadedBatch;
    @Unique
    private boolean argentum$disabledLighting;
    @Unique
    private boolean argentum$pushedBlend;

    @WrapMethod(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V")
    private void argentum$balanceSignTextBatch(SignBlockEntity sign, double x, double y, double z, float tickDelta, int breakProgress, Operation<Void> original) {
        NameTagBatch batch = NameTagBatch.active();
        if (batch != null) {
            batch.enter();
            batch.setCapturesDraws(false);
        }
        try {
            original.call(sign, x, y, z, tickDelta, breakProgress);
        } finally {
            this.argentum$endBatch();
            this.argentum$endShadedText();
            if (batch != null) batch.exit();
        }
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;depthMask(Z)V", ordinal = 0, shift = At.Shift.AFTER))
    private void argentum$beginSignText(SignBlockEntity sign, double x, double y, double z, float tickDelta,
                                        int breakProgress, CallbackInfo ci, @Local TextRenderer textRenderer) {
        textRenderer.argentum$beginBatch(() -> {});
        this.argentum$signTextRenderer = textRenderer;

        NameTagBatch batch = NameTagBatch.capturing();
        if (batch == null || textRenderer.argentum$getBatcher().isVanilla()) return;
        batch.setCapturesDraws(true);
        this.argentum$textBatch = batch;
        if (!batch.beginShadedText()) return;
        this.argentum$shadedBatch = batch;
        if (GlStateManager.LIGHTING.enabled) {
            GlStateManager.disableLighting();
            this.argentum$disabledLighting = true;
        }
        if (textRenderer.argentum$getBatcher().isBlending() && !GlStateManager.BLEND.state.enabled) {
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            this.argentum$pushedBlend = true;
        }
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;depthMask(Z)V", ordinal = 1))
    private void argentum$endSignText(SignBlockEntity sign, double x, double y, double z, float tickDelta, int breakProgress, CallbackInfo ci) {
        this.argentum$endBatch();
        this.argentum$endShadedText();
    }

    @Unique
    private void argentum$endShadedText() {
        if (this.argentum$textBatch != null) {
            this.argentum$textBatch.setCapturesDraws(false);
            this.argentum$textBatch = null;
        }
        NameTagBatch batch = this.argentum$shadedBatch;
        if (batch == null) return;
        this.argentum$shadedBatch = null;
        batch.endShadedText();
        if (this.argentum$disabledLighting) {
            this.argentum$disabledLighting = false;
            GlStateManager.enableLighting();
        }
        if (this.argentum$pushedBlend) {
            this.argentum$pushedBlend = false;
            GlStateManager.disableBlend();
        }
    }

    @Unique
    private void argentum$endBatch() {
        TextRenderer textRenderer = this.argentum$signTextRenderer;
        if (textRenderer == null) return;
        this.argentum$signTextRenderer = null;
        textRenderer.argentum$endBatch();
    }

    @WrapOperation(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderUtils;wrapText(Lnet/minecraft/text/Text;ILnet/minecraft/client/render/TextRenderer;ZZ)Ljava/util/List;"))
    private List<Text> argentum$cacheWrappedLine(Text line, int width, TextRenderer textRenderer,
            boolean stripLeadingSpaces, boolean allowFormatting, Operation<List<Text>> original,
            @Local(argsOnly = true) SignBlockEntity sign) {
        SignTextCache cache = (SignTextCache)sign;
        int fontGeneration = textRenderer.argentum$getBatcher().generation();
        List<Text> wrapped = cache.argentum$getWrappedLine(line, fontGeneration);
        if (wrapped == null) {
            wrapped = original.call(line, width, textRenderer, stripLeadingSpaces, allowFormatting);
            cache.argentum$putWrappedLine(line, fontGeneration, wrapped);
        }
        return wrapped;
    }

    @WrapOperation(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/text/Text;getFormattedString()Ljava/lang/String;"))
    private String argentum$cacheFormattedLine(Text line, Operation<String> original, @Local(argsOnly = true) SignBlockEntity sign) {
        SignTextCache cache = (SignTextCache)sign;
        String formatted = cache.argentum$getFormattedLine(line);
        if (formatted == null) {
            formatted = original.call(line);
            cache.argentum$putFormattedLine(line, formatted);
        }
        return formatted;
    }
}
