package dev.rdh.cera.modules;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.cera.Cera;
import dev.rdh.cera.props.Props;
import dev.rdh.cera.props.Result;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.texture.TickableTexture;
import net.minecraft.resource.Identifier;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;
import net.ornithemc.osl.resource.loader.api.resource.pack.ResourcePack;
import net.ornithemc.osl.resource.loader.api.resource.reload.ResourceReloadListener;
import org.embeddedt.embeddium.api.util.ColorMixer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class AnimatedTextures implements ResourceReloadListener {
    private volatile Map<Identifier, AnimatedTexture> textures = Map.of();
    private volatile ResourceManager resources;

    @Override
    public void resourcesReloaded(ResourceManager resources) {
        this.resources = resources;
        this.textures = Cera.CONFIG.animatedTextures ? build(resources) : freeAll();

        int count = this.textures.values().stream().mapToInt(tex -> tex.animations.length).sum();
        Cera.LOGGER.info("[AnimatedTextures] Loaded {} animations across {} textures", count, this.textures.size());
    }

    public Texture overrideFor(Identifier id) {
        AnimatedTexture tex = this.textures.get(id);
        if (tex != null) tex.bound = true;
        return tex;
    }

    public void tick() {
        if (!Cera.CONFIG.animatedTextures) return;
        boolean onlyVisible = Argentum.CONFIG.animateOnlyVisibleTextures;
        for (AnimatedTexture tex : this.textures.values()) tex.tick(onlyVisible);
    }

    public void setEnabled(boolean enabled) {
        if (enabled) {
            if (this.resources != null) this.textures = build(this.resources);
        } else {
            this.textures = freeAll();
        }
    }

    private Map<Identifier, AnimatedTexture> build(ResourceManager resources) {
        Map<Identifier, AnimatedTexture> old = this.textures;
        Map<Identifier, Target> loaded = parse(resources);
        Map<Identifier, AnimatedTexture> next = new Object2ObjectOpenHashMap<>();

        loaded.forEach((id, target) -> {
            AnimatedTexture reuse = old.get(id);
            if (reuse != null && reuse.matches(target.width(), target.height())) {
                reuse.reset(target.pixels(), target.animations());
                next.put(id, reuse);
            } else {
                next.put(id, new AnimatedTexture(target.pixels(), target.width(), target.height(), target.animations()));
            }
        });

        old.forEach((id, tex) -> {
            if (next.get(id) != tex) tex.clearGlId();
        });
        return Map.copyOf(next);
    }

    private Map<Identifier, AnimatedTexture> freeAll() {
        this.textures.values().forEach(AnimatedTexture::clearGlId);
        return Map.of();
    }

    private static Map<Identifier, Target> parse(ResourceManager resources) {
        Map<Identifier, List<Animation>> grouped = new Object2ObjectOpenHashMap<>();
        Set<String> seen = new HashSet<>();
        collect(resources, "optifine/anim/", grouped, seen);
        collect(resources, "mcpatcher/anim/", grouped, seen);

        Map<Identifier, Target> loaded = new Object2ObjectOpenHashMap<>();
        grouped.forEach((id, animations) -> {
            Target target = target(resources, id, animations);
            if (target != null) loaded.put(id, target);
        });
        return loaded;
    }

    private static void collect(ResourceManager resources, String directory, Map<Identifier, List<Animation>> grouped, Set<String> seen) {
        resources.findResources("minecraft", directory, id -> id.identifier().endsWith(".properties"))
                .forEach((id, resource) -> {
                    if (seen.add(id.identifier().substring(directory.length()))) load(resource, resources, grouped);
                });
    }

    private static void load(Resource resource, ResourceManager resources, Map<Identifier, List<Animation>> grouped) {
        try {
            Props props = new Props(resource);
            Result<Animation> result = Animation.parse(props, resources);
            if (!result.isSuccess()) {
                Cera.LOGGER.warn("[AnimatedTextures] Skipping {}: {}", props.id(), result.error());
                return;
            }
            Animation animation = result.value();
            int targetPriority = resources.getResource(animation.target()).map(target -> priority(resources, target.sourceName())).orElse(-1);
            if (priority(resources, resource.sourceName()) < targetPriority) {
                Cera.LOGGER.warn("[AnimatedTextures] Skipping {}: target texture is replaced by a higher resource pack", props.id());
                return;
            }
            grouped.computeIfAbsent(animation.target(), _ -> new ArrayList<>()).add(animation);
        } catch (IOException e) {
            Cera.LOGGER.warn("[AnimatedTextures] Failed to read {}", resource.location(), e);
        }
    }

    private static int priority(ResourceManager resources, String pack) {
        List<ResourcePack> packs = resources.getResourcePacks();
        for (int i = packs.size() - 1; i >= 0; i--) {
            if (packs.get(i).getName().equals(pack)) return i;
        }
        return -1;
    }

    private static Target target(ResourceManager resources, Identifier id, List<Animation> animations) {
        try {
            BufferedImage image = readImage(resources, id);
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            return new Target(pixels, image.getWidth(), image.getHeight(), animations.toArray(Animation[]::new));
        } catch (IOException e) {
            Cera.LOGGER.warn("[AnimatedTextures] Target texture not found: {}", id);
            return null;
        }
    }

    private static BufferedImage readImage(ResourceManager resources, Identifier id) throws IOException {
        Resource resource = resources.getResource(id).orElse(null);
        if (resource == null) throw new FileNotFoundException(id.toString());
        try (resource; InputStream in = resource.open()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) throw new IOException("Not an image");
            return image;
        }
    }

    private static BufferedImage scale(BufferedImage image, int targetWidth) {
        int targetHeight = Math.max((int) ((double) image.getHeight() * targetWidth / image.getWidth()), 1);
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return scaled;
    }

    private record Target(int[] pixels, int width, int height, Animation[] animations) {
    }

    private static final class Animation {
        private final Identifier target;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int[] tiles;
        private final int[] durations;
        private final boolean interpolate;
        private final int skip;
        private final IntBuffer strip;
        private int phase;
        private int counter;

        private static Result<Animation> parse(Props props, ResourceManager resources) {
            String from = props.get("from");
            String to = props.get("to");
            if (from == null || to == null) return Result.failure("source or target texture not specified");

            int x = props.getInt("x", -1).orElse(-1);
            int y = props.getInt("y", -1).orElse(-1);
            int width = props.getInt("w", -1).orElse(-1);
            int height = props.getInt("h", -1).orElse(-1);
            if (x < 0 || y < 0 || width <= 0 || height <= 0) return Result.failure("invalid coordinates");

            Identifier target = props.parseId(to.trim());
            if (target.getPath().startsWith("textures/atlas/")) {
                return Result.failure("block/item textures must use vanilla mcmeta animations");
            }

            BufferedImage strip;
            try {
                strip = readImage(resources, props.parseId(from.trim()));
            } catch (IOException e) {
                return Result.failure("source texture not found");
            }
            if (strip.getWidth() != width) strip = scale(strip, width);
            int frames = strip.getHeight() / height;
            if (frames <= 0 || strip.getHeight() % height != 0) return Result.failure("source frame size does not divide the strip evenly");

            BufferedImage image;
            try {
                image = readImage(resources, target);
            } catch (IOException e) {
                return Result.failure("target texture not found");
            }
            if (x + width > image.getWidth() || y + height > image.getHeight()) {
                return Result.failure("animation coordinates are outside the target texture");
            }

            int defaultDuration = Math.max(props.getInt("duration", 1).orElse(1), 1);
            int count;
            if (props.contains("tile.0")) {
                count = 0;
                while (props.contains("tile." + count)) count++;
            } else {
                count = frames;
            }

            int[] tiles = new int[count];
            int[] durations = new int[count];
            for (int i = 0; i < count; i++) {
                tiles[i] = props.getInt("tile." + i, i).orElse(i);
                if (tiles[i] < 0 || tiles[i] >= frames) return Result.failure("tile." + i + " is outside the source texture");
                durations[i] = Math.max(props.getInt("duration." + i, defaultDuration).orElse(defaultDuration), 1);
            }

            boolean interpolate = props.getBoolean("interpolate", false).orElse(false);
            int skip = Math.max(props.getInt("skip", 0).orElse(0), 0);
            int[] stripPixels = strip.getRGB(0, 0, strip.getWidth(), strip.getHeight(), null, 0, strip.getWidth());
            IntBuffer stripBuffer = BufferUtils.createIntBuffer(stripPixels.length);
            stripBuffer.put(stripPixels).flip();
            return Result.success(new Animation(target, x, y, width, height, tiles, durations, interpolate, skip, stripBuffer));
        }

        Animation(Identifier target, int x, int y, int width, int height,
                int[] tiles, int[] durations, boolean interpolate, int skip, IntBuffer strip) {
            this.target = target;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.tiles = tiles;
            this.durations = durations;
            this.interpolate = interpolate;
            this.skip = skip;
            this.strip = strip;
        }

        Identifier target() {
            return this.target;
        }

        boolean tick() {
            this.counter++;
            if (this.counter < this.durations[this.phase]) {
                return this.interpolate && (this.skip <= 1 || this.counter % this.skip == 0);
            }
            this.counter = 0;
            this.phase = (this.phase + 1) % this.durations.length;
            return true;
        }

        void upload(IntBuffer scratch) {
            int tile = this.tiles[this.phase];
            int source = tile * this.height * this.width;
            int count = this.width * this.height;
            IntBuffer data;
            if (!this.interpolate || this.counter <= 0) {
                data = this.strip.limit(source + count).position(source);
            } else {
                int next = this.tiles[(this.phase + 1) % this.tiles.length] * this.height * this.width;
                float ratio = (float) this.counter / this.durations[this.phase];
                scratch.clear();
                for (int i = 0; i < count; i++) {
                    scratch.put(ColorMixer.mix(this.strip.get(next + i), this.strip.get(source + i), ratio));
                }
                data = scratch.flip();
            }
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, this.x, this.y, this.width, this.height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, data);
            this.strip.clear();
        }
    }

    private static final class AnimatedTexture extends DynamicTexture implements TickableTexture {
        private final int width;
        private final int height;
        private int[] base;
        private Animation[] animations;
        private boolean[] dirty;
        boolean bound;
        private static IntBuffer scratch;

        AnimatedTexture(int[] base, int width, int height, Animation[] animations) {
            super(width, height);
            this.width = width;
            this.height = height;
            this.base = base;
            this.animations = animations;
            this.repaint();
        }

        boolean matches(int width, int height) {
            return this.width == width && this.height == height;
        }

        void reset(int[] base, Animation[] animations) {
            this.base = base;
            this.animations = animations;
            this.repaint();
        }

        @Override
        public void tick() {
            this.tick(false);
        }

        void tick(boolean onlyVisible) {
            boolean active = !onlyVisible || this.bound;
            this.bound = false;
            boolean glBound = false;
            for (int i = 0; i < this.animations.length; i++) {
                Animation animation = this.animations[i];
                if (animation.tick()) this.dirty[i] = true;
                if (!active || !this.dirty[i]) continue;
                if (!glBound) {
                    GlStateManager.bindTexture(this.getGlId());
                    glBound = true;
                }
                animation.upload(scratch(animation.width * animation.height));
                this.dirty[i] = false;
            }
        }

        private static IntBuffer scratch(int size) {
            if (scratch == null || scratch.capacity() < size) scratch = BufferUtils.createIntBuffer(size);
            return scratch;
        }

        // Full upload of the base image only at (re)load; animation regions are patched in by the first active tick.
        private void repaint() {
            System.arraycopy(this.base, 0, this.getPixels(), 0, this.base.length);
            this.upload();
            this.dirty = new boolean[this.animations.length];
            java.util.Arrays.fill(this.dirty, true);
        }
    }
}
