package dev.rdh.argentum.impl.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;

public class RestartRequiredScreen extends Screen {
	private final Screen parent;

	public RestartRequiredScreen(Screen parent) {
		this.parent = parent;
	}

	@Override
	public void init() {
		this.buttons.clear();
		this.buttons.add(new ButtonWidget(0, this.width / 2 - 155, this.height / 4 + 96, 150, 20, I18n.translate("menu.quit")));
		this.buttons.add(new ButtonWidget(1, this.width / 2 - 155 + 160, this.height / 4 + 96, 150, 20, I18n.translate("argentum.gui.restart_required.skip")));
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (button.id == 0) {
			this.minecraft.shutdown();
		} else if (button.id == 1) {
			this.minecraft.openScreen(this.parent);
		}
	}

	@Override
	public void render(int mouseX, int mouseY, float tickDelta) {
		this.renderBackground();
		this.drawCenteredString(this.textRenderer, I18n.translate("argentum.gui.restart_required.title"), this.width / 2, this.height / 4 - 30, 0xFFFFFF);
		this.drawCenteredString(this.textRenderer, I18n.translate("argentum.gui.restart_required.message"), this.width / 2, this.height / 4 - 10, 0xA0A0A0);
		super.render(mouseX, mouseY, tickDelta);
	}
}
