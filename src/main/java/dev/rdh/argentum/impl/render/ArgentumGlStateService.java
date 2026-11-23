package dev.rdh.argentum.impl.render;

import org.embeddedt.embeddium.impl.gl.state.GlStateService;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;

public enum ArgentumGlStateService implements GlStateService {
	INSTANCE;

	private static final int CACHED_TEXTURE_UNITS = 8;

	@Override
	public int activateTextureUnit(int unit) {
		int previous = GLX.GL_TEXTURE0 + GlStateManager.texture;
		GlStateManager.activeTexture(unit);
		return previous;
	}

	@Override
	public void bindTexture(int unit, int target, int texture) {
		int previous = this.activateTextureUnit(GLX.GL_TEXTURE0 + unit);

		if (target == GL11.GL_TEXTURE_2D && unit < CACHED_TEXTURE_UNITS) {
			GlStateManager.bindTexture(texture);
		} else {
			GL11.glBindTexture(target, texture);
		}

		this.activateTextureUnit(previous);
	}
}
