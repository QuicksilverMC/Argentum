package dev.rdh.cera.modules.cit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rdh.cera.Cera;
import dev.rdh.cera.props.BlendMethod;
import dev.rdh.cera.props.NumberList;
import dev.rdh.cera.props.Props;
import dev.rdh.cera.props.Result;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.render.model.block.BlockModel;
import net.minecraft.client.resource.ModelIdentifier;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.ModelManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.resource.Identifier;
import net.ornithemc.osl.core.api.util.NamespacedIdentifier;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record CitRule(Type type, NamespacedIdentifier source, IntList items, Identifier model, Identifier texture,
			   Map<String, Identifier> models, Map<String, Identifier> textures,
			   NumberList damage, boolean damagePercent, int damageMask, NumberList stackSize, NumberList enchantmentIds,
			   IntSet enchantments, NumberList enchantmentLevels, List<NbtMatcher> nbt, int layer, int weight,
			   BlendMethod blend, float speed, float rotation, Baked baked
) {

    static final class Baked {
        ModelIdentifier model;
        BakedModel value;
        final Map<String, ModelIdentifier> models = new Object2ObjectOpenHashMap<>();
        final Map<String, BakedModel> values = new Object2ObjectOpenHashMap<>();
        final Map<BakedModel, TransformedModel> transformed = new Reference2ObjectOpenHashMap<>();
    }

    static CitRule parse(Props props) {
        Type type = Type.parse(props.get("type", "item"));
        IntList items = items(props);
        if (type != Type.ENCHANTMENT && items.isEmpty()) throw new IllegalArgumentException("Missing items");
        Identifier model = type == Type.ITEM ? asset(props.get("model"), props.id(), ".json") : null;
        Identifier texture = texture(asset(props.get("texture", props.get("tile", props.get("source"))), props.id(), ".png"));
        Map<String, Identifier> models = type == Type.ITEM ? assets(props, "model.", ".json") : new Object2ObjectOpenHashMap<>();
        Map<String, Identifier> textures = assets(props, "texture.", ".png");
        for (var entry : textures.entrySet()) entry.setValue(texture(entry.getValue()));
        if (type == Type.ITEM && model == null && texture == null) {
            model = models.get("bow_standby");
            texture = textures.get("bow_standby");
            if (texture == null) texture = textures.get("potion_bottle_drinkable");
            if (texture == null) texture = textures.get("potion_bottle_splash");
            if (model == null && texture == null && models.isEmpty() && textures.isEmpty()) texture = inferredAsset(props.id());
        }
        if (model == null && texture == null && models.isEmpty() && textures.isEmpty()) throw new IllegalArgumentException("No model or texture");
        String damage = props.get("damage");
        boolean damagePercent = damage != null && damage.contains("%");
        NumberList damageValues = numbers(damage == null ? null : damage.replace("%", ""));
        NumberList enchantmentIds = numbers(props.get("enchantmentIDs"));
        IntSet enchantments = enchantments(props.get("enchantments"));
        if (type == Type.ENCHANTMENT && enchantmentIds == null && enchantments.isEmpty()) {
            throw new IllegalArgumentException("Missing enchantments");
        }
        return new CitRule(type, props.id(), items, model, texture, Map.copyOf(models), Map.copyOf(textures),
                damageValues, damagePercent, integer(props, "damageMask", 0),
                numbers(props.get("stackSize")), enchantmentIds, enchantments,
                numbers(props.get("enchantmentLevels")), nbt(props), integer(props, "layer", 0),
                integer(props, "weight", 0), blend(props), decimal(props, "speed", 0.0F),
                decimal(props, "rotation", 0.0F), new Baked()
        );
    }

    static CitRule potion(NamespacedIdentifier source, String name, boolean splash) {
        Identifier texture = new Identifier(source.namespace(), source.identifier().substring(0, source.identifier().length() - ".png".length()));
        if ("empty".equals(name) && !splash) return item(source, Items.GLASS_BOTTLE, texture, null, 0);
        int[] damage = potionDamage(name);
        if (damage == null) return null;
        if (splash) for (int i = 0; i < damage.length; i++) damage[i] |= 16384;
        return item(source, Items.POTION, texture, NumberList.of(damage), 16447);
    }

    private static CitRule item(NamespacedIdentifier source, Item item, Identifier texture, NumberList damage, int damageMask) {
        return new CitRule(Type.ITEM, source, new IntArrayList(new int[]{Item.getId(item)}), null, texture,
                new Object2ObjectOpenHashMap<>(), new Object2ObjectOpenHashMap<>(), damage, false, damageMask, null, null,
                new IntOpenHashSet(), null, new ObjectArrayList<>(), 0, 0, BlendMethod.ADD, 0.0F, 0.0F, new Baked());
    }

    void registerModels(ResourceManager resources, Map<String, Identifier> itemModels,
                        Map<Identifier, BlockModel> blockModels, int index) {
        if (type != Type.ITEM) return;
        baked.model = register(resources, itemModels, blockModels, index, "base", model, layers());
        Set<String> keys = new HashSet<>();
        keys.addAll(models.keySet());
        keys.addAll(textures.keySet());
        for (String key : keys) {
            Identifier variantModel = models.get(key);
            if (variantModel == null && model == null && (key.startsWith("bow_pulling_") || key.startsWith("fishing_rod_"))) {
                variantModel = new Identifier("item/" + key);
            }
            if (variantModel == null) continue;
            Identifier variantTexture = textures.getOrDefault(key, texture);
            ModelIdentifier registered = register(resources, itemModels, blockModels, index, key, variantModel,
                    variantTexture == null ? Map.of() : Map.of("layer0", variantTexture));
            if (registered != null) baked.models.put(key, registered);
        }
    }

    private ModelIdentifier register(ResourceManager resources, Map<String, Identifier> itemModels,
                                     Map<Identifier, BlockModel> blockModels, int index, String key,
                                     Identifier model, Map<String, Identifier> layers) {
        if (model == null && (layers.isEmpty() || !layers.values().stream().allMatch(texture -> textureExists(resources, texture)))) return null;
        Identifier synthetic = new Identifier("cera", "cit/" + index + "/" + key.replaceAll("[^a-z0-9_/.-]", "_"));
        try {
            BlockModel loaded = model(resources, model, layers);
            blockModels.put(synthetic, loaded);
            parents(resources, blockModels, loaded);
            itemModels.put(synthetic.toString(), synthetic);
            return new ModelIdentifier(synthetic.toString(), "inventory");
        } catch (IOException | RuntimeException e) {
            Cera.LOGGER.warn("[CIT] Failed to load model for {}: {}", source, e.getMessage());
            return null;
        }
    }

    private static void parents(ResourceManager resources, Map<Identifier, BlockModel> blockModels, BlockModel model) throws IOException {
        Identifier parent = model.getParentLocation();
        if (parent == null || parent.getPath().startsWith("builtin/") || blockModels.containsKey(parent)) return;
        BlockModel loaded = model(resources, parent, Map.of());
        blockModels.put(parent, loaded);
        parents(resources, blockModels, loaded);
    }

    private Map<String, Identifier> layers() {
        Map<String, Identifier> layers = new Object2ObjectOpenHashMap<>();
        if (items.size() == 1 && Item.byId(items.getInt(0)) instanceof ArmorItem armor && armor.getTier() == ArmorItem.Tier.CLOTH) {
            String key = Item.REGISTRY.getKey(armor).getPath();
            if (textures.containsKey(key) || textures.containsKey(key + "_overlay")) {
                layers.put("layer0", textures.getOrDefault(key, texture != null ? texture : new Identifier("items/" + key)));
                layers.put("layer1", textures.getOrDefault(key + "_overlay", new Identifier("items/" + key + "_overlay")));
                return layers;
            }
        }
        Identifier overlay = textures.get("potion_overlay");
        Identifier bottle = textures.getOrDefault("potion_bottle_drinkable", textures.get("potion_bottle_splash"));
        if (overlay != null || bottle != null) {
            if (overlay != null) layers.put("layer0", overlay);
            if (bottle != null) layers.put("layer1", bottle);
        } else if (texture != null) {
            layers.put("layer0", texture);
        }
        return layers;
    }

    void linkModels(ModelManager manager) {
        baked.transformed.clear();
        baked.value = baked.model == null ? null : linked(manager, baked.model);
        for (var entry : baked.models.entrySet()) {
            BakedModel model = linked(manager, entry.getValue());
            if (model != null) baked.values.put(entry.getKey(), model);
        }
    }

    BakedModel model(String variant) {
        if (variant != null) {
            BakedModel model = baked.values.get(variant);
            if (model != null) return model;
        }
        return baked.value;
    }

    BakedModel model(String variant, BakedModel original) {
        BakedModel model = model(variant);
        if (model == null || this.model != null || variant != null && models.containsKey(variant)) return model;
        TransformedModel wrapped = baked.transformed.get(original);
        if (wrapped == null || wrapped.model() != model) {
            baked.transformed.put(original, wrapped = new TransformedModel(model, original.getTransformations()));
        }
        return wrapped;
    }

    boolean matches(ItemStack stack) {
        int damage = stack.getDamage();
        if (damageMask != 0) damage &= damageMask;
        if (damagePercent) {
            int maxDamage = stack.getItem().getMaxDamage();
            if (maxDamage == 0) return false;
            damage = damage * 100 / maxDamage;
        }
        if (this.damage != null && !this.damage.contains(damage)) return false;
        if (stackSize != null && !stackSize.contains(stack.size)) return false;
        if ((enchantmentIds != null || !enchantments.isEmpty() || enchantmentLevels != null) && !matchesEnchantments(stack)) return false;
        for (NbtMatcher matcher : nbt) if (!matcher.matches(stack.getNbt())) return false;
        return true;
    }

    private boolean matchesEnchantments(ItemStack stack) {
        NbtList enchantments = stack.getItem() == Items.ENCHANTED_BOOK
                ? Items.ENCHANTED_BOOK.getStoredEnchantments(stack) : stack.getEnchantments();
        if (enchantments == null) return false;
        boolean id = enchantmentIds == null && this.enchantments.isEmpty();
        boolean level = enchantmentLevels == null;
        for (int i = 0; i < enchantments.size(); i++) {
            NbtCompound enchantment = enchantments.getCompound(i);
            if (enchantmentIds != null && enchantmentIds.contains(enchantment.getShort("id"))) id = true;
            if (this.enchantments.contains(enchantment.getShort("id"))) id = true;
            if (enchantmentLevels != null && enchantmentLevels.contains(enchantment.getShort("lvl"))) level = true;
        }
        return id && level;
    }

    String path() {
        return source.identifier();
    }

    private static BlockModel model(ResourceManager resources, Identifier model, Map<String, Identifier> layers) throws IOException {
        JsonObject json;
        if (model == null) {
            json = new JsonObject();
            json.addProperty("parent", "builtin/generated");
        } else {
            try (Reader reader = new InputStreamReader(modelResource(resources, model).asStream(), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (json.has("parent")) json.addProperty("parent", resolve(json.get("parent").getAsString(), model).toString());
        }
        JsonObject textures = json.has("textures") ? json.getAsJsonObject("textures") : new JsonObject();
        if (json.has("textures")) json.remove("textures");
        for (var entry : new ArrayList<>(textures.entrySet())) {
            String value = entry.getValue().getAsString();
            if (!value.startsWith("#")) textures.addProperty(entry.getKey(), texture(resolve(value, model)).toString());
        }
        layers.forEach((layer, texture) -> textures.addProperty(layer, texture.toString()));
        json.add("textures", textures);
        return BlockModel.fromJson(json.toString());
    }

    private static Resource modelResource(ResourceManager resources, Identifier model) throws IOException {
        try {
            return resources.getResource(new Identifier(model.getNamespace(), model.getPath() + ".json"));
        } catch (IOException ignored) {
            return resources.getResource(new Identifier(model.getNamespace(), "models/" + model.getPath() + ".json"));
        }
    }

    private static IntList items(Props props) {
        String value = props.get("items", props.get("matchItems"));
        IntSet parsed = new IntOpenHashSet();
        if (value != null) {
            String[] tokens = value.trim().split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i];
                int separator = token.indexOf('-');
                if (separator > 0 && token.indexOf(':') < 0) {
                    try {
                        int from = Integer.parseInt(token.substring(0, separator));
                        int to = Integer.parseInt(token.substring(separator + 1));
                        for (int id = from; id <= to; id++) if (Item.byId(id) != null) parsed.add(id);
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
                Item item = Item.byKey(token);
                if (item == null && token.endsWith("_") && i + 1 < tokens.length) item = Item.byKey(token + tokens[++i]);
                if (item == null) throw new IllegalArgumentException("Unknown item: " + token);
                parsed.add(Item.getId(item));
            }
        } else {
            String name = props.id().identifier();
            int slash = name.lastIndexOf('/');
            int dot = name.lastIndexOf('.');
            Item item = Item.byKey(name.substring(slash + 1, dot));
            if (item != null) parsed.add(Item.getId(item));
        }
        return new IntArrayList(parsed);
    }

    private static Map<String, Identifier> assets(Props props, String prefix, String extension) {
        Map<String, Identifier> assets = new Object2ObjectOpenHashMap<>();
        for (String key : props.properties().stringPropertyNames()) {
            if (key.startsWith(prefix)) assets.put(key.substring(prefix.length()), asset(props.get(key), props.id(), extension));
        }
        return assets;
    }

    private static Identifier asset(String value, NamespacedIdentifier source, String extension) {
        if (value == null) return null;
        String path = value.trim();
        if (path.endsWith(extension)) path = path.substring(0, path.length() - extension.length());
        return Props.parseId(path.contains(":") || path.contains("/") || path.startsWith("assets/") ? path : "./" + path, source);
    }

    private static Identifier inferredAsset(NamespacedIdentifier source) {
        String path = source.identifier();
        return new Identifier(source.namespace(), path.substring(0, path.length() - ".properties".length()));
    }

    private static Identifier texture(Identifier id) {
        if (id == null) return null;
        String path = id.getPath();
        if (path.startsWith("textures/")) path = path.substring("textures/".length());
        if (path.endsWith(".png")) path = path.substring(0, path.length() - ".png".length());
        if (path.equals("blocks/nether_bricks")) path = "blocks/nether_brick";
        return new Identifier(id.getNamespace(), path);
    }

    private static BakedModel linked(ModelManager manager, ModelIdentifier id) {
        BakedModel model = manager.getModel(id);
        return model == manager.getMissingModel() ? null : model;
    }

    private static boolean textureExists(ResourceManager resources, Identifier texture) {
        String path = texture.getPath();
        if (!path.startsWith("optifine/") && !path.startsWith("mcpatcher/")) path = "textures/" + path;
        try {
            resources.getResource(new Identifier(texture.getNamespace(), path + ".png"));
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static int integer(Props props, String key, int fallback) {
        Result<Integer> result = props.getInt(key, fallback);
        if (!result.isSuccess()) throw new IllegalArgumentException(result.error());
        return result.value();
    }

    private static float decimal(Props props, String key, float fallback) {
        String value = props.get(key);
        if (value == null) return fallback;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number for " + key + ": " + value, e);
        }
    }

    private static BlendMethod blend(Props props) {
        Result<BlendMethod> result = props.getBlendMethod("blend", BlendMethod.ADD);
        if (!result.isSuccess()) throw new IllegalArgumentException(result.error());
        return result.value();
    }

    private static NumberList numbers(String value) {
        if (value == null) return null;
        Result<NumberList> result = NumberList.parse(value);
        if (!result.isSuccess()) throw new IllegalArgumentException(result.error());
        return result.value();
    }

    private static int[] potionDamage(String name) {
        return switch (name) {
            case "water" -> new int[]{0};
            case "awkward" -> new int[]{16};
            case "thick" -> new int[]{32};
            case "potent" -> new int[]{48};
            case "regeneration" -> new int[]{1, 17, 33, 49};
            case "movespeed", "speed" -> new int[]{2, 18, 34, 50};
            case "fireresistance", "fire_resistance" -> new int[]{3, 19, 35, 51};
            case "poison" -> new int[]{4, 20, 36, 52};
            case "heal", "instant_health" -> new int[]{5, 21, 37, 53};
            case "nightvision", "night_vision" -> new int[]{6, 22, 38, 54};
            case "weakness" -> new int[]{8, 24, 40, 56};
            case "damageboost", "strength" -> new int[]{9, 25, 41, 57};
            case "moveslowdown", "slowness" -> new int[]{10, 26, 42, 58};
            case "leaping" -> new int[]{11, 27, 43, 59};
            case "harm", "instant_damage" -> new int[]{12, 28, 44, 60};
            case "waterbreathing", "water_breathing" -> new int[]{13, 29, 45, 61};
            case "invisibility" -> new int[]{14, 30, 46, 62};
            case "mundane" -> new int[]{64};
            default -> null;
        };
    }

    private static IntSet enchantments(String value) {
        IntSet enchantments = new IntOpenHashSet();
        if (value == null) return enchantments;
        for (String token : value.trim().split("\\s+")) {
            Enchantment enchantment = Enchantment.byKey(token);
            if (enchantment == null) throw new IllegalArgumentException("Unknown enchantment: " + token);
            enchantments.add(enchantment.id);
        }
        return enchantments;
    }

    private static List<NbtMatcher> nbt(Props props) {
        List<NbtMatcher> matchers = new ObjectArrayList<>();
        for (String key : props.properties().stringPropertyNames()) {
            if (key.startsWith("nbt.")) matchers.add(NbtMatcher.parse(key.substring("nbt.".length()), props.get(key)));
        }
        return List.copyOf(matchers);
    }

    private static Identifier resolve(String value, Identifier source) {
        return Props.parseId(value, source.getNamespace(), source.getPath());
    }

    enum Type {
        ITEM, ENCHANTMENT, ARMOR;

        private static Type parse(String value) {
            return switch (value) {
                case "item" -> ITEM;
                case "enchantment" -> ENCHANTMENT;
                case "armor" -> ARMOR;
                default -> throw new IllegalArgumentException("Unsupported type: " + value);
            };
        }
    }
}
