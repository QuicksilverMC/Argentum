package dev.rdh.argentum.test;

import dev.rdh.argentum.impl.Argentum;
import net.fabricmc.api.ClientModInitializer;
import net.ornithemc.osl.lifecycle.api.client.MinecraftClientEvents;

public class ArgentumTestMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MinecraftClientEvents.READY.register(minecraft -> {
            if (System.getProperty("argentum.itemTestVariant") != null) {
                Argentum.CONFIG.guiItemAtlas = "atlas".equals(System.getProperty("argentum.itemTestVariant"));
                minecraft.openScreen(new GuiItemVisualTestScreen());
            } else if (Boolean.getBoolean("argentum.guiBenchmark")) {
                Argentum.CONFIG.fontBatching = true;
                minecraft.openScreen(new GuiBatchBenchmarkScreen());
            } else {
                minecraft.openScreen(new FontVisualTestScreen());
            }
        });
    }
}
