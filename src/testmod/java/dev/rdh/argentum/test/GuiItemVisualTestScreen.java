package dev.rdh.argentum.test;

import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GuiItemVisualTestScreen extends Screen {
    private static final int COLUMNS = 16;
    private static final int CELL = 20;
    private static final int ORIGIN_X = 12;
    private static final int ORIGIN_Y = 12;
    private static final int BLOCK_COUNT = 48;
    private static final int ITEM_COUNT = 96;
    private static final int[] DYES = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00};

    private final String variant = System.getProperty("argentum.itemTestVariant", "atlas");
    private final boolean glint = Boolean.getBoolean("argentum.itemTestGlint");
    private final List<ItemStack> stacks = new ArrayList<>();
    private int ticks;
    private long renderNanos;
    private int renderFrames;

    private void collect() {
        for (int id = 1; id < 512 && this.stacks.size() < BLOCK_COUNT; id++) {
            Item item = Item.byId(id);
            if (item != null) this.stacks.add(new ItemStack(item, 1, 0));
        }

        for (int id = 1; id < 512 && this.stacks.size() < ITEM_COUNT; id++) {
            Item item = Item.byId(id);
            if (item == null || item.getMaxDamage() <= 0) continue;

            // no enchanted stacks here: the glint scrolls on wall-clock time, so two separate client
            this.stacks.add(new ItemStack(item, 1, item.getMaxDamage() / 2));
        }

        for (int color : DYES) {
            ItemStack stack = new ItemStack(Item.byId(298), 1, 0);
            ((ArmorItem) stack.getItem()).setColor(stack, color);
            this.stacks.add(stack);
        }

        if (this.glint) this.stacks.forEach(stack -> stack.addEnchantment(Enchantment.SHARPNESS, 1));
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        long start = this.glint ? System.nanoTime() : 0;
        if (this.stacks.isEmpty()) this.collect();

        fill(0, 0, this.width, this.height, 0xFF202020);

        this.itemRenderer.zOffset = 0.0F;
        Lighting.turnOnGui();

        for (int i = 0; i < this.stacks.size(); i++) {
            int x = ORIGIN_X + (i % COLUMNS) * CELL;
            int y = ORIGIN_Y + (i / COLUMNS) * CELL;
            this.itemRenderer.renderGuiItem(this.stacks.get(i), x, y);
            this.itemRenderer.renderGuiItemDecoration(this.textRenderer, this.stacks.get(i), x, y);
        }

        Lighting.turnOff();
        if (this.glint && this.ticks >= 20) {
            this.renderNanos += System.nanoTime() - start;
            this.renderFrames++;
        }
    }

    @Override
    public void tick() {
        this.ticks++;
        if (this.glint && this.ticks == 140) {
            System.out.printf("GUI glint %s: %.3f ms/frame%n", this.variant,
                    this.renderNanos / (double) this.renderFrames / 1_000_000.0);
            this.minecraft.stop();
            return;
        }
        if (!this.glint && this.ticks == 5) {
            this.takeScreenshot("first");
        } else if (!this.glint && this.ticks == 15) {
            this.takeScreenshot("second");
        } else if (!this.glint && this.ticks == 16) {
            this.minecraft.stop();
        }
    }

    private void takeScreenshot(String stage) {
        Screenshot.take(this.minecraft.gameDir, "item-" + this.variant + "-" + stage + ".png",
                this.minecraft.width, this.minecraft.height, this.minecraft.getRenderTarget()
        );
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
