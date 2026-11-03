package dev.rdh.cera.modules.shaders;

import dev.rdh.cera.Cera;
import com.ventooth.swansong.shader.config.ConfigEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;

import java.util.function.Supplier;

final class ShaderOptionsScreen extends Screen {
    private static final int RESET_BUTTON = 1000;
    private static final int FULL_RESET_BUTTON = 1001;
    private static final int DONE_BUTTON = 1002;
    private static final int APPLY_BUTTON = 1003;
    private static final int CONFIRM_REINIT = 0;
    private static final int CONFIRM_ESCAPE = 1;

    private final Supplier<? extends Screen> parent;
    private final ConfigEntry.RootScreen rootScreen;
    private final ConfigEntry.Screen configScreen;
    private ButtonWidget applyButton;

    ShaderOptionsScreen(Screen parent, ConfigEntry.RootScreen configScreen) {
        this(() -> parent, configScreen, configScreen);
    }

    private ShaderOptionsScreen(Supplier<? extends Screen> parent, ConfigEntry.RootScreen root, ConfigEntry.Screen configScreen) {
        this.parent = parent;
        this.rootScreen = root;
        this.configScreen = configScreen;
    }

    @Override
    public void init() {
        var entries = configScreen.entries();
        var columns = Math.max(configScreen.columnsHint(), (int) Math.ceil(entries.size() / 9D));
        var colWidth = Math.min(width / columns, 200);
        var baseX = (width - colWidth * columns) / 2;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry == null) {
                continue;
            }
            var x = (i % columns) * colWidth + 5 + baseX;
            var y = 30 + (i / columns) * 20;
            var w = colWidth - 10;
            var id = 100 + i;
            ButtonWidget button;
            if (entry instanceof ConfigEntry.Screen screen) {
                button = new EntryButton(id, x, y, w, screen, screen.optionName());
            } else if (entry instanceof ConfigEntry.Fixed fixed) {
                button = new EntryButton(id, x, y, w, fixed, fixed.optionName() + ": " + fixed.valueName());
            } else if (entry instanceof ConfigEntry.Switchable || entry instanceof ConfigEntry.Toggleable) {
                button = new EntryButton(id, x, y, w, entry, label(entry));
            } else if (entry instanceof ConfigEntry.Draggable draggable) {
                button = new SliderButton(id, x, y, w, draggable);
            } else {
                Cera.LOGGER.error("Unknown config entry class: {}", entry.getClass().getName());
                continue;
            }
            buttons.add(button);
        }

        var bottom = height / 6 + 168 + 11;
        var subWidth = 75;
        if (configScreen == rootScreen) {
            buttons.add(new ButtonWidget(RESET_BUTTON, width / 2 - 170, bottom, subWidth, 20, I18n.translate("controls.reset")));
            buttons.add(new ButtonWidget(FULL_RESET_BUTTON, width / 2 - 85, bottom, subWidth, 20, I18n.translate("cera.shaders.reset_all")));
        } else {
            buttons.add(new ButtonWidget(RESET_BUTTON, width / 2 - 170, bottom, 160, 20, I18n.translate("controls.reset")));
        }
        applyButton = new ButtonWidget(APPLY_BUTTON, width / 2 + 10, bottom, subWidth, 20, I18n.translate("cera.shaders.apply"));
        applyButton.active = rootScreen.isModified();
        buttons.add(applyButton);
        buttons.add(new ButtonWidget(DONE_BUTTON, width / 2 + 95, bottom, subWidth, 20, I18n.translate("gui.done")));
    }

    private static String label(ConfigEntry entry) {
        var mutable = (ConfigEntry.Mutable) entry;
        var value = entry instanceof ConfigEntry.Toggleable toggleable ? toggleable.getValue() ? "ON" : "OFF" : entry.valueName();
        return entry.optionName() + ": " + color(mutable) + value;
    }

    private static String color(ConfigEntry.Mutable entry) {
        return entry.isDefault() ? entry.isModified() ? "§e" : "" : entry.isModified() ? "§a" : "§b";
    }

    private ShaderOptionsScreen copy(ConfigEntry.Screen screen) {
        return new ShaderOptionsScreen(parent, rootScreen, screen);
    }

    private void save() {
        rootScreen.save();
        Minecraft.getInstance().cera$getShaderEngine().shaderPacks().saveShaderSettings();
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) {
            return;
        }
        if (button instanceof EntryButton entry) {
            switch (entry.entry) {
                case ConfigEntry.Screen screen -> minecraft.openScreen(new ShaderOptionsScreen(() -> copy(configScreen), rootScreen, screen));
                case ConfigEntry.Switchable switchable -> switchable.nextValue();
                case ConfigEntry.Toggleable toggleable -> toggleable.toggle();
                default -> {
                }
            }
            if (entry.entry instanceof ConfigEntry.Mutable) {
                entry.message = label(entry.entry);
            }
            applyButton.active = rootScreen.isModified();
            return;
        }
        switch (button.id) {
            case FULL_RESET_BUTTON -> minecraft.openScreen(
                    new ConfirmScreen(this, I18n.translate("cera.shaders.reset_all.query"), "", CONFIRM_REINIT)
            );
            case RESET_BUTTON -> {
                configScreen.reset();
                minecraft.openScreen(copy(configScreen));
            }
            case APPLY_BUTTON -> {
                if (rootScreen.isModified()) {
                    save();
                    minecraft.openScreen(copy(configScreen));
                }
            }
            case DONE_BUTTON -> {
                if (configScreen == rootScreen && rootScreen.isModified()) {
                    save();
                }
                minecraft.openScreen(parent.get());
            }
            default -> {
            }
        }
    }

    @Override
    public void confirmResult(boolean result, int id) {
        if (id == CONFIRM_REINIT) {
            if (result) {
                rootScreen.fullReset();
                save();
            }
            minecraft.openScreen(copy(configScreen));
        } else if (id == CONFIRM_ESCAPE) {
            if (result) {
                save();
            } else {
                rootScreen.cancel();
            }
            minecraft.openScreen(parent.get());
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == 1) {
            if (configScreen == rootScreen && rootScreen.isModified()) {
                minecraft.openScreen(new ConfirmScreen(this, I18n.translate("cera.shaders.exit.query"), "", CONFIRM_ESCAPE));
            } else {
                minecraft.openScreen(parent.get());
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        renderBackground();
        drawCenteredString(textRenderer, configScreen.screenTitle(), width / 2, 15, 0xFFFFFF);
        super.render(mouseX, mouseY, tickDelta);
        for (var button : buttons) {
            if (button instanceof EntryButton entry && button.isHovered()) {
                renderTooltip(entry.entry.description(), mouseX, mouseY);
            }
        }
    }

    private static class EntryButton extends ButtonWidget {
        final ConfigEntry entry;

        EntryButton(int id, int x, int y, int width, ConfigEntry entry, String message) {
            super(id, x, y, width, 20, message);
            this.entry = entry;
        }
    }

    private static final class SliderButton extends EntryButton {
        private final ConfigEntry.Draggable draggable;
        private float sliderPos;
        private boolean dragging;

        SliderButton(int id, int x, int y, int width, ConfigEntry.Draggable draggable) {
            super(id, x, y, width, draggable, label(draggable));
            this.draggable = draggable;
            this.sliderPos = draggable.getValue();
        }

        @Override
        public boolean mouseClicked(Minecraft minecraft, int mouseX, int mouseY) {
            if (super.mouseClicked(minecraft, mouseX, mouseY)) {
                dragging = true;
                updatePos(mouseX);
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            dragging = false;
        }

        @Override
        protected int getYImage(boolean hovered) {
            return 0;
        }

        @Override
        protected void renderBackground(Minecraft minecraft, int mouseX, int mouseY) {
            if (visible) {
                if (dragging) {
                    updatePos(mouseX);
                }
                minecraft.getTextureManager().bind(WIDGETS_LOCATION);
                GlStateManager.color4f(1F, 1F, 1F, 1F);
                var offset = x + (int) (sliderPos * (width - 8F));
                drawTexture(offset, y, 0, 66, 4, 20);
                drawTexture(offset + 4, y, 196, 66, 4, 20);
            }
        }

        private void updatePos(int mouseX) {
            sliderPos = draggable.setValue((mouseX - (x + 4F)) / (width - 8F));
            message = label(draggable);
        }
    }
}
