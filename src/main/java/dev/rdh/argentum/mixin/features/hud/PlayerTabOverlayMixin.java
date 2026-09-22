package dev.rdh.argentum.mixin.features.hud;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.rdh.argentum.impl.render.hud.HudBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.overlay.PlayerTabOverlay;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin extends GuiElement {
    @Unique
    private static final int MAX_SKIN_QUADS = 160;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private HudBatch.Colored argentum$backgroundBatch;

    @Unique
    private HudBatch.Textured argentum$textureBatch;

    @Unique
    private HudBatch.Textured argentum$iconBatch;

    @Unique
    private HudBatch.Text argentum$textBatch;

    @Unique
    private final Identifier[] argentum$skinTextures = new Identifier[MAX_SKIN_QUADS];

    @Unique
    private final float[] argentum$skinQuads = new float[MAX_SKIN_QUADS * 10];

    @Unique
    private Identifier argentum$skinTexture;

    @Unique
    private int argentum$skinQuadCount;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void argentum$createBuffers(Minecraft minecraft, net.minecraft.client.gui.GameGui gui, CallbackInfo ci) {
        this.argentum$backgroundBatch = HudBatch.colored();
        this.argentum$textureBatch = HudBatch.textured(16 * 1024);
        this.argentum$iconBatch = HudBatch.textured(256 * 1024);
        this.argentum$textBatch = HudBatch.text(this.minecraft.textRenderer, this.argentum$backgroundBatch);
    }

    @WrapMethod(method = "render")
    private void argentum$batchTabList(int width, Scoreboard scoreboard, ScoreboardObjective objective, Operation<Void> original) {
        this.argentum$skinQuadCount = 0;
        this.argentum$textBatch.begin();
        try {
            original.call(width, scoreboard, objective);
        } finally {
            this.argentum$textBatch.draw(this::argentum$prepareTextBatch);

            if (!this.argentum$iconBatch.isEmpty()) {
                this.minecraft.getTextureManager().bind(ICONS_LOCATION);
                this.argentum$iconBatch.draw();
            }
        }
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/overlay/PlayerTabOverlay;fill(IIIII)V")
    )
    private void argentum$captureBackground(int left, int top, int right, int bottom, int color) {
        this.argentum$backgroundBatch.fill(left, top, right, bottom, color);
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureManager;bind(Lnet/minecraft/resource/Identifier;)V")
    )
    private void argentum$captureSkinTexture(TextureManager manager, Identifier texture) {
        this.argentum$skinTexture = texture;
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiElement;drawTexture(IIFFIIIIFF)V")
    )
    private void argentum$captureSkin(int x, int y, float u, float v, int sourceWidth, int sourceHeight, int width, int height, float textureWidth, float textureHeight) {
        int index = this.argentum$skinQuadCount++;
        this.argentum$skinTextures[index] = this.argentum$skinTexture;
        int offset = index * 10;
        float[] quads = this.argentum$skinQuads;
        quads[offset] = x;
        quads[offset + 1] = y;
        quads[offset + 2] = u;
        quads[offset + 3] = v;
        quads[offset + 4] = sourceWidth;
        quads[offset + 5] = sourceHeight;
        quads[offset + 6] = width;
        quads[offset + 7] = height;
        quads[offset + 8] = textureWidth;
        quads[offset + 9] = textureHeight;
    }

    @Redirect(
            method = "renderPing",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/overlay/PlayerTabOverlay;drawTexture(IIIIII)V")
    )
    private void argentum$capturePing(PlayerTabOverlay overlay, int x, int y, int u, int v, int width, int height) {
        this.argentum$iconBatch.quad(x, y, u, v, width, height, width, height, 256, 256, this.drawOffset);
    }

    @Redirect(
            method = "renderDisplayScore",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/overlay/PlayerTabOverlay;drawTexture(FFIIII)V")
    )
    private void argentum$captureHeart(PlayerTabOverlay overlay, float x, float y, int u, int v, int width, int height) {
        this.argentum$iconBatch.quad(x, y, u, v, width, height, width, height, 256, 256, this.drawOffset);
    }

    @Unique
    private void argentum$prepareTextBatch() {
        this.argentum$backgroundBatch.draw();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color4f(1, 1, 1, 1);
        this.argentum$drawSkins();
    }

    @Unique
    private void argentum$drawSkins() {
        Identifier texture = null;
        for (int i = 0; i < this.argentum$skinQuadCount; i++) {
            Identifier nextTexture = this.argentum$skinTextures[i];
            if (!nextTexture.equals(texture)) {
                this.argentum$textureBatch.draw();
                texture = nextTexture;
                this.minecraft.getTextureManager().bind(texture);
            }

            int offset = i * 10;
            float[] quad = this.argentum$skinQuads;
            this.argentum$textureBatch.quad(
                    quad[offset], quad[offset + 1], quad[offset + 2], quad[offset + 3],
                    (int)quad[offset + 4], (int)quad[offset + 5], (int)quad[offset + 6], (int)quad[offset + 7],
                    quad[offset + 8], quad[offset + 9], 0
            );
        }

        this.argentum$textureBatch.draw();
    }
}
