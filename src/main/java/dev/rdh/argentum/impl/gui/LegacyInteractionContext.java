package dev.rdh.argentum.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.resource.Identifier;
import org.embeddedt.embeddium.impl.gui.framework.InteractionContext;

enum LegacyInteractionContext implements InteractionContext {
    INSTANCE;

    private static final Identifier BUTTON_SOUND = new Identifier("gui.button.press");

    @Override
    public boolean isSpecialKeyDown(SpecialKey key) {
        return switch (key) {
            case SHIFT -> Screen.isShiftDown();
            case CTRL -> Screen.isControlDown();
            case ALT -> Screen.isAltDown();
        };
    }

    @Override
    public String getClipboard() {
        return Screen.getClipboard();
    }

    @Override
    public void setClipboard(String text) {
        Screen.setClipboard(text);
    }

    @Override
    public void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.of(BUTTON_SOUND, 1.0F));
    }
}
