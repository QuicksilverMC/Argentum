package dev.rdh.cera.modules.random;

import dev.rdh.cera.Cera;
import dev.rdh.cera.props.Props;
import dev.rdh.cera.props.Result;
import dev.rdh.cera.props.RandomRules;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resource.Identifier;
import net.minecraft.world.World;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;
import net.ornithemc.osl.resource.loader.api.resource.reload.ResourceReloadListener;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public final class RandomEntities implements ResourceReloadListener {
    private static final String ROOT_OPTIFINE = "optifine/random/";
    private static final String ROOT_MCPATCHER = "mcpatcher/mob/";
    private static final String[] DEPENDANT_SUFFIXES = {
            "_armor", "_eyes", "_exploding", "_shooting", "_fur", "_invulnerable", "_angry", "_tame", "_collar"
    };
    private static final int CACHE_LIMIT = 4096;

    private volatile Map<String, Base> bases = Map.of();
    private final Object2ObjectOpenHashMap<Object, Reference2ObjectArrayMap<Base, Entry>> cache = new Object2ObjectOpenHashMap<>();
    private final Set<String> unevaluated = new ObjectOpenHashSet<>();

    @Override
    public void resourcesReloaded(ResourceManager resources) {
        this.cache.clear();
        this.unevaluated.clear();
        Map<String, Base> loaded = new Object2ObjectOpenHashMap<>();
        scan(resources, ROOT_OPTIFINE, "textures/", loaded);
        scan(resources, ROOT_MCPATCHER, "textures/entity/", loaded);
        this.bases = Map.copyOf(loaded);
        this.unevaluated.addAll(loaded.keySet());
        Cera.LOGGER.info("[RandomEntities] Loaded {} texture groups", loaded.size());
    }

    public boolean hasVariants(Identifier id) {
        return Cera.CONFIG.randomEntities && this.bases.containsKey(id.getPath());
    }

    /**
     * Returns the texture to bind instead of {@code id}, or {@code id} when it has no random variants.
     */
    public Identifier apply(Identifier id) {
        if (this.bases.isEmpty() || !Cera.CONFIG.randomEntities) return id;
        String path = id.getPath();
        if (!path.startsWith("textures/entity/") && !path.startsWith("textures/painting/")) return id;
        Base base = this.bases.get(path);
        if (base == null) return id;
        Subject subject = Subject.current();
        if (subject == null) return id;
        int variant = select(subject, base);
        if (this.unevaluated.remove(path)) {
            Cera.LOGGER.info("[RandomEntities] {} -> variant {}/{}", path, variant, base.count());
        }
        if (variant <= 1) return id;
        Identifier file = base.variant(variant);
        return file == null ? id : file;
    }

    private int select(Subject subject, Base base) {
        Reference2ObjectArrayMap<Base, Entry> entries = this.cache.get(subject.key());
        Entry entry = entries == null ? null : entries.get(base);
        World world = subject.world();
        long now = world == null ? 0L : world.getTime();
        if (entry != null && (base.rules() == null || now < entry.expiresAt())) return entry.variant();

        int seed = subject.seed();
        int variant = base.rules() == null
                ? Math.floorMod(seed, base.count()) + 1
                : base.rules().select(subject, seed);
        if (entries == null) this.cache.put(subject.key(), entries = new Reference2ObjectArrayMap<>(2));
        entries.put(base, new Entry(variant, base.rules() == null ? Long.MAX_VALUE : now + 20));
        if (this.cache.size() > CACHE_LIMIT) this.cache.clear();
        return variant;
    }

    private static void scan(ResourceManager resources, String root, String basePrefix, Map<String, Base> loaded) {
        Map<String, Group> groups = new Object2ObjectOpenHashMap<>();
        resources.findResources(root, id -> id.identifier().endsWith(".png")).forEach((id, _) -> {
            String relative = id.identifier().substring(root.length());
            int slash = relative.lastIndexOf('/');
            String directory = slash < 0 ? "" : relative.substring(0, slash + 1);
            String stem = slash < 0 ? relative : relative.substring(slash + 1);
            stem = stem.substring(0, stem.length() - ".png".length());

            int digits = stem.length();
            while (digits > 0 && Character.isDigit(stem.charAt(digits - 1))) digits--;
            if (digits == 0 || digits == stem.length()) return;
            int number;
            try {
                number = Integer.parseInt(stem.substring(digits));
            } catch (NumberFormatException _) {
                return;
            }
            if (number < 2) return;

            String basePath = basePrefix + directory + stem.substring(0, digits) + ".png";
            Group group = groups.get(basePath);
            if (group == null) {
                group = new Group(id.namespace(), directory, stem.substring(0, digits));
                groups.put(basePath, group);
            }
            group.numbers.add(number);
        });

        for (Map.Entry<String, Group> entry : groups.entrySet()) {
            String basePath = entry.getKey();
            Group group = entry.getValue();
            if (resources.getResource(new Identifier(group.namespace(), basePath)).isEmpty()) continue;

            Int2ObjectMap<Identifier> files = new Int2ObjectOpenHashMap<>();
            for (int number : group.numbers()) {
                files.put(number, new Identifier(group.namespace(), root + group.directory() + group.stem() + number + ".png"));
            }
            RandomRules<Subject> rules = rules(resources, group, root);
            loaded.put(basePath, new Base(files, rules));
            Cera.LOGGER.info("[RandomEntities] {}: {} variant(s), {}", basePath, files.size() + 1,
                    rules == null || rules.isEmpty() ? "no rules" : "with rules");
        }
    }

    private static RandomRules<Subject> rules(ResourceManager resources, Group group, String root) {
        Props props = properties(resources, group.namespace(), root, group.directory(), group.stem());
        if (props == null) {
            for (String suffix : DEPENDANT_SUFFIXES) {
                if (group.stem().endsWith(suffix)) {
                    props = properties(resources, group.namespace(), root, group.directory(),
                            group.stem().substring(0, group.stem().length() - suffix.length()));
                    if (props != null) break;
                }
            }
        }
        if (props == null) return null;
        return parseRules(props);
    }

    private static RandomRules<Subject> parseRules(Props props) {
        Result<RandomRules<Subject>> rules = RandomRules.parse(props, "textures", RandomConditions.reader(props));
        if (rules.isSuccess() && !rules.value().isEmpty()) return rules.value();
        if (hasRules(props, "skins")) {
            Result<RandomRules<Subject>> skins = RandomRules.parse(props, "skins", RandomConditions.reader(props));
            if (skins.isSuccess() && !skins.value().isEmpty()) return skins.value();
            if (!skins.isSuccess()) rules = skins;
        }
        if (!rules.isSuccess()) {
            Cera.LOGGER.warn("[RandomEntities] {}: {}", props.id(), rules.error());
        }
        return null;
    }

    private static boolean hasRules(Props props, String key) {
        for (String name : props.properties().stringPropertyNames()) {
            if (name.startsWith(key + ".")) return true;
        }
        return false;
    }

    private static Props properties(ResourceManager resources, String namespace, String root, String directory, String stem) {
        Resource resource = resources.getResource(new Identifier(namespace, root + directory + stem + ".properties")).orElse(null);
        if (resource == null) return null;
        try {
            return new Props(resource);
        } catch (IOException e) {
            Cera.LOGGER.warn("[RandomEntities] Failed to read {} properties", stem, e);
            return null;
        }
    }

    private record Group(String namespace, String directory, String stem, IntSet numbers) {
        Group(String namespace, String directory, String stem) {
            this(namespace, directory, stem, new IntOpenHashSet());
        }
    }

    private record Base(Int2ObjectMap<Identifier> variants, RandomRules<Subject> rules) {
        int count() {
            return this.variants.size() + 1;
        }

        Identifier variant(int number) {
            return this.variants.get(number);
        }
    }

    private record Entry(int variant, long expiresAt) {
    }
}
