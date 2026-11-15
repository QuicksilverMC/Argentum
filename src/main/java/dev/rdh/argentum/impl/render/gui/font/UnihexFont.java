package dev.rdh.argentum.impl.render.gui.font;

import dev.rdh.argentum.impl.Argentum;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;

import java.io.IOException;
import java.util.function.IntFunction;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class UnihexFont implements IntFunction<Glyph> {
    private final ResourceManager resources;
    private final Identifier file;
    private final int[] overrides;
    private ByteArrayList bitmaps;
    private Int2LongOpenHashMap glyphs;

    UnihexFont(ResourceManager resources, JsonObject provider) {
        this.resources = resources;
        this.file = new Identifier(provider.get("hex_file").getAsString());
        JsonArray ranges = provider.has("size_overrides") ? provider.getAsJsonArray("size_overrides") : new JsonArray();
        this.overrides = new int[ranges.size() * 4];
        for (int i = 0; i < ranges.size(); i++) {
            JsonObject range = ranges.get(i).getAsJsonObject();
            this.overrides[i * 4] = range.get("from").getAsString().codePointAt(0);
            this.overrides[i * 4 + 1] = range.get("to").getAsString().codePointAt(0);
            this.overrides[i * 4 + 2] = range.get("left").getAsInt();
            this.overrides[i * 4 + 3] = range.get("right").getAsInt();
        }
    }

    @Override
    public Glyph apply(int codepoint) {
        if (this.glyphs == null) this.load();
        long entry = this.glyphs.get(codepoint);
        if (entry == 0) return null;

        int offset = (int) (entry >>> 8);
        int bitWidth = (int) entry & 0xFF;
        int[] lines = new int[16];
        int mask = 0;
        for (int row = 0; row < 16; row++) {
            int line = 0;
            for (int b = 0; b < bitWidth / 8; b++) line = line << 8 | this.bitmaps.getByte(offset++) & 0xFF;
            lines[row] = line << (32 - bitWidth);
            mask |= lines[row];
        }

        int left = 0;
        int right = bitWidth;
        if (mask != 0) {
            left = Integer.numberOfLeadingZeros(mask);
            right = 31 - Integer.numberOfTrailingZeros(mask);
        }
        for (int i = 0; i < this.overrides.length; i += 4) {
            if (codepoint >= this.overrides[i] && codepoint <= this.overrides[i + 1]) {
                left = this.overrides[i + 2];
                right = this.overrides[i + 3];
                break;
            }
        }

        int width = right - left + 1;
        if (mask == 0) return new Glyph(width / 2 + 1, true);
        int[] pixels = new int[width * 16];
        for (int row = 0; row < 16; row++) {
            for (int column = 0; column < width; column++) {
                int bit = 31 - left - column;
                if (bit >= 0 && bit < 32 && (lines[row] >>> bit & 1) != 0) pixels[row * width + column] = 0xFFFFFFFF;
            }
        }
        return new Glyph(width / 2 + 1, (width - 0.02F) / 2.0F, 0.0F, 8.0F, 2.0F, true,
                new Glyph.Sheet(this.file, pixels, width), 0, 0, width, 16);
    }

    private void load() {
        this.bitmaps = new ByteArrayList();
        this.glyphs = new Int2LongOpenHashMap();
        try (ZipInputStream zip = new ZipInputStream(this.resources.getResource(this.file).asStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().endsWith(".hex")) this.parse(zip.readAllBytes());
            }
        } catch (IOException | RuntimeException e) {
            Argentum.LOGGER.warn("Could not load unihex font {}", this.file, e);
        }
        this.bitmaps.trim();
        this.glyphs.trim();
    }

    private void parse(byte[] hex) {
        int i = 0;
        int line = 1;
        while (i < hex.length) {
            int colon = i;
            while (colon < hex.length && hex[colon] != ':') colon++;
            int end = colon;
            while (end < hex.length && hex[end] != '\n') end++;
            int digits = end - colon - 1;
            if (colon - i < 4 || colon - i > 6 || colon == hex.length || digits % 32 != 0 || digits == 0 || digits > 128) {
                throw new IllegalArgumentException("Invalid unihex entry at line " + line);
            }

            int codepoint = 0;
            for (int d = i; d < colon; d++) codepoint = codepoint << 4 | digit(hex[d], line);
            this.glyphs.put(codepoint, (long) this.bitmaps.size() << 8 | digits / 4);
            for (int d = colon + 1; d < end; d += 2) this.bitmaps.add((byte) (digit(hex[d], line) << 4 | digit(hex[d + 1], line)));

            i = end + 1;
            line++;
        }
    }

    private static int digit(byte b, int line) {
        int digit = Character.digit(b, 16);
        if (digit < 0) throw new IllegalArgumentException("Invalid hex digit '" + (char) b + "' at line " + line);
        return digit;
    }
}
