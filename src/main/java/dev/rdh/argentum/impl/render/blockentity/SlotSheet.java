package dev.rdh.argentum.impl.render.blockentity;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.resource.Identifier;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class SlotSheet {
    private static final long IDLE_BEFORE_REUSE = TimeUnit.SECONDS.toNanos(2);

    private final int glId;
    private final int x;
    private final int y;
    private final int columns;
    private final int slotWidth;
    private final int slotHeight;
    private final int textureHeight;
    private final int mipLevels;
    private final float atlasWidth;
    private final float atlasHeight;
    private final PixelSource source;
    private final Entry[] slots;
    private final Map<Identifier, Entry> entries = new ConcurrentHashMap<>();
    private final Set<Identifier> rejected = new HashSet<>();

    private SlotSheet(TextureAtlas atlas, TextureAtlasSprite sheet, int mipLevels, int slotWidth, int slotHeight, int textureHeight,
            PixelSource source) {
        this.glId = atlas.getGlId();
        this.x = sheet.getX();
        this.y = sheet.getY();
        this.columns = sheet.getWidth() / slotWidth;
        this.slotWidth = slotWidth;
        this.slotHeight = slotHeight;
        this.textureHeight = textureHeight;
        this.mipLevels = mipLevels;
        this.atlasWidth = BakedBlockEntities.Region.atlasSize(sheet.getWidth(), sheet.getUMin(), sheet.getUMax());
        this.atlasHeight = BakedBlockEntities.Region.atlasSize(sheet.getHeight(), sheet.getVMin(), sheet.getVMax());
        this.source = source;
        this.slots = new Entry[this.columns * (sheet.getHeight() / slotHeight)];
    }

    static SlotSheet of(TextureAtlas atlas, String name, int mipLevels, int slotWidth, int slotHeight, int textureHeight,
            PixelSource source) {
        TextureAtlasSprite sheet = atlas.getSprite(name);
        return sheet instanceof EntityTextureSprite ? new SlotSheet(atlas, sheet, mipLevels, slotWidth, slotHeight, textureHeight, source) : null;
    }

    public Entry entry(Identifier key) {
        Entry entry = this.entries.get(key);
        if (entry != null) entry.lastUsed = System.nanoTime();
        return entry;
    }

    public Entry upload(Identifier key) {
        Entry entry = this.entry(key);
        if (entry != null || this.rejected.contains(key)) return entry;
        int slot = this.freeSlot();
        if (slot < 0) return null;
        int[] pixels;
        try {
            pixels = this.source.pixels(key);
        } catch (IOException exception) {
            this.rejected.add(key);
            return null;
        }
        if (pixels == null) return null;

        Entry previous = this.slots[slot];
        if (previous != null) {
            previous.evicted = true;
            this.entries.remove(previous.key);
        }

        int slotX = this.x + slot % this.columns * this.slotWidth;
        int slotY = this.y + slot / this.columns * this.slotHeight;
        int[][] levels = new int[this.mipLevels + 1][];
        levels[0] = pixels;
        GlStateManager.bindTexture(this.glId);
        TextureUtil.upload(TextureUtil.generateMipmaps(this.mipLevels, this.slotWidth, levels), this.slotWidth, this.slotHeight,
                slotX, slotY, false, false);

        entry = new Entry(key, new BakedBlockEntities.Region(slotX / this.atlasWidth, slotY / this.atlasHeight,
                this.slotWidth / this.atlasWidth, this.textureHeight / this.atlasHeight, pixels, this.slotWidth, this.textureHeight));
        this.slots[slot] = entry;
        this.entries.put(key, entry);
        return entry;
    }

    private int freeSlot() {
        long now = System.nanoTime();
        int oldest = -1;
        for (int slot = 0; slot < this.slots.length; slot++) {
            Entry entry = this.slots[slot];
            if (entry == null) return slot;
            if (entry.references == 0 && now - entry.lastUsed > IDLE_BEFORE_REUSE
                    && (oldest < 0 || entry.lastUsed < this.slots[oldest].lastUsed)) {
                oldest = slot;
            }
        }
        return oldest;
    }

    public void clear() {
        for (Entry entry : this.slots) {
            if (entry != null) entry.evicted = true;
        }
        Arrays.fill(this.slots, null);
        this.entries.clear();
        this.rejected.clear();
    }

    @FunctionalInterface
    interface PixelSource {
        int[] pixels(Identifier key) throws IOException;
    }

    public static final class Entry {
        private final Identifier key;
        private final BakedBlockEntities.Region region;
        private volatile long lastUsed = System.nanoTime();
        private volatile boolean evicted;
        private int references;

        private Entry(Identifier key, BakedBlockEntities.Region region) {
            this.key = key;
            this.region = region;
        }

        public BakedBlockEntities.Region region() {
            return this.region;
        }

        public boolean use() {
            this.lastUsed = System.nanoTime();
            return !this.evicted;
        }

        public boolean acquire() {
            if (this.evicted) return false;
            this.references++;
            return true;
        }

        public void release() {
            if (!this.evicted && --this.references == 0) this.lastUsed = System.nanoTime();
        }
    }
}
