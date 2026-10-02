package dev.rdh.argentum.api.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.celeritas.api.options.structure.OptionStorage;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class JsonOptionStorage<T> implements OptionStorage<T> {
    private static final Logger LOGGER = LogManager.getLogger(JsonOptionStorage.class);
    private final Gson gson;

    private final Path path;
    private final Consumer<T> validator;
    private final T data;

    private JsonOptionStorage(Path path, Gson gson, Consumer<T> validator, T data) {
        this.path = path;
        this.validator = validator;
        this.gson = gson;
        this.data = data;
    }

    public static <T> JsonOptionStorage<T> load(Path path, Class<T> type, UnaryOperator<GsonBuilder> gsonConfig, Supplier<T> defaults, Consumer<T> validator) {
        Gson gson = gsonConfig.apply(new GsonBuilder()).create();
        T data = defaults.get();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                data = gson.fromJson(reader, type);
                if (data == null) {
                    throw new IllegalArgumentException("Configuration is empty");
                }
            } catch (Exception e) {
                LOGGER.error("Could not load configuration from {}", path, e);
                try {
                    Files.move(path, path.resolveSibling(path.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveException) {
                    LOGGER.error("Could not move the broken configuration aside", moveException);
                }
            }
        }

        JsonOptionStorage<T> storage = new JsonOptionStorage<>(path, gson, validator, data);
        storage.save();
        return storage;
    }

    public static <T> JsonOptionStorage<T> load(Path path, Class<T> type, Supplier<T> defaults, Consumer<T> validator) {
        return load(path, type, b -> b
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .setPrettyPrinting(), defaults, validator);
    }

    @Override
    public T getData() {
        return this.data;
    }

    @Override
    public void save() {
        this.validator.accept(this.data);
        try {
            Path dir = this.path.getParent();

            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            } else if (!Files.isDirectory(dir)) {
                throw new IOException("Not a directory: " + dir);
            }

            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");

            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(this.data, writer);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOGGER.error("Could not save configuration to {}", this.path, e);
        }
    }
}
