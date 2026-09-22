package dev.rdh.argentum.mixin.features.text;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.rdh.argentum.impl.ext.SignTextCache;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.block.entity.SignRenderer;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SignRenderer.class)
public class SignRendererMixin {
    @Unique
    private TextRenderer argentum$signTextRenderer;

    @WrapMethod(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V")
    private void argentum$balanceSignTextBatch(SignBlockEntity sign, double x, double y, double z, float tickDelta, int breakProgress, Operation<Void> original) {
        try {
            original.call(sign, x, y, z, tickDelta, breakProgress);
        } finally {
            this.argentum$endBatch();
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;depthMask(Z)V",
                    ordinal = 0, shift = At.Shift.AFTER)
    )
    private void argentum$beginSignText(SignBlockEntity sign, double x, double y, double z, float tickDelta,
                                        int breakProgress, CallbackInfo ci, @Local TextRenderer textRenderer) {
        textRenderer.argentum$beginBatch(() -> {});
        this.argentum$signTextRenderer = textRenderer;
    }

    @Inject(
            method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;depthMask(Z)V",
                    ordinal = 1)
    )
    private void argentum$endSignText(SignBlockEntity sign, double x, double y, double z, float tickDelta, int breakProgress, CallbackInfo ci) {
        this.argentum$endBatch();
    }

    @Unique
    private void argentum$endBatch() {
        TextRenderer textRenderer = this.argentum$signTextRenderer;
        if (textRenderer == null) return;
        this.argentum$signTextRenderer = null;
        textRenderer.argentum$endBatch();
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/TextRenderUtils;wrapText(Lnet/minecraft/text/Text;ILnet/minecraft/client/render/TextRenderer;ZZ)Ljava/util/List;")
    )
    private List<Text> argentum$cacheWrappedLine(Text line, int width, TextRenderer textRenderer,
            boolean stripLeadingSpaces, boolean allowFormatting, Operation<List<Text>> original,
            @Local(argsOnly = true) SignBlockEntity sign) {
        SignTextCache cache = (SignTextCache)sign;
        boolean unicode = textRenderer.getUnicode();
        List<Text> wrapped = cache.argentum$getWrappedLine(line, unicode);
        if (wrapped == null) {
            wrapped = original.call(line, width, textRenderer, stripLeadingSpaces, allowFormatting);
            cache.argentum$putWrappedLine(line, unicode, wrapped);
        }
        return wrapped;
    }
}
