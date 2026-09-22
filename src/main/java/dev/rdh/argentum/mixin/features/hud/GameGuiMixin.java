package dev.rdh.argentum.mixin.features.hud;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.argentum.impl.render.hud.HudBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public abstract class GameGuiMixin extends GuiElement {
    @Shadow
    public abstract TextRenderer getTextRenderer();

    @Unique
    private HudBatch.Textured argentum$statusBatch;

    @Unique
    private HudBatch.Colored argentum$scoreboardBackgroundBatch;

    @Unique
    private HudBatch.Text argentum$scoreboardTextBatch;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void argentum$createBatches(Minecraft minecraft, CallbackInfo ci) {
        this.argentum$statusBatch = HudBatch.textured(16 * 1024);
        this.argentum$scoreboardBackgroundBatch = HudBatch.colored();
        this.argentum$scoreboardTextBatch = HudBatch.text(this.getTextRenderer(), this.argentum$scoreboardBackgroundBatch);
    }

    @Redirect(
            method = "renderStatusBars",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;drawTexture(IIIIII)V")
    )
    private void argentum$batchStatusIcon(GameGui gui, int x, int y, int u, int v, int width, int height) {
        this.argentum$statusBatch.quad(x, y, u, v, width, height, width, height, 256, 256, this.drawOffset);
    }

    @Inject(method = "renderStatusBars", at = @At("RETURN"))
    private void argentum$drawStatusBatch(CallbackInfo ci) {
        this.argentum$statusBatch.draw();
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;renderScoreboardObjective(Lnet/minecraft/scoreboard/ScoreboardObjective;Lnet/minecraft/client/render/Window;)V")
    )
    private void argentum$batchScoreboard(GameGui gui, ScoreboardObjective objective, Window window, Operation<Void> original) {
        this.argentum$scoreboardTextBatch.begin();
        try {
            original.call(gui, objective, window);
        } finally {
            this.argentum$scoreboardTextBatch.draw();
        }
    }

    @Redirect(
            method = "renderScoreboardObjective",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;fill(IIIII)V")
    )
    private void argentum$captureScoreboardBackground(int left, int top, int right, int bottom, int color) {
        this.argentum$scoreboardBackgroundBatch.fill(left, top, right, bottom, color);
    }
}
