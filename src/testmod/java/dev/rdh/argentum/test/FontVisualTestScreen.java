package dev.rdh.argentum.test;

import dev.rdh.argentum.impl.render.hud.HudBatch;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;

public class FontVisualTestScreen extends Screen {
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
