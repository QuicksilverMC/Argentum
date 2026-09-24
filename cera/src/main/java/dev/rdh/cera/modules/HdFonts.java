package dev.rdh.cera.modules;

import dev.rdh.cera.Cera;
import dev.rdh.cera.props.Props;

import dev.rdh.argentum.impl.render.gui.TextBatcher;
import net.minecraft.resource.Identifier;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;

import java.io.IOException;

public final class HdFonts {
    private static final String TEXTURES = "textures/";
    private static final String MCPATCHER = "mcpatcher/";

    public static Identifier resolve(ResourceManager resources, Identifier location) {
        if (!Cera.CONFIG.hdFonts || location == null) return location;

        String path = location.getPath();
        if (!path.startsWith(TEXTURES)) return location;

        Identifier hd = new Identifier(location.getNamespace(), MCPATCHER + path.substring(TEXTURES.length()));
        return resources.getResource(hd).isPresent() ? hd : location;
    }

    public static boolean apply(ResourceManager resources, Identifier fontLocation, TextBatcher batcher) {
        if (!Cera.CONFIG.hdFonts) return false;

        String path = fontLocation.getPath();
        if (!path.endsWith(".png")) return false;

        Identifier location = new Identifier(fontLocation.getNamespace(),
                path.substring(0, path.length() - ".png".length()) + ".properties");
        Resource resource = resources.getResource(location).orElse(null);
        if (resource == null) return false;

        Props props;
        try {
            props = new Props(resource);
        } catch (IOException | RuntimeException e) {
            Cera.LOGGER.warn("[HdFonts] Failed to load {}", location, e);
            return false;
        }

        // an HD font is wider than vanilla's, so the space has to grow with it or words run together
        // MCPatcher derives it from the average glyph width, but width.32 overrides that
        if (props.get("width.32") == null) {
            batcher.setCharWidth(' ', averageWidth(batcher) / 2.0F);
        }

        int overrides = 0;
        for (String key : props.properties().stringPropertyNames()) {
            if (!key.startsWith("width.")) continue;
            String index = key.substring("width.".length());
            try {
                int character = Integer.parseInt(index.trim());
                if (character < 0 || character > 255) {
                    Cera.LOGGER.warn("[HdFonts] {}: character out of range: {}", location, index);
                    continue;
                }
                float width = props.getFloat(key, Float.NaN).orElse(Float.NaN);
                if (Float.isNaN(width) || width < 0.0F || width > 8.0F) {
                    Cera.LOGGER.warn("[HdFonts] {}: width out of range: {}", location, props.get(key));
                    continue;
                }
                batcher.setCharWidth(character, width);
                overrides++;
            } catch (NumberFormatException _) {
                Cera.LOGGER.warn("[HdFonts] {}: invalid character: {}", location, index);
            }
        }

        // widths are baked into cached glyph geometry, so clear the cache after applying overrides
        batcher.clearCaches();
        Cera.LOGGER.info("[HdFonts] {}: {} width overrides", location, overrides);
        return props.getBoolean("blend", false).orElse(false);
    }

    private static float averageWidth(TextBatcher batcher) {
        float total = 0.0F;
        int count = 0;
        for (char c = 'A'; c <= 'Z'; c++) { total += batcher.getCharWidth(c); count++; }
        for (char c = 'a'; c <= 'z'; c++) { total += batcher.getCharWidth(c); count++; }
        for (char c = '0'; c <= '9'; c++) { total += batcher.getCharWidth(c); count++; }
        return count == 0 ? 0.0F : total / count;
    }

    private HdFonts() {
    }
}
