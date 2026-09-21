package dev.rdh.argentum.extras.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import dev.rdh.argentum.extras.ArgentumExtras;

import net.minecraft.client.gui.screen.inventory.menu.AnvilScreen;
import net.minecraft.client.render.TextRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilScreen.class)
public class AnvilScreenMixin {
	// why does this roll its own shadows? we will never know
	@WrapWithCondition(
			method = "renderLabels",
			at = {
					@At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I"),
					@At(value = "INVOKE", ordinal = 2, target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I"),
					@At(value = "INVOKE", ordinal = 3, target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I")
			}
	)
	private boolean argentumExtras$skipAnvilCostShadow(TextRenderer renderer, String text, int x, int y, int color) {
		return !ArgentumExtras.CONFIG.disableTextShadows;
	}
}
