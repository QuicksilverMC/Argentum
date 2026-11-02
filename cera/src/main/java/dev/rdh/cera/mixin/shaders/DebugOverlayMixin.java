package dev.rdh.cera.mixin.shaders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.overlay.DebugOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugOverlay.class)
public abstract class DebugOverlayMixin {
    @Inject(method = "getSystemInfo", at = @At("RETURN"))
    private void cera$info(CallbackInfoReturnable<List<String>> cir) {
        var lines = cir.getReturnValue();
        lines.add("");
        lines.add("§bCera shaders");
        var engine = Minecraft.getInstance().cera$getShaderEngine();
        if (!engine.isInitializedImpl()) {
            lines.add("§4Shaders disabled");
            return;
        }
        lines.add("§bPack §9" + engine.shaderPacks().getCurrentShaderPackName());
        lines.add("§bShadows " + (engine.shadowPassExists() ? "§aEnabled" : "§4Disabled"));
        lines.add("§bShader switches: §r" + engine.prevFrameShaderSwitches());
        if (engine.doGraphLog()) {
            lines.add("Graph log:");
            for (var node : engine.graphLog()) {
                lines.add(node.name());
            }
        }
    }
}
