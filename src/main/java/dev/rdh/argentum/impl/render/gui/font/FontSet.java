package dev.rdh.argentum.impl.render.gui.font;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.render.gui.TextBatcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;

import org.apache.commons.io.IOUtils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

public final class FontSet {
    private static final Identifier DEFINITION = new Identifier("font/default.json");
    private static final Identifier ASCII = new Identifier("textures/font/ascii.png");
    private static final Identifier GLYPH_SIZES = new Identifier("font/glyph_sizes.bin");
    private static final String UNICODE_PAGES = "minecraft:font/unicode_page_%s.png";

    private final float[] widths;
    private final Glyph[][] blocks = new Glyph[0x1100][];
    private Identifier sheet;
    private GlyphAtlas atlas;
    private List<IntFunction<Glyph>> providers;
    private boolean unicode;

    public FontSet(float[] widths) {
        this.widths = widths;
    }

    public void setSheet(Identifier sheet) {
        this.sheet = sheet;
    }

    public Glyph glyph(int codepoint, boolean unicode) {
        this.ensureLoaded(unicode);

        Glyph[] block = this.blocks[codepoint >> 8];
        if (block == null) block = this.blocks[codepoint >> 8] = new Glyph[256];
        Glyph glyph = block[codepoint & 0xFF];
        if (glyph == null) glyph = block[codepoint & 0xFF] = this.resolve(codepoint);
        return glyph;
    }

    public Identifier bake(Glyph glyph) {
        if (glyph.page == null) this.atlas.bake(glyph);
        return glyph.page;
    }

    public Identifier whitePage(Identifier current, boolean unicode) {
        this.ensureLoaded(unicode);
        return this.atlas.whitePage(current);
    }

    public static float whiteUv() {
        return GlyphAtlas.WHITE;
    }

    public void reset() {
        if (this.atlas != null) this.atlas.reset();
        this.atlas = null;
        this.providers = null;
        Arrays.fill(this.blocks, null);
    }

    private Glyph resolve(int codepoint) {
        for (IntFunction<Glyph> provider : this.providers) {
            Glyph glyph = provider.apply(codepoint);
            if (glyph != null) return glyph;
        }
        return Glyph.EMPTY;
    }

    private void ensureLoaded(boolean unicode) {
        if (this.providers == null || unicode != this.unicode) this.load(unicode);
    }

    private void load(boolean unicode) {
        this.reset();
        this.unicode = unicode;
        this.atlas = new GlyphAtlas(this.sheet.getPath());

        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        List<IntFunction<Glyph>> providers = new ObjectArrayList<>();
        boolean defined = false;
        if (this.sheet.getPath().endsWith("font/ascii.png")) {
            List<Resource> stack = List.of();
            try {
                stack = resources.getResources(DEFINITION);
            } catch (IOException _) {
            }
            for (int i = stack.size() - 1; i >= 0; i--) {
                this.loadDefinition(resources, stack.get(i), providers);
                defined = true;
            }
        }

        try {
            if (!defined && !unicode) providers.add(this.bitmap(resources, ASCII, characterGrid(), 8, 7));
            providers.add(legacyUnicode(resources, GLYPH_SIZES, UNICODE_PAGES));
        } catch (IOException e) {
            Argentum.LOGGER.warn("Could not load the built-in font for {}", this.sheet, e);
        }
        this.providers = providers;
    }

    private void loadDefinition(ResourceManager resources, Resource resource, List<IntFunction<Glyph>> providers) {
        JsonObject definition;
        try (Reader reader = new InputStreamReader(resource.asStream(), StandardCharsets.UTF_8)) {
            definition = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            Argentum.LOGGER.warn("Could not read {} from {}", DEFINITION, resource.getSourceName(), e);
            return;
        }

        for (JsonElement element : definition.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            try {
                if (!this.passes(provider.getAsJsonObject("filter"))) continue;
                IntFunction<Glyph> loaded = this.provider(resources, provider);
                if (loaded != null) providers.add(loaded);
            } catch (IOException | RuntimeException e) {
                Argentum.LOGGER.warn("Could not load font provider {} from {}", provider, resource.getSourceName(), e);
            }
        }
    }

    private boolean passes(JsonObject filter) {
        if (filter == null) return true;
        for (Map.Entry<String, JsonElement> option : filter.entrySet()) {
            boolean enabled = option.getKey().equals("uniform") && this.unicode;
            if (option.getValue().getAsBoolean() != enabled) return false;
        }
        return true;
    }

