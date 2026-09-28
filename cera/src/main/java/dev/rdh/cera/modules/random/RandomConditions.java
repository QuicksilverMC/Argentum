package dev.rdh.cera.modules.random;

import dev.rdh.cera.Cera;
import dev.rdh.cera.modules.cit.NbtMatcher;
import dev.rdh.cera.props.NumberList;
import dev.rdh.cera.props.Patterns;
import dev.rdh.cera.props.Props;
import dev.rdh.cera.props.Result;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.DyeColor;
import net.minecraft.world.biome.Biome;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static dev.rdh.cera.props.Props.normalize;

public final class RandomConditions {
    private static final Object2IntMap<String> PROFESSION_IDS = new Object2IntOpenHashMap<>();
    private static final Int2ObjectMap<Object2IntMap<String>> CAREER_IDS = new Int2ObjectOpenHashMap<>();
    private static final int MAX_CAREER = 64;
    private static final Map<String, DyeColor> DYE_COLORS = new Object2ObjectOpenHashMap<>();

    static {
        PROFESSION_IDS.defaultReturnValue(-1);
        putProfession(0, "farmer", "farmer", "fisherman", "shepherd", "fletcher");
        putProfession(1, "librarian", "librarian", "cartographer");
        putProfession(2, "priest", "cleric");
        putProfession(3, "blacksmith", "armor", "weapon", "tool");
        putProfession(4, "butcher", "butcher", "leather");
        putProfession(5, "nitwit", "nitwit");
        for (DyeColor color : DyeColor.values()) {
            DYE_COLORS.put(normalize(color.name()), color);
        }
    }

    private RandomConditions() {
    }

    private static void putProfession(int id, String name, String... careers) {
        PROFESSION_IDS.put(name, id);
        Object2IntMap<String> table = new Object2IntOpenHashMap<>();
        table.defaultReturnValue(-1);
        for (int i = 0; i < careers.length; i++) {
            table.put(normalize(careers[i]), i + 1);
        }
        CAREER_IDS.put(id, table);
    }

    static Function<Integer, Predicate<Subject>> reader(Props props) {
        return n -> build(props, n);
    }

    private static Predicate<Subject> build(Props props, int n) {
        Predicate<Subject> predicate = null;
        predicate = and(predicate, biomes(props, n));
        predicate = and(predicate, heights(props, n));
        predicate = and(predicate, health(props, n));
        predicate = and(predicate, name(props, n));
        predicate = and(predicate, professions(props, n));
        predicate = and(predicate, colors(props, n));
        predicate = and(predicate, baby(props, n));
        predicate = and(predicate, moonPhase(props, n));
        predicate = and(predicate, dayTime(props, n));
        predicate = and(predicate, weather(props, n));
        predicate = and(predicate, sizes(props, n));
        predicate = and(predicate, nbt(props, n));
        predicate = and(predicate, blocks(props, n));
        return predicate;
    }

    private static Predicate<Subject> and(Predicate<Subject> first, Predicate<Subject> second) {
        return second == null ? first : first == null ? second : first.and(second);
    }

