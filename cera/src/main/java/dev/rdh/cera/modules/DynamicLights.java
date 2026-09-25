package dev.rdh.cera.modules;

import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;
import dev.rdh.cera.Cera;
import dev.rdh.cera.props.Props;

import net.minecraft.block.material.Material;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.BlazeEntity;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.entity.living.mob.monster.MagmaCubeEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;
import net.ornithemc.osl.resource.loader.api.resource.reload.ResourceReloadListener;

import java.io.IOException;

public final class DynamicLights {
    private static final String CONFIG = "optifine/dynamic_lights.properties";
    private static final double MAX_DISTANCE = 7.5;
    private static final Light[] NO_LIGHTS = new Light[0];
    private final Int2ObjectMap<Light> tracked = new Int2ObjectOpenHashMap<>();
    private final Rules rules;
    private volatile Light[] lights = NO_LIGHTS;
    private int ticks;

    public DynamicLights(Rules rules) {
        this.rules = rules;
    }

    public void update(ClientWorld world, ArgentumWorldRenderer renderer) {
        Mode mode = Cera.CONFIG.dynamicLights;
        if (mode == Mode.OFF) {
            clear();
            return;
        }
        if (mode == Mode.FAST && ticks++ % 10 != 0) return;

        IntOpenHashSet seen = new IntOpenHashSet();
        for (Entity entity : world.entities) {
            int level = getLightLevel(entity);
            if (level <= 0) continue;

            int id = entity.getNetworkId();
            seen.add(id);
            double x = entity.x - 0.5;
            double y = entity.y - 0.5 + entity.getEyeHeight();
            double z = entity.z - 0.5;
            boolean underwater = world.getBlockState(new BlockPos(x, y, z)).getBlock().getMaterial() == Material.WATER;
            Light previous = tracked.get(id);
            if (previous == null || previous.changed(x, y, z, level, underwater)) {
                if (previous != null) dirty(renderer, previous.x, previous.y, previous.z);
                dirty(renderer, x, y, z);
                tracked.put(id, new Light(x, y, z, level, underwater));
            }
        }

        var iterator = tracked.int2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var e = iterator.next();
            if (seen.contains(e.getIntKey())) continue;
            Light light = e.getValue();
            dirty(renderer, light.x, light.y, light.z);
            iterator.remove();
        }
        lights = tracked.values().toArray(Light[]::new);
    }

    public boolean active() {
        return Cera.CONFIG.dynamicLights != Mode.OFF && lights.length > 0;
    }

    public int combine(int x, int y, int z, int packedLight) {
        if (Cera.CONFIG.dynamicLights == Mode.OFF) return packedLight;

        double maximum = 0.0;
        for (Light light : lights) {
            double dx = x - light.x;
            double dy = y - light.y;
            double dz = z - light.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            int level = light.level;
            if (light.underwater) {
                level = Math.max(0, level - 2);
                distanceSquared *= 2.0;
            }
            if (distanceSquared > MAX_DISTANCE * MAX_DISTANCE) continue;
            maximum = Math.max(maximum, (1.0 - Math.sqrt(distanceSquared) / MAX_DISTANCE) * level);
        }

        int dynamic = (int)(maximum * 16.0);
        return dynamic > (packedLight & 0xFF) ? packedLight & ~0xFF | dynamic : packedLight;
    }

    public int combine(Entity entity, int packedLight) {
        if (Cera.CONFIG.dynamicLights == Mode.OFF) return packedLight;
        int dynamic = getLightLevel(entity) << 4;
        return dynamic > (packedLight & 0xFF) ? packedLight & ~0xFF | dynamic : packedLight;
    }

    private int getLightLevel(Entity entity) {
        if (entity instanceof PlayerEntity player && player.isSpectator()) return 0;
        if (entity.isOnFire()) return 15;

        int level = rules.entity(entity);
        if (entity instanceof BlazeEntity blaze && blaze.isFireActive()) level = Math.max(level, 15);
        if (entity instanceof MagmaCubeEntity magma && magma.targetStretch > 0.6F) level = Math.max(level, 13);
        if (entity instanceof CreeperEntity creeper && creeper.getFuse(0.0F) > 0.001F) level = Math.max(level, 15);
        if (entity instanceof LivingEntity living) {
            level = Math.max(level, getLightLevel(living.getDisplayItemInHand()));
            level = Math.max(level, getLightLevel(living.getEquipment(4)));
        }
        if (entity instanceof ItemEntity item) level = Math.max(level, getLightLevel(item.getItem()));
        return level;
    }

    private int getLightLevel(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return 0;
        if (stack.getItem() instanceof BlockItem blockItem) return blockItem.getBlock().getLight();
        return rules.item(stack.getItem());
    }

    private static void dirty(ArgentumWorldRenderer renderer, double x, double y, double z) {
        renderer.scheduleRebuildForBlockArea(
                (int)Math.floor(x - MAX_DISTANCE), (int)Math.floor(y - MAX_DISTANCE), (int)Math.floor(z - MAX_DISTANCE),
                (int)Math.floor(x + MAX_DISTANCE), (int)Math.floor(y + MAX_DISTANCE), (int)Math.floor(z + MAX_DISTANCE),
                false
        );
    }

    private void clear() {
        tracked.clear();
        lights = NO_LIGHTS;
        ticks = 0;
    }

    public static final class Rules implements ResourceReloadListener {
        private final Object2IntMap<String> entities = new Object2IntOpenHashMap<>();
        private final Object2IntMap<Item> items = new Object2IntOpenHashMap<>();

        @Override
        public void resourcesReloaded(ResourceManager resources) {
            entities.clear();
            items.clear();
            resources.findResources("optifine", id -> CONFIG.equals(id.identifier()))
                    .forEach((_, resource) -> load(resource));
            Cera.LOGGER.info("[DynamicLights] Loaded {} entity rules and {} item rules", entities.size(), items.size());
        }

        private int entity(Entity entity) {
            String key = Entities.getKey(entity);
            return key == null ? 0 : entities.getOrDefault(key, 0);
        }

        private int item(Item item) {
            return items.getOrDefault(item, 0);
        }

        private void load(Resource resource) {
            String namespace = resource.location().namespace();
            try {
                Props properties = new Props(resource);
                parse(properties.get("entities"), resource.location(), (name, level) ->
                        entities.put("minecraft".equals(namespace) ? name : namespace + ":" + name, level));
                parse(properties.get("items"), resource.location(), (name, level) -> {
                    Item item = Item.REGISTRY.get(new Identifier(namespace, name));
                    if (item == null) {
                        Cera.LOGGER.warn("[DynamicLights] Unknown item: {}:{}", namespace, name);
                    } else {
                        items.put(item, level);
                    }
                });
            } catch (IOException e) {
                Cera.LOGGER.warn("[DynamicLights] Failed to load from {}", resource.location(), e);
            }
        }

        private void parse(String value, NamespacedIdentifier id, RuleConsumer consumer) {
            if (value == null) return;
            String trimmed = value.trim();
            if (trimmed.isEmpty()) return;
            for (String entry : trimmed.split("\\s+")) {
                int separator = entry.lastIndexOf(':');
                if (separator <= 0) {
                    Cera.LOGGER.warn("[DynamicLights] Invalid rule in {}: {}", id, entry);
                    continue;
                }
                try {
                    int level = Integer.parseInt(entry.substring(separator + 1));
                    if (level < 0 || level > 15) throw new NumberFormatException();
                    consumer.accept(entry.substring(0, separator), level);
                } catch (NumberFormatException e) {
                    Cera.LOGGER.warn("[DynamicLights] Invalid light level in {}: {}", id, entry);
                }
            }
        }

        @FunctionalInterface
        private interface RuleConsumer {
            void accept(String name, int level);
        }
    }

    private record Light(double x, double y, double z, int level, boolean underwater) {
        private boolean changed(double x, double y, double z, int level, boolean underwater) {
            return Math.abs(this.x - x) > 0.1 || Math.abs(this.y - y) > 0.1 || Math.abs(this.z - z) > 0.1
                    || this.level != level || this.underwater != underwater;
        }
    }

    public enum Mode {
        OFF,
        FAST,
        FANCY;

        public String key() {
            return "value.dynamic_lights." + name().toLowerCase();
        }
    }
}
