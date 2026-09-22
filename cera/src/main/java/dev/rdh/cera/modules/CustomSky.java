package dev.rdh.cera.modules;

import dev.rdh.cera.Cera;
import dev.rdh.cera.props.BlendMethod;
import dev.rdh.cera.props.NumberList;
import dev.rdh.cera.props.Props;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.vertex.VertexFormat;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;
import net.ornithemc.osl.resource.loader.api.resource.reload.ResourceReloadListener;
import org.lwjgl.opengl.GL11;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class CustomSky implements ResourceReloadListener {
    private static final Pattern WORLD = Pattern.compile("(?:optifine|mcpatcher)/sky/world(-?\\d+)/sky(\\d+)\\.properties");
    private static final Pattern CELESTIAL = Pattern.compile("(?:optifine|mcpatcher)/sky/world(-?\\d+)/(sun|moon_phases)\\.properties");
    private volatile Int2ObjectMap<List<LayerState>> layers = Int2ObjectMaps.emptyMap();
    private volatile Int2ObjectMap<Celestial> celestials = Int2ObjectMaps.emptyMap();
    private Asset activeSun;
    private Asset activeMoon;
    private float celestialBrightness;
    private int lastDimension = Integer.MIN_VALUE;
    private long lastWorldTime = Long.MIN_VALUE;

    @Override
    public void resourcesReloaded(ResourceManager resources) {
        Int2ObjectMap<List<LayerState>> loaded = loadWorlds(resources, "optifine/sky/");
        loadWorlds(resources, "mcpatcher/sky/").forEach(loaded::putIfAbsent);
        Int2ObjectMap<Celestial> loadedCelestials = loadCelestials(resources, "mcpatcher/sky/");
        loadCelestials(resources, "optifine/sky/").forEach((world, celestial) ->
                loadedCelestials.put((int)world, celestial.over(loadedCelestials.get((int)world))));
        layers = Int2ObjectMaps.unmodifiable(loaded);
        celestials = Int2ObjectMaps.unmodifiable(loadedCelestials);
        lastDimension = Integer.MIN_VALUE;
        lastWorldTime = Long.MIN_VALUE;
        Cera.LOGGER.info("[CustomSky] Loaded {} layers and {} celestial overrides",
                loaded.values().stream().mapToInt(List::size).sum(),
                loadedCelestials.values().stream().mapToInt(Celestial::size).sum());
    }

    public void prepareCelestial(ClientWorld world, float tickDelta) {
        Celestial celestial = Cera.CONFIG.customSky ? celestials.get(world.dimension.getId()) : null;
        activeSun = celestial == null ? null : celestial.sun;
        activeMoon = celestial == null ? null : celestial.moon;
        celestialBrightness = 1.0F - Math.clamp(world.getRain(tickDelta), 0.0F, 1.0F);
    }

    public Identifier resolveSun(Identifier source) {
        return resolve(source, activeSun);
    }

    public Identifier resolveMoon(Identifier source) {
        return resolve(source, activeMoon);
    }

    private Identifier resolve(Identifier source, Asset override) {
        if (override == null) return source;
        override.blend.apply(celestialBrightness);
        return override.source;
    }

    public boolean hasLayers(ClientWorld world) {
        List<LayerState> sky = layers.get(world.dimension.getId());
        return sky != null && !sky.isEmpty();
    }

    public void render(ClientWorld world, float tickDelta) {
        int dimension = world.dimension.getId();
        if (dimension != lastDimension) {
            layers.values().forEach(layers -> layers.forEach(LayerState::reset));
            lastDimension = dimension;
            lastWorldTime = Long.MIN_VALUE;
        }
        List<LayerState> sky = layers.get(dimension);
        if (sky == null) return;

        int time = Math.floorMod((int)(world.getTimeOfDay() % 24000L), 24000);
        float rain = Math.clamp(world.getRain(tickDelta), 0.0F, 1.0F);
        float thunder = Math.clamp(world.getThunder(tickDelta), 0.0F, 1.0F);
        if (rain > 0.0F) thunder = Math.min(1.0F, thunder / rain);
        long worldTime = world.getTime();
        int elapsed = lastWorldTime == Long.MIN_VALUE ? 1 : Math.clamp(worldTime - lastWorldTime, 0, 200);
        lastWorldTime = worldTime;
        GlStateManager.enableTexture();
        GlStateManager.enableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.disableCull();
        GlStateManager.disableFog();
        GlStateManager.depthMask(false);
        GlStateManager.pushMatrix();
        GlStateManager.rotatef(-90.0F, 0.0F, 1.0F, 0.0F);
        for (LayerState layer : sky) layer.layer.render(world, time, tickDelta, rain, thunder, position(layer, world, elapsed));
        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.enableAlphaTest();
        GlStateManager.disableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableCull();
        GlStateManager.enableFog();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void texturesReloading(TextureManager textureManager) {
        layers.values().forEach(sky -> sky.forEach(layer -> textureManager.close(layer.layer.source)));
        celestials.values().forEach(celestial -> {
            if (celestial.sun != null) textureManager.close(celestial.sun.source);
            if (celestial.moon != null) textureManager.close(celestial.moon.source);
        });
    }

    private static Int2ObjectMap<List<LayerState>> loadWorlds(ResourceManager resources, String root) {
        Int2ObjectMap<List<LayerState>> result = new Int2ObjectOpenHashMap<>();
        List<Map.Entry<NamespacedIdentifier, Resource>> properties = new ArrayList<>(resources.findResources("minecraft", root,
                id -> WORLD.matcher(id.identifier()).matches()).entrySet());
        properties.sort(Comparator.comparingInt((Map.Entry<NamespacedIdentifier, Resource> e) -> layerWorld(e.getKey()))
                .thenComparingInt(e -> layerIndex(e.getKey())));
        for (var property : properties) {
            NamespacedIdentifier location = property.getKey();
            Resource resource = property.getValue();
            try {
                String name = location.identifier();
                Layer layer = Layer.parse(new Props(resource), new Identifier(location.namespace(), name.substring(0, name.length() - 11) + ".png"));
                if (hasResource(resources, layer.source)) result.computeIfAbsent(layerWorld(location), ignored -> new ArrayList<>()).add(new LayerState(layer));
            } catch (IOException | RuntimeException e) {
                Cera.LOGGER.warn("[CustomSky] Failed to load {}", resource.location(), e);
            }
        }
        result.replaceAll((_, sky) -> List.copyOf(sky));
        return result;
    }

    private static Int2ObjectMap<Celestial> loadCelestials(ResourceManager resources, String root) {
        Int2ObjectMap<Celestial> result = new Int2ObjectOpenHashMap<>();
        for (var entry : resources.findResources("minecraft", root, id -> CELESTIAL.matcher(id.identifier()).matches()).entrySet()) {
            NamespacedIdentifier location = entry.getKey();
            Resource resource = entry.getValue();
            Matcher matcher = CELESTIAL.matcher(location.identifier());
            if (!matcher.matches()) continue;
            try {
                String name = location.identifier();
                Asset asset = Asset.parse(new Props(resource), new Identifier(location.namespace(), name.substring(0, name.length() - 11) + ".png"));
                if (!hasResource(resources, asset.source)) continue;
                int world = Integer.parseInt(matcher.group(1));
                Celestial celestial = matcher.group(2).equals("sun") ? new Celestial(asset, null) : new Celestial(null, asset);
                result.put(world, celestial.over(result.get(world)));
            } catch (IOException | RuntimeException e) {
                Cera.LOGGER.warn("[CustomSky] Failed to load {}", resource.location(), e);
            }
        }
        return result;
    }

    private static int layerWorld(NamespacedIdentifier location) {
        Matcher matcher = WORLD.matcher(location.identifier());
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid sky layer: " + location);
        return Integer.parseInt(matcher.group(1));
    }

    private static int layerIndex(NamespacedIdentifier location) {
        Matcher matcher = WORLD.matcher(location.identifier());
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid sky layer: " + location);
        return Integer.parseInt(matcher.group(2));
    }

    private static boolean hasResource(ResourceManager resources, Identifier id) {
        Resource resource = resources.getResource(id).orElse(null);
        if (resource == null) {
            Cera.LOGGER.warn("[CustomSky] Texture not found: {}", id);
            return false;
        }
        try (resource) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static int time(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid time: " + value);
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) throw new IllegalArgumentException("Invalid time: " + value);
        return Math.floorMod(hour - 6, 24) * 1000 + minute * 1000 / 60;
    }

    private float position(LayerState state, ClientWorld world, int elapsed) {
        Layer layer = state.layer;
        if (layer.biomes().isEmpty() && layer.heights() == null) return 1.0F;
        Entity camera = Minecraft.getInstance().getCamera();
        float target = 0.0F;
        if (camera != null) {
            // OptiFine samples getCommandSourceBlockPos() (y + 0.5), not the entity's own block, so
            // heights bands flip at the same point it does. Only y matters here; biomes are 2D.
            BlockPos pos = camera.getCommandSourceBlockPos();
            Biome biome = world.getBiome(pos);
            if ((layer.biomes().isEmpty() || layer.biomes().contains(Props.biome(biome.name)) != layer.excludeBiomes())
                    && (layer.heights() == null || layer.heights().contains(pos.getY()))) {
                target = 1.0F;
            }
        }
        float brightness = Float.isNaN(state.positionBrightness) ? target : state.positionBrightness;
        float step = layer.transition() == 0.0F ? 1.0F : elapsed / (layer.transition() * 20.0F);
        brightness += Math.copySign(Math.min(Math.abs(target - brightness), step), target - brightness);
        state.positionBrightness = brightness;
        return brightness;
    }

    private static final class LayerState {
        private final Layer layer;
        private float positionBrightness = Float.NaN;

        private LayerState(Layer layer) {
            this.layer = layer;
        }

        private void reset() {
            positionBrightness = Float.NaN;
        }
    }

    private record Celestial(Asset sun, Asset moon) {
        private Celestial over(Celestial fallback) {
            return fallback == null ? this : new Celestial(sun == null ? fallback.sun : sun, moon == null ? fallback.moon : moon);
        }

        private int size() {
            return (sun == null ? 0 : 1) + (moon == null ? 0 : 1);
        }
    }

    private record Asset(Identifier source, BlendMethod blend) {
        private static Asset parse(Props props, Identifier defaultSource) {
            Identifier source = props.parseId(props.get("source", defaultSource.toString()));
            return new Asset(source, props.getBlendMethod("blend", BlendMethod.ADD).value());
        }
    }

    private static final VertexFormat FORMAT = DefaultVertexFormat.POSITION_TEX;
    private static VertexBuffer geometry;

    private record Layer(
            Identifier source,
            int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut,
            BlendMethod blend, boolean rotate, float speed,
            float axisX, float axisY, float axisZ,
            NumberList days, int daysLoop,
            boolean clear, boolean rain, boolean thunder,
            Set<String> biomes, boolean excludeBiomes, NumberList heights, float transition
    ) {

        private static Layer parse(Props props, Identifier defaultSource) {
            Identifier source = props.parseId(props.get("source", defaultSource.toString()));
            String start = props.get("startFadeIn");
            String end = props.get("endFadeIn");
            String out = props.get("endFadeOut");
            if ((start == null) != (end == null) || (start == null) != (out == null)) {
                throw new IllegalArgumentException("Incomplete fade times");
            }
            int startFadeIn = start == null ? -1 : time(start);
            int endFadeIn = startFadeIn < 0 ? -1 : time(end);
            int endFadeOut = startFadeIn < 0 ? -1 : time(out);
            int fadeIn = startFadeIn < 0 ? 0 : Math.floorMod(endFadeIn - startFadeIn, 24000);
            int startFadeOut = startFadeIn < 0 ? -1 : props.contains("startFadeOut")
                    ? time(props.get("startFadeOut")) : Math.floorMod(endFadeOut - fadeIn, 24000);
            if (startFadeIn >= 0 && Math.floorMod(endFadeIn - startFadeIn, 24000)
                    + Math.floorMod(startFadeOut - endFadeIn, 24000)
                    + Math.floorMod(endFadeOut - startFadeOut, 24000)
                    + Math.floorMod(startFadeIn - endFadeOut, 24000) != 24000) {
                throw new IllegalArgumentException("Invalid fade times");
            }

            BlendMethod blend = props.getBlendMethod("blend", BlendMethod.ADD).value();
            boolean rotate = props.getBoolean("rotate", true).value();
            float speed = props.getFloat("speed", 1.0F).value();
            if (speed < 0.0F) throw new IllegalArgumentException("Invalid speed");
            String[] axis = props.get("axis", "1 0 0").trim().split("\\s+");
            if (axis.length != 3) throw new IllegalArgumentException("Invalid axis");
            float x = Float.parseFloat(axis[0]);
            float y = Float.parseFloat(axis[1]);
            float z = Float.parseFloat(axis[2]);
            if (Math.abs(x) > 1.0F || Math.abs(y) > 1.0F || Math.abs(z) > 1.0F || x * x + y * y + z * z < 1.0E-5F) {
                throw new IllegalArgumentException("Invalid axis");
            }
            int daysLoop = props.getInt("daysLoop", 8).value();
            if (daysLoop <= 0) throw new IllegalArgumentException("Invalid daysLoop");
            Set<String> weather = new ObjectOpenHashSet<>();
            for (String value : props.get("weather", "clear").trim().split("\\s+")) {
                if (!value.equals("clear") && !value.equals("rain") && !value.equals("thunder")) {
                    throw new IllegalArgumentException("Invalid weather: " + value);
                }
                weather.add(value);
            }
            String biomeList = props.get("biomes");
            boolean excludeBiomes = biomeList != null && biomeList.trim().startsWith("!");
            if (excludeBiomes) biomeList = biomeList.trim().substring(1);
            Set<String> biomes = new ObjectOpenHashSet<>();
            if (biomeList != null) for (String value : biomeList.split("[ ,]+")) if (!value.isEmpty()) biomes.add(Props.biome(value));
            float transition = props.getFloat("transition", 1.0F).value();
            if (transition < 0.0F) throw new IllegalArgumentException("Invalid transition");
            return new Layer(source,
                    startFadeIn, endFadeIn, startFadeOut, endFadeOut,
                    blend, rotate, speed,
                    z, y, -x,
                    props.getNumberList("days").value(), daysLoop,
                    weather.contains("clear"), weather.contains("rain"), weather.contains("thunder"),
                    Set.copyOf(biomes), excludeBiomes,
                    props.getNumberList("heights").value(), transition
            );
        }

        private void render(ClientWorld world, int time, float tickDelta, float rainStrength, float thunderStrength, float positionBrightness) {
            if (!active(world, time)) return;
            float brightness = fade(time) * weather(rainStrength, thunderStrength) * positionBrightness;
            if (brightness < 1.0E-4F) return;
            GlStateManager.pushMatrix();
            if (rotate) {
                long day = (world.getTimeOfDay() + 18000L) / 24000L;
                float offset = speed == Math.round(speed) ? 0.0F : (float)((day * (double)(speed % 1.0F)) % 1.0);
                GlStateManager.rotatef(360.0F * (offset + world.getTimeOfDay(tickDelta) * speed), axisX, axisY, axisZ);
            }
            blend.apply(brightness);
            Minecraft.getInstance().getTextureManager().bind(source);
            draw();
            GlStateManager.popMatrix();
        }

        private boolean active(ClientWorld world, int time) {
            if (startFadeIn >= 0 && between(time, endFadeOut, startFadeIn)) return false;
            if (days == null) return true;
            int day = Math.floorMod((int)Math.floorDiv(world.getTimeOfDay() - Math.max(startFadeIn, 0), 24000L), daysLoop);
            return days.contains(day);
        }

        private float fade(int time) {
            if (startFadeIn < 0 || between(time, endFadeIn, startFadeOut)) return 1.0F;
            if (between(time, startFadeIn, endFadeIn)) return (float)Math.floorMod(time - startFadeIn, 24000) / Math.floorMod(endFadeIn - startFadeIn, 24000);
            if (between(time, startFadeOut, endFadeOut)) return 1.0F - (float)Math.floorMod(time - startFadeOut, 24000) / Math.floorMod(endFadeOut - startFadeOut, 24000);
            return 0.0F;
        }

        private float weather(float rainStrength, float thunderStrength) {
            return Math.min(1.0F, (clear ? 1.0F - rainStrength : 0.0F)
                    + (rain ? rainStrength - thunderStrength : 0.0F) + (thunder ? thunderStrength : 0.0F));
        }

        private static boolean between(int time, int start, int end) {
            return start <= end ? time >= start && time <= end : time >= start || time <= end;
        }

        private static void draw() {
            if (geometry == null) geometry = build();
            geometry.bind();
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            int stride = FORMAT.getVertexSize();
            GL11.glVertexPointer(3, GL11.GL_FLOAT, stride, FORMAT.getOffset(0));
            GL11.glTexCoordPointer(2, GL11.GL_FLOAT, stride, FORMAT.getUvOffset(0));
            geometry.draw(GL11.GL_QUADS);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            geometry.unbind();
        }

        private static VertexBuffer build() {
            double size = 100.0;
            float third = 1.0F / 3.0F;
            float twoThirds = 2.0F / 3.0F;
            BufferBuilder buffer = new BufferBuilder(24 * FORMAT.getVertexSize() / Integer.BYTES);
            buffer.begin(GL11.GL_QUADS, FORMAT);

            buffer.vertex(-size, -size, size).texture(0.0F, 0.0F).nextVertex();
            buffer.vertex(size, -size, size).texture(0.0F, 0.5F).nextVertex();
            buffer.vertex(size, -size, -size).texture(third, 0.5F).nextVertex();
            buffer.vertex(-size, -size, -size).texture(third, 0.0F).nextVertex();

            buffer.vertex(size, size, size).texture(third, 0.0F).nextVertex();
            buffer.vertex(-size, size, size).texture(third, 0.5F).nextVertex();
            buffer.vertex(-size, size, -size).texture(twoThirds, 0.5F).nextVertex();
            buffer.vertex(size, size, -size).texture(twoThirds, 0.0F).nextVertex();

            buffer.vertex(size, size, -size).texture(twoThirds, 0.0F).nextVertex();
            buffer.vertex(size, -size, -size).texture(twoThirds, 0.5F).nextVertex();
            buffer.vertex(size, -size, size).texture(1.0F, 0.5F).nextVertex();
            buffer.vertex(size, size, size).texture(1.0F, 0.0F).nextVertex();

            buffer.vertex(size, size, size).texture(0.0F, 0.5F).nextVertex();
            buffer.vertex(size, -size, size).texture(0.0F, 1.0F).nextVertex();
            buffer.vertex(-size, -size, size).texture(third, 1.0F).nextVertex();
            buffer.vertex(-size, size, size).texture(third, 0.5F).nextVertex();

            buffer.vertex(-size, size, size).texture(third, 0.5F).nextVertex();
            buffer.vertex(-size, -size, size).texture(third, 1.0F).nextVertex();
            buffer.vertex(-size, -size, -size).texture(twoThirds, 1.0F).nextVertex();
            buffer.vertex(-size, size, -size).texture(twoThirds, 0.5F).nextVertex();

            buffer.vertex(-size, size, -size).texture(twoThirds, 0.5F).nextVertex();
            buffer.vertex(-size, -size, -size).texture(twoThirds, 1.0F).nextVertex();
            buffer.vertex(size, -size, -size).texture(1.0F, 1.0F).nextVertex();
            buffer.vertex(size, size, -size).texture(1.0F, 0.5F).nextVertex();

            buffer.end();
            VertexBuffer uploaded = new VertexBuffer(FORMAT);
            uploaded.upload(buffer.getBuffer());
            return uploaded;
        }
    }
}