    private static String value(Props props, String key) {
        String value = props.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static NumberList numbers(Props props, String key) {
        Result<NumberList> result = props.getNumberList(key);
        if (!result.isSuccess()) {
            Cera.LOGGER.warn("[RandomEntities] {}: {}", key, result.error());
            return null;
        }
        return result.value();
    }

    private static Predicate<Subject> biomes(Props props, int n) {
        String value = value(props, "biomes." + n);
        if (value == null) return null;
        Set<String> names = new ObjectOpenHashSet<>();
        for (String token : value.split("\\s+")) {
            String name = Props.biome(token);
            if (!name.isEmpty()) names.add(name);
        }
        if (names.isEmpty()) return null;
        return subject -> {
            Biome biome = subject.spawnBiome();
            return biome != null && names.contains(Props.biome(biome.name));
        };
    }

    private static Predicate<Subject> heights(Props props, int n) {
        NumberList parsed = numbers(props, "heights." + n);
        if (parsed == null) parsed = legacyHeights(props, n);
        if (parsed == null) return null;
        NumberList range = parsed;
        return subject -> {
            var pos = subject.spawnPos();
            return pos != null && range.contains(pos.getY());
        };
    }

    /** Legacy {@code minHeight.N}/{@code maxHeight.N} properties, as accepted by OptiFine. */
    private static NumberList legacyHeights(Props props, int n) {
        String min = value(props, "minHeight." + n);
        String max = value(props, "maxHeight." + n);
        if (min == null && max == null) return null;
        try {
            int minHeight = min == null ? 0 : Integer.parseInt(min);
            int maxHeight = max == null ? 256 : Integer.parseInt(max);
            if (minHeight < 0 || maxHeight < 0 || maxHeight < minHeight) throw new NumberFormatException();
            Result<NumberList> result = NumberList.parse(minHeight + "-" + maxHeight);
            if (!result.isSuccess()) throw new NumberFormatException();
            return result.value();
        } catch (NumberFormatException _) {
            Cera.LOGGER.warn("[RandomEntities] Invalid minHeight/maxHeight.{}", n);
            return null;
        }
    }

    private static Predicate<Subject> health(Props props, int n) {
        String value = value(props, "health." + n);
        if (value == null) return null;
        boolean percent = value.indexOf('%') >= 0;
        Result<NumberList> result = NumberList.parse(value.replace("%", ""));
        if (!result.isSuccess()) {
            Cera.LOGGER.warn("[RandomEntities] health.{}: {}", n, result.error());
            return null;
        }
        NumberList range = result.value();
        return subject -> {
            if (!subject.isLiving()) return false;
            int health = subject.health();
            if (percent && subject.maxHealth() > 0) {
                health = (int) ((long) health * 100 / subject.maxHealth());
            }
            return range.contains(health);
        };
    }

    private static Predicate<Subject> name(Props props, int n) {
        String value = value(props, "name." + n);
        if (value == null) return null;
        Predicate<String> matcher = Patterns.matcher(value);
        return subject -> matcher.test(subject.name());
    }

    private static Predicate<Subject> professions(Props props, int n) {
        String value = value(props, "professions." + n);
        if (value == null) return null;
        List<Entry> entries = new ArrayList<>();
        for (String token : value.split("\\s+")) {
            String[] parts = token.split(":", 2);
            int profession = profession(parts[0]);
            if (profession == -1) {
                Cera.LOGGER.warn("[RandomEntities] professions.{}: unknown profession {}", n, parts[0]);
                continue;
            }
            IntList careers = careers(profession, parts.length > 1 ? parts[1] : null, n);
            entries.add(new Entry(profession, careers));
        }
        if (entries.isEmpty()) return null;
        return subject -> {
            int profession = subject.profession();
            if (profession < 0) return false;
            for (Entry entry : entries) {
                if (entry.profession() == profession
                        && (entry.careers() == null || entry.careers().contains(subject.career()))) {
                    return true;
                }
            }
            return false;
        };
    }

    private record Entry(int profession, IntList careers) {
    }

    public static int profession(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException _) {
            return PROFESSION_IDS.getInt(normalize(value));
        }
    }

    public static boolean career(int profession, String token, IntList out) {
        String trimmed = token.trim();
        if (trimmed.isEmpty()) return true;

        Result<NumberList> parsed = NumberList.parse(trimmed);
        if (parsed.isSuccess()) {
            NumberList list = parsed.value();
            for (int i = 0; i < list.rangeCount(); i++) {
                long range = list.range(i);
                int end = Math.min(NumberList.end(range), MAX_CAREER);
                for (int career = NumberList.start(range); career <= end; career++) {
                    out.add(career);
                }
            }
            return true;
        }

        Object2IntMap<String> table = CAREER_IDS.get(profession);
        int id = table == null ? -1 : table.getInt(normalize(trimmed));
        if (id == -1) return false;
        out.add(id);
        return true;
    }

    private static IntList careers(int profession, String value, int n) {
        if (value == null || value.isBlank()) return null;
        IntList careers = new IntArrayList();
        for (String token : value.split(",")) {
            if (!career(profession, token, careers)) {
                Cera.LOGGER.warn("[RandomEntities] professions.{}: unknown career {}", n, token);
            }
        }
        return careers.isEmpty() ? null : careers;
    }

    private static Predicate<Subject> colors(Props props, int n) {
        String value = value(props, "colors." + n);
        if (value == null) value = value(props, "collarColors." + n);
        if (value == null) return null;
        Set<DyeColor> colors = new ObjectOpenHashSet<>();
        for (String token : value.split("\\s+")) {
            DyeColor color = DYE_COLORS.get(normalize(token));
            if (color == null) {
                Cera.LOGGER.warn("[RandomEntities] colors.{}: unknown color {}", n, token);
            } else {
                colors.add(color);
            }
        }
        if (colors.isEmpty()) return null;
        return subject -> {
            DyeColor color = subject.color();
            return color != null && colors.contains(color);
        };
    }

