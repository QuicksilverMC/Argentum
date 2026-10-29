package dev.rdh.argentum.test;

import dev.rdh.argentum.impl.Argentum;
import net.fabricmc.api.ClientModInitializer;
import net.ornithemc.osl.lifecycle.api.client.MinecraftClientEvents;

public class ArgentumTestMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MinecraftClientEvents.READY.register(minecraft -> {
            if (System.getProperty("argentum.portalBenchmarkVariant") != null) {
                PortalBenchmark.start(minecraft);
            } else if (System.getProperty("argentum.itemTestVariant") != null) {
                Argentum.CONFIG.guiItemAtlas = "atlas".equals(System.getProperty("argentum.itemTestVariant"));
                minecraft.openScreen(new GuiItemVisualTestScreen());
            } else if (System.getProperty("argentum.fontBenchmark") != null) {
                Argentum.CONFIG.fontBatching = !"vanilla".equals(System.getProperty("argentum.fontBenchmark"));
                minecraft.openScreen(new FontBenchmarkScreen());
            } else if (Boolean.getBoolean("argentum.guiBenchmark")) {
                Argentum.CONFIG.fontBatching = true;
                minecraft.openScreen(new GuiBatchBenchmarkScreen());
            } else {
                Argentum.CONFIG.fontBatching = !Boolean.getBoolean("argentum.disableFontBatching");
                minecraft.openScreen(new FontVisualTestScreen());
            }
        });
    }
}
