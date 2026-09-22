package dev.rdh.argentum.extras;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import dev.rdh.argentum.impl.config.JsonOptionStorage;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.lwjgl.sdl.SDLHints;
import org.taumc.celeritas.api.OptionGUIConstructionEvent;
import pl.tomgirl.lenis.window.DisplaySdl;

public class ArgentumExtras implements ClientModInitializer, PreLaunchEntrypoint {
    public static ArgentumExtrasConfig CONFIG;
    static JsonOptionStorage<ArgentumExtrasConfig> CONFIG_STORAGE;

	@Override
	public void onInitializeClient() {
        OptionGUIConstructionEvent.BUS.addListener(event -> ArgentumExtrasOptionPage.create().forEach(event::addPage));
	}

	@Override
	public void onPreLaunch() {
		CONFIG_STORAGE = JsonOptionStorage.load(FabricLoader.getInstance().getConfigDir().resolve("argentum-extras.json"),
				ArgentumExtrasConfig.class, ArgentumExtrasConfig::new, ArgentumExtrasConfig::validate
		);
		CONFIG = CONFIG_STORAGE.getData();

		DisplaySdl d = DisplaySdl.instance();
		d.setHighPixelDensity(CONFIG.highDpiScreen);
		d.setWindowHint(SDLHints.SDL_HINT_MAC_SCROLL_MOMENTUM, CONFIG.macosSmoothScrolling ? "1" : "0");
		d.setWindowHint(SDLHints.SDL_HINT_MAC_CTRL_CLICK_EMULATE_RIGHT_CLICK, CONFIG.macosRightClickEmulation ? "1" : "0");
	}
}
