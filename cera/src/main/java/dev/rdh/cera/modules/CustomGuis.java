package dev.rdh.cera.modules;

import dev.rdh.cera.Cera;
import dev.rdh.cera.modules.random.RandomConditions;
import dev.rdh.cera.props.NumberList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import dev.rdh.cera.props.Patterns;
import dev.rdh.cera.props.Props;
import dev.rdh.cera.props.Result;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.menu.AnvilScreen;
import net.minecraft.client.gui.screen.inventory.menu.BeaconScreen;
import net.minecraft.client.gui.screen.inventory.menu.BrewingStandScreen;
import net.minecraft.client.gui.screen.inventory.menu.ChestScreen;
import net.minecraft.client.gui.screen.inventory.menu.CraftingTableScreen;
import net.minecraft.client.gui.screen.inventory.menu.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.inventory.menu.DispenserScreen;
import net.minecraft.client.gui.screen.inventory.menu.EnchantingTableScreen;
import net.minecraft.client.gui.screen.inventory.menu.FurnaceScreen;
import net.minecraft.client.gui.screen.inventory.menu.HopperScreen;
import net.minecraft.client.gui.screen.inventory.menu.HorseScreen;
import net.minecraft.client.gui.screen.inventory.menu.SurvivalInventoryScreen;
import net.minecraft.client.gui.screen.inventory.menu.VillagerScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Nameable;
import net.minecraft.world.biome.Biome;
import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;
import net.ornithemc.osl.resource.loader.api.resource.reload.ResourceReloadListener;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static dev.rdh.cera.props.Props.normalize;

public final class CustomGuis implements ResourceReloadListener {
    private volatile List<Rule> rules = List.of();
    private BlockPos blockPos;
    private Entity entity;

    @Override
    public void resourcesReloaded(ResourceManager resources) {
        List<Rule> loaded = new ArrayList<>();
        List<NamespacedIdentifier> locations = new ArrayList<>(resources.findResources("minecraft", "optifine/gui/container/",
                id -> id.identifier().endsWith(".properties")).keySet());
        locations.sort(Comparator.comparing(NamespacedIdentifier::identifier));
        for (NamespacedIdentifier location : locations) {
            List<Resource> stack = resources.getResourceStack(location);
            if (stack.isEmpty()) continue;
            Resource resource = stack.getLast();
            try {
				loaded.add(Rule.parse(new Props(resource)));
            } catch (IOException | RuntimeException e) {
                Cera.LOGGER.warn("[CustomGuis] Failed to load {} from {}", location, resource.sourceName(), e);
            }
        }
        rules = List.copyOf(loaded);
        Cera.LOGGER.info("[CustomGuis] Loaded {} rules", rules.size());
    }

    public void interactedWith(BlockPos pos) {
        blockPos = pos;
        entity = null;
    }

    public void interactedWith(Entity entity) {
        this.entity = entity;
        blockPos = null;
    }

