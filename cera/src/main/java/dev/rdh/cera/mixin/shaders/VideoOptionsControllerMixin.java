package dev.rdh.cera.mixin.shaders;

import dev.rdh.cera.modules.shaders.ShaderPacksScreen;
import net.minecraft.client.Minecraft;
import org.embeddedt.embeddium.impl.gui.CeleritasVideoOptionsController;
import org.embeddedt.embeddium.impl.gui.frame.tab.Tab;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.api.options.OptionIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(value = CeleritasVideoOptionsController.class, remap = false)
abstract class VideoOptionsControllerMixin {
    @Inject(method = "createExtraTabs", at = @At("TAIL"))
    private void cera$addShaderPacksTab(Map<String, List<Tab<?>>> tabs, CallbackInfo ci) {
        OptionIdentifier<Void> id = OptionIdentifier.create("cera", "shader_packs");
        tabs.computeIfAbsent(id.getModId(), _ -> new ArrayList<>()).add(Tab.createBuilder()
                .setTitle(TextComponent.translatable("cera.options.pages.shader_packs"))
                .setId(id)
                .setOnSelectFunction(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.openScreen(new ShaderPacksScreen(minecraft.screen));
                    return false;
                })
                .build());
    }
}
