package dev.rdh.argentum.extras;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import dev.rdh.argentum.impl.config.JsonOptionStorage;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.lwjgl.sdl.SDLHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.celeritas.api.OptionGUIConstructionEvent;
import pl.tomgirl.pylon.window.DisplaySdl;

public class ArgentumExtras implements ClientModInitializer, PreLaunchEntrypoint {
	public static final Logger LOGGER = LoggerFactory.getLogger("argentum-extras");
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

		if (isPylonLoaded()) {
			DisplaySdl d = DisplaySdl.instance();
			d.setHighPixelDensity(CONFIG.highDpiScreen);
			d.setWindowHint(SDLHints.SDL_HINT_MAC_SCROLL_MOMENTUM, CONFIG.macosSmoothScrolling ? "1" : "0");
			d.setWindowHint(SDLHints.SDL_HINT_MAC_CTRL_CLICK_EMULATE_RIGHT_CLICK, CONFIG.macosRightClickEmulation ? "1" : "0");
		} else if (isLegacyLwjgl3Loaded()) {
			System.getProperties().putIfAbsent("legacy_lwjgl3.scale_framebuffer", Boolean.toString(CONFIG.highDpiScreen));
			if (usesLegacySdl()) {
				SDLHints.SDL_SetHint(SDLHints.SDL_HINT_MAC_SCROLL_MOMENTUM, CONFIG.macosSmoothScrolling ? "1" : "0");
				SDLHints.SDL_SetHint(SDLHints.SDL_HINT_MAC_CTRL_CLICK_EMULATE_RIGHT_CLICK, CONFIG.macosRightClickEmulation ? "1" : "0");
			}
		}
	}

	static boolean isPylonLoaded() {
		return FabricLoader.getInstance().isModLoaded("pylon");
	}

	static boolean isLegacyLwjgl3Loaded() {
		return FabricLoader.getInstance().isModLoaded("legacy-lwjgl3");
	}

	static boolean usesLegacySdl() {
		try {
			return Class.forName("io.github.moehreag.legacylwjgl3.LegacyLWJGL3").getField("USE_SDL").getBoolean(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