    private static Predicate<Subject> baby(Props props, int n) {
        Result<Boolean> result = props.getBoolean("baby." + n);
        if (result.isSuccess() && result.value() == null) return null;
        Boolean expected = result.orElse(null);
        if (expected == null) {
            Cera.LOGGER.warn("[RandomEntities] baby.{}: {}", n, result.error());
            return null;
        }
        return subject -> expected.equals(subject.baby());
    }

    private static Predicate<Subject> moonPhase(Props props, int n) {
        NumberList range = numbers(props, "moonPhase." + n);
        if (range == null) return null;
        return subject -> {
            var world = subject.world();
            return world != null && range.contains(world.getMoonPhase());
        };
    }

    private static Predicate<Subject> dayTime(Props props, int n) {
        NumberList range = numbers(props, "dayTime." + n);
        if (range == null) return null;
        return subject -> {
            var world = subject.world();
            return world != null && range.contains((int) (world.getTimeOfDay() % 24000L));
        };
    }

    private static Predicate<Subject> weather(Props props, int n) {
        String value = value(props, "weather." + n);
        if (value == null) return null;
        boolean clear = false, rain = false, thunder = false;
        for (String token : value.toLowerCase(Locale.ROOT).split("\\s+")) {
            switch (token) {
                case "clear" -> clear = true;
                case "rain" -> rain = true;
                case "thunder" -> thunder = true;
                default -> Cera.LOGGER.warn("[RandomEntities] weather.{}: unknown weather {}", n, token);
            }
        }
        if (!clear && !rain && !thunder) return null;
        boolean anyClear = clear, anyRain = rain, anyThunder = thunder;
        return subject -> {
            var world = subject.world();
            if (world == null) return false;
            boolean raining = world.isRaining();
            boolean thundering = world.isThundering();
            return anyClear && !raining || anyRain && raining && !thundering || anyThunder && thundering;
        };
    }

    private static Predicate<Subject> sizes(Props props, int n) {
        NumberList range = numbers(props, "sizes." + n);
        if (range == null) return null;
        return subject -> subject.slimeSize() >= 0 && range.contains(subject.slimeSize());
    }

    private static Predicate<Subject> nbt(Props props, int n) {
        String prefix = "nbt." + n + ".";
        List<NbtMatcher> matchers = null;
        for (String key : props.properties().stringPropertyNames()) {
            if (!key.startsWith(prefix)) continue;
            try {
                if (matchers == null) matchers = new ArrayList<>();
                matchers.add(NbtMatcher.parse(key.substring(prefix.length()), props.get(key)));
            } catch (IllegalArgumentException e) {
                Cera.LOGGER.warn("[RandomEntities] {}: {}", key, e.getMessage());
            }
        }
        if (matchers == null) return null;
        List<NbtMatcher> frozen = List.copyOf(matchers);
        return subject -> {
            for (NbtMatcher matcher : frozen) {
                if (!matcher.matches(subject.nbt())) return false;
            }
            return true;
        };
    }

    private static Predicate<Subject> blocks(Props props, int n) {
        String value = value(props, "blocks." + n);
        if (value == null) return null;
        List<BlockEntry> entries = new ArrayList<>();
        for (String token : value.split("\\s+")) {
            int meta = -1;
            String specs = token;
            int colon = token.lastIndexOf(':');
            if (colon >= 0 && isNumeric(token.substring(colon + 1))) {
                meta = Integer.parseInt(token.substring(colon + 1));
                specs = token.substring(0, colon);
            }
            for (String name : specs.split(",")) {
                Block block = parseBlock(name);
                if (block == null) {
                    Cera.LOGGER.warn("[RandomEntities] blocks.{}: unknown block {}", n, name);
                } else {
                    entries.add(new BlockEntry(block, meta));
                }
            }
        }
        if (entries.isEmpty()) return null;
        return subject -> {
            BlockState state = subject.blockState();
            if (state == null) return false;
            for (BlockEntry entry : entries) {
                if (state.getBlock() == entry.block()
                        && (entry.metadata() == -1 || metadataOf(state, entry.block()) == entry.metadata())) {
                    return true;
                }
            }
            return false;
        };
    }

    private record BlockEntry(Block block, int metadata) {
    }

    private static boolean isNumeric(String value) {
        if (value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

    private static Block parseBlock(String value) {
        try {
            return Block.byId(Integer.parseInt(value));
        } catch (NumberFormatException _) {
            return Block.byKey(value);
        }
    }

    private static int metadataOf(BlockState state, Block block) {
        for (int metadata = 0; metadata < 16; metadata++) {
            try {
                if (block.getStateFromMetadata(metadata) == state) return metadata;
            } catch (IllegalArgumentException _) {
            }
        }
        return -1;
    }
}
