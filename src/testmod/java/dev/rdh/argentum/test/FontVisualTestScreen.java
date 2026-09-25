package dev.rdh.argentum.test;

import dev.rdh.argentum.impl.render.gui.hud.HudBatch;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.resource.Identifier;

public class FontVisualTestScreen extends Screen {
    private static final Identifier ICONS = new Identifier("textures/gui/icons.png");
    private final String variant = System.getProperty("argentum.fontTestVariant", "batched");
    private final HudBatch.Colored backgroundBatch = HudBatch.colored();
    private HudBatch.Text textBatch;
    private int ticks;

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        fill(0, 0, this.width, this.height, 0xFF202020);

        int x = 16;
        int y = 16;
        this.textRenderer.draw("Argentum font visual test", x, y, 0xFFFFFFFF);
        this.textRenderer.draw("Formatting: §aGreen §lBold §oItalic §rReset", x, y + 16, 0xFFFFFFFF);
        this.textRenderer.draw("Unicode: Ελληνικά Русский 日本語 Ğğ", x, y + 32, 0xFFFFFFFF);
        this.textRenderer.drawWithShadow("Shadow: colored text", x, y + 48, 0xFFFFAA55);
        this.textRenderer.draw("Decorations: §nUnderline §mStrike §n§mBoth", x, y + 64, 0xFFFFFFFF);

        boolean bidirectional = this.textRenderer.isBidirectional();
        this.textRenderer.setBidirectional(true);
        this.textRenderer.draw("שלום עולם", x, y + 80, 0xFFFFFFFF);
        this.textRenderer.setBidirectional(bidirectional);

        this.renderHudBatchTest(x, y + 112);
        this.renderCacheStateTest(270, y);
    }

    private void renderCacheStateTest(int x, int y) {
        this.textRenderer.draw("Carry: plain §lbold", x, y, 0xFFFFFFFF);
        this.textRenderer.drawWithShadow("Carry: plain §lbold", x, y + 12, 0xFFFFFFFF);
        this.textRenderer.drawWithShadow("Carry: §oitalic §lboth", x, y + 24, 0xFFFFFFFF);

        this.textRenderer.drawWithShadow("Alpha: fading text", x, y + 40, 0xFFFFFFFF);
        this.textRenderer.drawWithShadow("Alpha: fading text", x, y + 52, 0x80FFFFFF);
        this.textRenderer.drawWithShadow("Alpha: fading text", x, y + 64, 0x40FFFFFF);

        int[] alphas = {0xFF, 0x80, 0x40};
        HudBatch.Text batch = this.variant.equals("vanilla") ? null : HudBatch.text(this.textRenderer);
        if (batch != null) batch.begin();
        for (int i = 0; i < alphas.length; i++) this.textRenderer.draw("Batched alpha", x, y + 80 + i * 12, alphas[i] << 24 | 0xFFFFFF);
        if (batch != null) batch.draw();

        this.textRenderer.draw("Leak: §cred", x, y + 120, 0xFFFFFFFF);
        this.minecraft.getTextureManager().bind(ICONS);
        this.drawTexture(x + 60, y + 120, 16, 0, 9, 9);
    }

    private void renderHudBatchTest(int x, int y) {
        String[] lines = {
                "Cached ASCII",
                "Formatted: §aGreen §lBold",
                "Decorated: §nUnderline §mStrike",
                "Unicode: Ελληνικά 日本語"
        };

        if (this.variant.equals("vanilla")) {
            for (int i = 0; i < lines.length; i++) {
                fill(x - 2, y + i * 12 - 2, x + 250, y + i * 12 + 10, 0x80000000);
                this.textRenderer.draw(lines[i], x, y + i * 12, 0xFFFFFFFF);
            }
            return;
        }

        if (this.textBatch == null) this.textBatch = HudBatch.text(this.textRenderer, this.backgroundBatch);
        this.textBatch.begin();
        for (int i = 0; i < lines.length; i++) {
            this.backgroundBatch.fill(x - 2, y + i * 12 - 2, x + 250, y + i * 12 + 10, 0x80000000);
            this.textRenderer.draw(lines[i], x, y + i * 12, 0xFFFFFFFF);
        }
        this.textBatch.draw();
    }

    @Override
    public void tick() {
        this.ticks++;
        if (this.ticks == 5) {
            this.takeScreenshot("before-reload");
        } else if (this.ticks == 6) {
            this.minecraft.reloadResources();
        } else if (this.ticks == 12) {
            this.takeScreenshot("after-reload");
        } else if (this.ticks == 13) {
            this.minecraft.stop();
        }
    }

    private void takeScreenshot(String stage) {
        Screenshot.take(this.minecraft.gameDir, "font-" + this.variant + "-" + stage + ".png",
                this.minecraft.width, this.minecraft.height, this.minecraft.getRenderTarget()
        );
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
