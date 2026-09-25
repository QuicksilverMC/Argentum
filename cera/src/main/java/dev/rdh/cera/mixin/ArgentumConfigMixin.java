package dev.rdh.cera.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.rdh.argentum.impl.config.ArgentumConfig;
import dev.rdh.cera.Cera;

@Mixin(value = ArgentumConfig.class, remap = false)
public class ArgentumConfigMixin {
	@Shadow public boolean fontBatching;

	@Inject(method = "validate", at = @At("TAIL"))
	private void cera$forceTextBatcherOn(CallbackInfo ci) {
		if (Cera.CONFIG != null && Cera.CONFIG.hdFonts) {
			this.fontBatching = true;
		}
	}
}