    private IntFunction<Glyph> provider(ResourceManager resources, JsonObject provider) throws IOException {
        String type = provider.get("type").getAsString();
        switch (type) {
            case "bitmap" -> {
                Identifier file = new Identifier(provider.get("file").getAsString());
                int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
                JsonArray rows = provider.getAsJsonArray("chars");
                int[][] grid = new int[rows.size()][];
                for (int i = 0; i < grid.length; i++) grid[i] = rows.get(i).getAsString().codePoints().toArray();
                return this.bitmap(resources, new Identifier(file.getNamespace(), "textures/" + file.getPath()),
                        grid, height, provider.get("ascent").getAsInt());
            }
            case "legacy_unicode" -> {
                return legacyUnicode(resources, new Identifier(provider.get("sizes").getAsString()),
                        provider.get("template").getAsString());
            }
            case "space" -> {
                Int2ObjectMap<Glyph> glyphs = new Int2ObjectOpenHashMap<>();
                for (Map.Entry<String, JsonElement> advance : provider.getAsJsonObject("advances").entrySet()) {
                    glyphs.put(advance.getKey().codePointAt(0), new Glyph(advance.getValue().getAsFloat(), false));
                }
                return glyphs::get;
            }
            default -> {
                Argentum.LOGGER.warn("Unsupported font provider type {}", type);
                return null;
            }
        }
    }

    private IntFunction<Glyph> bitmap(ResourceManager resources, Identifier texture, int[][] grid, int height, int ascent)
            throws IOException {
        boolean rendererSheet = texture.equals(ASCII);
        if (rendererSheet) texture = this.sheet;

        BufferedImage image;
        try (InputStream stream = resources.getResource(texture).asStream()) {
            image = TextureUtil.readImage(stream);
        }
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int[] pixels = image.getRGB(0, 0, imageWidth, imageHeight, null, 0, imageWidth);
        int cellWidth = imageWidth / grid[0].length;
        int cellHeight = imageHeight / grid.length;
        float scale = (float) height / cellHeight;
        Glyph.Sheet sheet = new Glyph.Sheet(pixels, imageWidth);

        Int2ObjectMap<Glyph> glyphs = new Int2ObjectOpenHashMap<>();
        for (int row = 0; row < grid.length; row++) {
            for (int column = 0; column < grid[row].length; column++) {
                int codepoint = grid[row][column];
                if (codepoint == 0) continue;

                int x = column * cellWidth;
                int y = row * cellHeight;
                int used = usedWidth(pixels, imageWidth, x, y, cellWidth, cellHeight);
                int slot = row * grid[row].length + column;
                float advance = rendererSheet && TextBatcher.CHARACTERS.indexOf(codepoint) == slot
                        ? this.widths[slot]
                        : (int) (0.5 + used * scale) + 1;
                glyphs.put(codepoint, new Glyph(advance, advance - 1.01F, 7 - ascent, height, 1.0F / scale, false,
                        used == 0 ? null : sheet, x, y, cellWidth, cellHeight));
            }
        }
        return glyphs::get;
    }

    private static int usedWidth(int[] pixels, int stride, int x, int y, int width, int height) {
        for (int column = width - 1; column >= 0; column--) {
            for (int row = 0; row < height; row++) {
                if ((pixels[(y + row) * stride + x + column] >>> 24) != 0) return column + 1;
            }
        }
        return 0;
    }

    private static IntFunction<Glyph> legacyUnicode(ResourceManager resources, Identifier sizesLocation, String template)
            throws IOException {
        byte[] sizes = new byte[65536];
        try (InputStream stream = resources.getResource(sizesLocation).asStream()) {
            IOUtils.read(stream, sizes);
        }

        Glyph.Sheet[] pages = new Glyph.Sheet[256];
        return codepoint -> {
            if (codepoint > 0xFFFF || sizes[codepoint] == 0) return null;

            int start = sizes[codepoint] >> 4 & 15;
            int columns = (sizes[codepoint] & 15) + 1 - start;
            if (columns <= 0) return null;

            int page = codepoint >> 8;
            if (pages[page] == null) {
                Identifier name = new Identifier(String.format(template, String.format("%02x", page)));
                pages[page] = new Glyph.Sheet(resources, new Identifier(name.getNamespace(), "textures/" + name.getPath()), 256);
            }
            return new Glyph(columns / 2 + 1, (columns - 0.02F) / 2.0F, 0.0F, 8.0F, 2.0F, true,
                    pages[page], (codepoint & 15) * 16 + start, (codepoint >> 4 & 15) * 16, columns, 16);
        };
    }

    private static int[][] characterGrid() {
        int[][] grid = new int[16][16];
        for (int i = 0; i < 256; i++) grid[i / 16][i % 16] = TextBatcher.CHARACTERS.charAt(i);
        return grid;
    }
}