    public Identifier resolve(Identifier original) {
        if (!Cera.CONFIG.customGuis || !"minecraft".equals(original.getNamespace()) || !original.getPath().startsWith("textures/gui/")) {
            return original;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Container container = Container.of(minecraft.screen);
        Context context = Context.of(minecraft, container, blockPos, entity);
        if (context == null) return original;
        for (Rule rule : rules) {
            if (rule.matches(context)) return rule.textures.getOrDefault(original, original);
        }
        return original;
    }

    private record Context(Container container, ClientWorld world, BlockPos pos, BlockEntity blockEntity, Entity entity) {
        private static Context of(Minecraft minecraft, Container container, BlockPos blockPos, Entity entity) {
            ClientWorld world = minecraft.world;
            if (container == null || world == null) return null;
            if (container == Container.CREATIVE || container == Container.INVENTORY) {
                if (minecraft.player == null) return null;
                BlockPos pos = new BlockPos(minecraft.player);
                return new Context(container, world, pos, null, minecraft.player);
            }
            if (container == Container.HORSE || container == Container.VILLAGER) {
                return entity == null ? null : new Context(container, world, new BlockPos(entity), null, entity);
            }
            return blockPos == null ? null : new Context(container, world, blockPos, world.getBlockEntity(blockPos), null);
        }

        private String name() {
            if (entity instanceof Nameable nameable) return nameable.getDisplayName().getString();
            if (blockEntity instanceof Inventory inventory) return inventory.getDisplayName().getString();
            return null;
        }

        private String biome() {
            Biome biome = world.getBiome(pos);
            return normalize(biome.name);
        }
    }

    private record Rule(Container container, Map<Identifier, Identifier> textures, Predicate<String> name, Set<String> biomes,
                        boolean excludeBiomes, NumberList heights, Boolean large, Boolean trapped, Boolean christmas,
                        Boolean ender, NumberList levels, List<Profession> professions, Set<Variant> variants) {
        private static Rule parse(Props props) {
            Result<Container> parsedContainer = Container.parse(props.get("container"));
            if (!parsedContainer.isSuccess()) throw new IllegalArgumentException(parsedContainer.error());
            Container container = parsedContainer.value();
            Map<Identifier, Identifier> textures = textures(props, container);
            if (textures.isEmpty()) throw new IllegalArgumentException("No texture");
            Predicate<String> name = Patterns.matcher(props.get("name"));
            Biomes biomes = Biomes.parse(props.get("biomes"));
            NumberList heights = props.getNumberList("heights").value();
            NumberList levels = props.getNumberList("levels").value();
            Boolean large = props.getBoolean("large").value();
            Boolean trapped = props.getBoolean("trapped").value();
            Boolean christmas = props.getBoolean("christmas").value();
            Boolean ender = props.getBoolean("ender").value();
            List<Profession> professions = professions(props.get("professions"));
            Set<Variant> variants = variants(props.get("variants"), container);
            return new Rule(container, Map.copyOf(textures), name, biomes.names, biomes.exclude, heights, large, trapped,
                    christmas, ender, levels, professions, variants);
        }

        private boolean matches(Context context) {
            if (container != context.container) return false;
            if (name != null && !name.test(context.name())) return false;
            if (!biomes.isEmpty() && biomes.contains(context.biome()) == excludeBiomes) return false;
            if (heights != null && !heights.contains(context.pos.getY())) return false;
            return switch (container) {
                case CHEST -> matchesChest(context.blockEntity);
                case BEACON -> matchesBeacon(context.blockEntity);
                case DISPENSER -> matchesDispenser(context.blockEntity);
                case HORSE -> matchesHorse(context.entity);
                case VILLAGER -> matchesVillager(context.entity);
                default -> true;
            };
        }

        private boolean matchesChest(BlockEntity blockEntity) {
            if (blockEntity instanceof EnderChestBlockEntity) {
                return matches(false, false, false, true);
            }
            if (!(blockEntity instanceof ChestBlockEntity chest)) return false;
            boolean large = chest.northNeighbor != null || chest.southNeighbor != null || chest.eastNeighbor != null || chest.westNeighbor != null;
            return matches(large, chest.getChestType() == 1, CustomGuis.christmas(), false);
        }

        private boolean matches(boolean large, boolean trapped, boolean christmas, boolean ender) {
            return (this.large == null || this.large == large)
                    && (this.trapped == null || this.trapped == trapped)
                    && (this.christmas == null || this.christmas == christmas)
                    && (this.ender == null || this.ender == ender);
        }

        private boolean matchesBeacon(BlockEntity blockEntity) {
            if (!(blockEntity instanceof BeaconBlockEntity beacon)) return false;
            if (levels == null) return true;
            NbtCompound nbt = new NbtCompound();
            beacon.writeNbt(nbt);
            return levels.contains(nbt.getInt("Levels"));
        }

        private boolean matchesDispenser(BlockEntity blockEntity) {
            if (!(blockEntity instanceof DispenserBlockEntity)) return false;
            return variants == null || variants.contains(blockEntity instanceof DropperBlockEntity ? Variant.DROPPER : Variant.DISPENSER);
        }

        private boolean matchesHorse(Entity entity) {
            if (!(entity instanceof HorseBaseEntity horse)) return false;
            return variants == null || variants.contains(switch (horse.getType()) {
                case 0 -> Variant.HORSE;
                case 1 -> Variant.DONKEY;
                case 2 -> Variant.MULE;
                default -> null;
            });
        }

        private boolean matchesVillager(Entity entity) {
            if (!(entity instanceof VillagerEntity villager) || professions == null) return entity instanceof VillagerEntity;
            NbtCompound nbt = new NbtCompound();
            villager.writeNbt(nbt);
            return professions.stream().anyMatch(profession -> profession.matches(villager.getProfession(), nbt.getInt("Career")));
        }

        private static Map<Identifier, Identifier> textures(Props props, Container container) {
            Map<Identifier, Identifier> textures = new HashMap<>();
            String replacement = props.get("texture");
            if (replacement != null && container.defaultTexture != null) textures.put(container.defaultTexture, texture(replacement, props.id()));
            for (String key : props.properties().stringPropertyNames()) {
                if (!key.startsWith("texture.")) continue;
                String path = key.substring("texture.".length()).replace('\\', '/');
                path = path.replaceFirst("^/+", "").replaceFirst("\\.png$", "");
                textures.put(new Identifier("textures/gui/" + path + ".png"), texture(props.get(key), props.id()));
            }
            return textures;
        }

        private static Identifier texture(String value, NamespacedIdentifier source) {
            String path = value.trim();
            if (!path.endsWith(".png")) path += ".png";
            if (path.startsWith("/~")) path = path.substring(1);
            if (path.startsWith("~/")) return new Identifier(source.namespace(), "mcpatcher/" + path.substring(2));
            if (path.startsWith("/")) return new Identifier(source.namespace(), "mcpatcher/" + path.substring(1));
            return Props.parseId(path.contains(":") || path.startsWith("assets/") || path.startsWith("./") ? path : "./" + path, source);
        }

        private static List<Profession> professions(String value) {
            if (value == null) return null;
            List<Profession> professions = new ArrayList<>();
            for (String token : value.trim().split("\\s+")) professions.add(Profession.parse(token));
            return List.copyOf(professions);
        }

        private static Set<Variant> variants(String value, Container container) {
            if (value == null) return null;
            if (container != Container.HORSE && container != Container.DISPENSER) throw new IllegalArgumentException("Invalid variants");
            Set<Variant> variants = EnumSet.noneOf(Variant.class);
            for (String token : value.trim().split("\\s+")) {
                Variant variant = Variant.parse(token);
                if (variant == null || (container == Container.HORSE) != variant.horse) throw new IllegalArgumentException("Invalid variant: " + token);
                variants.add(variant);
            }
            return variants;
        }
    }

    private record Biomes(Set<String> names, boolean exclude) {
        private static Biomes parse(String value) {
            if (value == null) return new Biomes(Set.of(), false);
            String names = value.trim();
            boolean exclude = names.startsWith("!");
            if (exclude) names = names.substring(1);
            Set<String> parsed = new HashSet<>();
            for (String name : names.split("\\s+")) parsed.add(normalize(name));
            return new Biomes(Set.copyOf(parsed), exclude);
        }
    }

    private record Profession(int profession, IntList careers) {
        private static Profession parse(String value) {
            String[] parts = value.split(":", -1);
            if (parts.length > 2) throw new IllegalArgumentException("Invalid profession: " + value);
            int profession = RandomConditions.profession(parts[0]);
            if (profession < 0) throw new IllegalArgumentException("Invalid profession: " + value);
            if (parts.length == 1) return new Profession(profession, null);

            IntList careers = new IntArrayList();
            for (String career : parts[1].split(",")) {
                if (!RandomConditions.career(profession, career, careers)) {
                    throw new IllegalArgumentException("Invalid career: " + value);
                }
            }
            return new Profession(profession, careers);
        }

        private boolean matches(int profession, int career) {
            return this.profession == profession && (careers == null || careers.contains(career));
        }
    }

    private enum Variant {
        HORSE(true), DONKEY(true), MULE(true), DISPENSER(false), DROPPER(false);

        private final boolean horse;

        Variant(boolean horse) {
            this.horse = horse;
        }

        private static Variant parse(String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private enum Container {
        ANVIL("anvil"),
        BEACON("beacon"),
        BREWING_STAND("brewing_stand"),
        CHEST("generic_54"),
        CRAFTING("crafting_table"),
        DISPENSER("dispenser"),
        ENCHANTMENT("enchanting_table"),
        FURNACE("furnace"),
        HOPPER("hopper"),
        HORSE("horse"),
        VILLAGER("villager"),
        CREATIVE(null),
        INVENTORY("inventory");

        private final Identifier defaultTexture;

        Container(String texture) {
            defaultTexture = texture == null ? null : new Identifier("textures/gui/container/" + texture + ".png");
        }

        private static Result<Container> parse(String value) {
            if (value == null) return Result.failure("Missing container");
            try {
                return Result.success(valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return Result.failure("Invalid container: " + value);
            }
        }

        private static Container of(Screen screen) {
            if (screen instanceof AnvilScreen) return ANVIL;
            if (screen instanceof BeaconScreen) return BEACON;
            if (screen instanceof BrewingStandScreen) return BREWING_STAND;
            if (screen instanceof ChestScreen) return CHEST;
            if (screen instanceof CraftingTableScreen) return CRAFTING;
            if (screen instanceof DispenserScreen) return DISPENSER;
            if (screen instanceof EnchantingTableScreen) return ENCHANTMENT;
            if (screen instanceof FurnaceScreen) return FURNACE;
            if (screen instanceof HopperScreen) return HOPPER;
            if (screen instanceof HorseScreen) return HORSE;
            if (screen instanceof VillagerScreen) return VILLAGER;
            if (screen instanceof CreativeInventoryScreen) return CREATIVE;
            if (screen instanceof SurvivalInventoryScreen) return INVENTORY;
            return null;
        }
    }

    private static boolean christmas() {
        LocalDate date = LocalDate.now();
        return date.getMonthValue() == 12 && date.getDayOfMonth() >= 24 && date.getDayOfMonth() <= 26;
    }

}
