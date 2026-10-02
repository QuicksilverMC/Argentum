package dev.rdh.argentum.impl;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.rdh.argentum.api.config.JsonOptionStorage;
import dev.rdh.argentum.impl.config.ArgentumConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Argentum implements ClientModInitializer {
    public static final String ID = "argentum";
    public static String VERSION;
    public static ArgentumConfig CONFIG;
    public static JsonOptionStorage<ArgentumConfig> CONFIG_STORAGE;

    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    @Override
    public void onInitializeClient() {
        try {
            Class.forName("org.lwjgl.system.Configuration");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Argentum requires LWJGL 3; install Pylon to continue");
        }
        FabricLoader loader = FabricLoader.getInstance();
        VERSION = loader.getModContainer(ID).orElseThrow().getMetadata().getVersion().toString();
        CONFIG_STORAGE = JsonOptionStorage.load(getConfigPath(loader.getConfigDir()), ArgentumConfig.class, ArgentumConfig::new, ArgentumConfig::validate);
        CONFIG = CONFIG_STORAGE.getData();

        LOGGER.info("Argentum v{}", VERSION);
    }

    public static boolean renderAheadSupported() {
        GLCapabilities caps = GL.getCapabilities();
        return caps.OpenGL32 || caps.GL_ARB_sync;
    }

    private Path getConfigPath(Path c) {
        Path celery = c.resolve("celeritas.json");
        Path argentum = c.resolve("argentum.json");
        if (Files.exists(celery)) {
            if (!Files.exists(argentum)) {
                try {
                    Files.move(celery, argentum);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                try {
                    Files.delete(celery);
                } catch (IOException e) {
					throw new UncheckedIOException(e);
				}
            }
        }
        return argentum;
    }
}
