package dev.rdh.cera.modules.cit;

import dev.rdh.cera.Cera;
import dev.rdh.cera.props.BlendMethod;
import dev.rdh.cera.props.Props;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.block.BlockModel;
import net.minecraft.client.resource.ModelIdentifier;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.ModelManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.Identifier;
import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CustomItems {
    private volatile Rules rules = Rules.empty();
    private final Object2IntOpenHashMap<Identifier> glintWidths = new Object2IntOpenHashMap<>();
    private int generation;

    public void registerModels(ResourceManager resources, Map<String, Identifier> itemModels,
                               Map<Identifier, BlockModel> blockModels) {
        rules = load().registerModels(resources, itemModels, blockModels);
        generation++;
        glintWidths.clear();
        Cera.LOGGER.info("[CIT] Loaded {} rules", rules.all.size());
    }

    public void linkModels(ModelManager manager) {
        rules = rules.linkModels(manager);
        generation++;
    }

    public void discardEmptyModels(Map<String, Identifier> itemModels, Map<Identifier, BlockModel> blockModels) {
        itemModels.entrySet().removeIf(entry -> entry.getValue().getNamespace().equals("cera")
                && entry.getValue().getPath().startsWith("cit/") && blockModels.get(entry.getValue()) == null);
    }

    public BakedModel resolve(ItemStack stack, BakedModel original, ModelIdentifier location) {
        if (!Cera.CONFIG.customItems || stack == null) return original;
        List<CitRule> candidates = rules.byItem.get(Item.getId(stack.getItem()));
        if (candidates == null) return original;
        StackCache cache = cache(stack);
        if (cache != null && cache.model != null && cache.original == original && Objects.equals(cache.location, location)) {
            return cache.model;
        }
        BakedModel model = resolve(stack, original, location, candidates);
        if (cache != null) {
            cache.original = original;
            cache.location = location;
            cache.model = model;
        }
        return model;
    }

    private static BakedModel resolve(ItemStack stack, BakedModel original, ModelIdentifier location, List<CitRule> candidates) {
        String variant = location == null ? null : location.getPath();
        for (CitRule rule : candidates) {
            if (rule.type() != CitRule.Type.ITEM || !rule.matches(stack)) continue;
            BakedModel model = rule.model(variant, original);
            if (model != null) return model;
        }
        return original;
    }

    public Identifier resolveArmor(ItemStack stack, String material, int layer, boolean overlay, Identifier original) {
        if (!Cera.CONFIG.customItems) return original;
        List<CitRule> candidates = rules.byItem.get(Item.getId(stack.getItem()));
        if (candidates == null) return original;
        String key = material + "_layer_" + layer + (overlay ? "_overlay" : "");
        for (CitRule rule : candidates) {
            if (rule.type() != CitRule.Type.ARMOR || !rule.matches(stack)) continue;
            Identifier texture = rule.textures().getOrDefault(key, rule.texture());
            if (texture != null) return file(texture);
        }
        return original;
    }

    public List<Effect> effects(ItemStack stack) {
        Rules rules = this.rules;
        if (!Cera.CONFIG.customItems || stack == null || rules.enchantments.isEmpty()) return List.of();
        StackCache cache = cache(stack);
        if (cache != null && cache.effects != null) return cache.effects;
        List<Effect> effects = List.of();
        IntSet layers = null;
        for (int i = 0; i < rules.enchantments.size(); i++) {
            CitRule rule = rules.enchantments.get(i);
            if (rules.effects[i] == null || !rule.matches(stack)) continue;
            if (layers == null) {
                layers = new IntOpenHashSet();
                effects = new ObjectArrayList<>();
            }
            if (layers.add(rule.layer())) effects.add(rules.effects[i]);
        }
        if (cache != null) cache.effects = effects;
        return effects;
    }

    private StackCache cache(ItemStack stack) {
        ClientWorld world = Minecraft.getInstance().world;
        if (world == null) return null;
        StackCache cache = stack.cera$getCitCache();
        if (cache == null) stack.cera$setCitCache(cache = new StackCache());
        long key = (long) generation << 32 | world.getTime() & 0xFFFFFFFFL;
        if (cache.key != key) {
            cache.key = key;
            cache.model = null;
            cache.effects = null;
        }
        return cache;
    }

    public int glintWidth(Identifier texture) {
        int width = glintWidths.getInt(texture);
        if (width == 0) {
            width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            if (width <= 0) width = 16;
            glintWidths.put(texture, width);
        }
        return width;
    }

    private static Rules load() {
        List<NamespacedIdentifier> locations = new ArrayList<>();
        var resources = net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager.client();
        for (String root : List.of("optifine/cit/", "mcpatcher/cit/")) {
            locations.addAll(resources.findResources("minecraft", root, id -> id.identifier().endsWith(".properties")).keySet());
        }
        locations.sort(Comparator.comparing(NamespacedIdentifier::identifier));
        List<CitRule> loaded = new ObjectArrayList<>();
        for (NamespacedIdentifier location : locations) {
            List<Resource> stack = resources.getResourceStack(location);
            if (stack.isEmpty()) continue;
            Resource resource = stack.getLast();
            try {
				loaded.add(CitRule.parse(new Props(resource)));
            } catch (IOException | RuntimeException e) {
                Cera.LOGGER.warn("[CIT] Failed to load {} from {}", location, resource.sourceName(), e);
            }
        }
        loaded.addAll(potions(resources));
        loaded.sort(Comparator.comparingInt(CitRule::layer).thenComparing(Comparator.comparingInt(CitRule::weight).reversed())
                .thenComparing(CitRule::path));
        return Rules.of(loaded);
    }

    private static List<CitRule> potions(net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager resources) {
        List<CitRule> rules = new ObjectArrayList<>();
        for (String root : List.of("mcpatcher/cit/potion/normal/", "mcpatcher/cit/Potion/normal/", "mcpatcher/cit/potion/splash/", "mcpatcher/cit/Potion/splash/")) {
            boolean splash = root.toLowerCase().contains("/splash/");
            for (NamespacedIdentifier location : resources.findResources("minecraft", root, id -> id.identifier().endsWith(".png")).keySet()) {
                String path = location.identifier();
                String name = path.substring(root.length(), path.length() - ".png".length());
                if (!name.contains("/")) {
                    CitRule rule = CitRule.potion(location, name, splash);
                    if (rule != null) rules.add(rule);
                }
            }
        }
        return rules;
    }

    private static Identifier file(Identifier texture) {
        return new Identifier(texture.getNamespace(), texture.getPath() + ".png");
    }

    public record Effect(Identifier texture, BlendMethod blend, float speed, float rotation) {
    }

    public static final class StackCache {
        private long key;
        private BakedModel original;
        private ModelIdentifier location;
        private BakedModel model;
        private List<Effect> effects;
    }

    private record Rules(List<CitRule> all, Int2ObjectMap<List<CitRule>> byItem, List<CitRule> enchantments, Effect[] effects) {
        private static Rules empty() {
            return of(new ObjectArrayList<>());
        }

        private static Rules of(List<CitRule> all) {
            Int2ObjectMap<List<CitRule>> byItem = new Int2ObjectOpenHashMap<>();
            List<CitRule> enchantments = new ObjectArrayList<>();
            for (CitRule rule : all) {
                if (rule.type() == CitRule.Type.ENCHANTMENT) enchantments.add(rule);
                else for (int item : rule.items()) byItem.computeIfAbsent(item, ignored -> new ObjectArrayList<>()).add(rule);
            }
            Effect[] effects = enchantments.stream().map(rule -> rule.texture() == null ? null
                    : new Effect(file(rule.texture()), rule.blend(), rule.speed(), rule.rotation())).toArray(Effect[]::new);
            return new Rules(List.copyOf(all), byItem, List.copyOf(enchantments), effects);
        }

        private Rules registerModels(ResourceManager resources, Map<String, Identifier> itemModels, Map<Identifier, BlockModel> blockModels) {
            int index = 0;
            for (CitRule rule : all) rule.registerModels(resources, itemModels, blockModels, index++);
            return this;
        }

        private Rules linkModels(ModelManager manager) {
            for (CitRule rule : all) rule.linkModels(manager);
            return new Rules(all, byItem, enchantments, effects);
        }
    }
}
