package dev.rdh.cera.modules.shaders;

import com.ventooth.swansong.EnvInfo;
import com.ventooth.swansong.resources.ShaderPackManager;
import dev.rdh.cera.Cera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ListWidget;
import net.minecraft.client.resource.language.I18n;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ShaderPacksScreen extends Screen {
    private static final int BUTTON_ID_FOLDER = 201;
    private static final int BUTTON_ID_DONE = 202;

    private final Screen parent;
    private int updateTimer = -1;
    private PackList packs;

    public ShaderPacksScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        packs = new PackList(width);
        var buttonWidth = Math.min(150, width / 2 - 10);
        buttons.add(new ButtonWidget(BUTTON_ID_FOLDER,
                                     width / 2 - buttonWidth - 5,
                                     height - 25,
                                     buttonWidth,
                                     20,
                                     I18n.translate("cera.shaders.folder")));
        buttons.add(new ButtonWidget(BUTTON_ID_DONE, width / 2 + 5, height - 25, buttonWidth, 20, I18n.translate("gui.done")));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) {
            return;
        }
        switch (button.id) {
            case BUTTON_ID_FOLDER -> openShaderPacksDir();
            case BUTTON_ID_DONE -> minecraft.openScreen(parent);
            default -> packs.buttonClicked(button);
        }
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        packs.handleMouse();
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        renderBackground();
        packs.render(mouseX, mouseY, tickDelta);
        drawCenteredString(textRenderer, I18n.translate("cera.options.pages.shader_packs"), width / 2, 15, 0xFFFFFF);
        var env = EnvInfo.get();
        var info = "OpenGL: " + env.glVersionStr + ", " + env.glVendorStr + ", " + env.glRendererStr;
        if (textRenderer.getWidth(info) < width - 5) {
            drawCenteredString(textRenderer, info, width / 2, height - 40, 0x808080);
        } else {
            drawString(textRenderer, info, 5, height - 40, 0x808080);
        }
        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    public void tick() {
        if (--updateTimer <= 0) {
            packs.update();
            updateTimer += 20;
        }
    }

    private static void openShaderPacksDir() {
        var dir = Minecraft.getInstance().cera$getShaderEngine().shaderPacks().getShaderPacksDir()
                                .toAbsolutePath()
                                .toString();
        var os = System.getProperty("os.name")
                       .toLowerCase(Locale.ROOT);
        var command = os.contains("mac") ? new String[]{"open", dir}
                                         : os.contains("win") ? new String[]{"explorer", dir}
                                                              : new String[]{"xdg-open", dir};
        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            Cera.LOGGER.error("Failed to open shaderpacks directory", e);
        }
    }

    private final class PackList extends ListWidget {
        private List<String> names;
        private int selectedIndex;

        PackList(int width) {
            super(ShaderPacksScreen.this.minecraft, width, ShaderPacksScreen.this.height, 30, ShaderPacksScreen.this.height - 50, 18);
            update();
            var selectedY = selectedIndex * entryHeight;
            var middle = (maxY - minY) / 2;
            if (selectedY > middle) {
                scroll(selectedY - middle);
            }
            setScrollButtonIds(7, 8);
        }

        void update() {
            var manager = Minecraft.getInstance().cera$getShaderEngine().shaderPacks();
            manager.refreshShaderPackNames();
            names = manager.getShaderPackNames();
            selectedIndex = 0;
            var current = manager.getCurrentShaderPackName();
            for (int i = 0; i < names.size(); i++) {
                if (Objects.equals(names.get(i), current)) {
                    selectedIndex = i;
                    break;
                }
            }
        }

        @Override
        public int getRowWidth() {
            return width - 20;
        }

        @Override
        protected int size() {
            return names.size();
        }

        @Override
        protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            if (index != selectedIndex) {
                selectedIndex = index;
                Minecraft.getInstance().cera$getShaderEngine().shaderPacks().setShaderPackByName(names.get(index));
            }
        }

        @Override
        protected boolean isEntrySelected(int index) {
            return index == selectedIndex;
        }

        @Override
        protected int getScrollbarPosition() {
            return width - 6;
        }

        @Override
        protected int getHeight() {
            return size() * 18;
        }

        @Override
        protected void renderBackground() {
        }

        @Override
        protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
            var label = names.get(index);
            int color;
            if (label.equals(ShaderPackManager.DISABLED_SHADER_PACK_NAME)) {
                label = I18n.translate("options.off");
                color = 0xE04040;
            } else if (Minecraft.getInstance().cera$getShaderEngine().shaderPacks().isReferencePack(label)) {
                label = I18n.translate("cera.shaders.default");
                color = 0xA0A0A0;
            } else {
                color = 0xE0E0E0;
            }
            drawCenteredString(textRenderer, label, width / 2, y + 1, color);
        }
    }
}
