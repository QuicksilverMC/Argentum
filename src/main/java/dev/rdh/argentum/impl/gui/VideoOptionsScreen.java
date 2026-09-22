package dev.rdh.argentum.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.embeddedt.embeddium.impl.gui.CeleritasVideoOptionsController;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.taumc.celeritas.api.options.structure.OptionFlag;

import java.util.Set;

public final class VideoOptionsScreen extends Screen {
    private final Screen parent;
    private final LegacyDrawContext context = new LegacyDrawContext();
    private final CeleritasVideoOptionsController controller;
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    public VideoOptionsScreen(Screen parent) {
        this.parent = parent;
        this.controller = new CeleritasVideoOptionsController(() -> this.minecraft.openScreen(this.parent), ArgentumOptionPages.create(), this.context) {
            @Override
            protected void applyFlagSideEffects(Set<OptionFlag> flags) {
                super.applyFlagSideEffects(flags);
                Minecraft minecraft = Minecraft.getInstance();
                if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
                    minecraft.worldRenderer.reload();
                }
                if (flags.contains(OptionFlag.REQUIRES_RENDERER_UPDATE)) {
                    minecraft.worldRenderer.onViewChanged();
                }
                if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
                    minecraft.reloadResources();
                }
                if (flags.contains(OptionFlag.REQUIRES_GAME_RESTART)) {
                    minecraft.openScreen(new RestartRequiredScreen(VideoOptionsScreen.this));
                }
            }
        };
    }

    @Override
    public void init() {
        this.controller.init(this.width, this.height);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.controller.render(this.context, mouseX, mouseY, tickDelta);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        this.controller.getFrame().mouseClicked(LegacyInteractionContext.INSTANCE, mouseX, mouseY, button);
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        this.controller.getFrame().mouseReleased(LegacyInteractionContext.INSTANCE, mouseX, mouseY, button);
        this.lastMouseX = -1;
        this.lastMouseY = -1;
    }

    @Override
    protected void mouseDragged(int mouseX, int mouseY, int button, long elapsed) {
        if (this.lastMouseX >= 0) {
            this.controller.getFrame().mouseDragged(LegacyInteractionContext.INSTANCE, mouseX, mouseY, button, mouseX - this.lastMouseX, mouseY - this.lastMouseY);
        }
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
    }

    @Override
    protected void keyPressed(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_P && isShiftDown()) {
            this.minecraft.openScreen(new net.minecraft.client.gui.screen.VideoOptionsScreen(this, this.minecraft.options));
            return;
        }
        super.keyPressed(character, keyCode);
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.minecraft.width;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.minecraft.height - 1;
            this.controller.getFrame().mouseScrolled(LegacyInteractionContext.INSTANCE, mouseX, mouseY, 0, wheel > 0 ? 1.0D : -1.0D);
        }
    }
}
