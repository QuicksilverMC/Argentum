package dev.rdh.cera.modules.ctm;

import dev.rdh.cera.Cera;
import dev.rdh.cera.props.BlockMatcher;
import dev.rdh.cera.props.Patterns;
import dev.rdh.cera.props.Props;
import dev.rdh.cera.props.Result;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.Direction;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.rdh.cera.modules.ctm.CtmRule.Connect;
import static dev.rdh.cera.modules.ctm.CtmRule.Method;
import static dev.rdh.cera.modules.ctm.CtmRule.Tile;
import static dev.rdh.cera.modules.ctm.CtmRule.TileAction;

final class CtmRuleLoader {
    private static final String OPTIFINE_PREFIX = "optifine/ctm/";
    private static final String MCPATCHER_PREFIX = "mcpatcher/ctm/";
    private static final String DEFAULT_PREFIX = "mcpatcher/ctm/default/";
    private static final Pattern BLOCK_FILE = Pattern.compile("^block(\\d+).*");
    private static final Pattern RANGE = Pattern.compile("(-?\\d+)(?:-(-?\\d+))?");

    private CtmRuleLoader() {
    }

    static List<CtmRule> load(ResourceManager resources, TextureAtlas atlas,
            Map<String, TextureAtlasSprite> sourcedSprites) {
        List<CtmRule> rules = new ObjectArrayList<>();
        load(resources, OPTIFINE_PREFIX, atlas, sourcedSprites, rules);
        load(resources, MCPATCHER_PREFIX, atlas, sourcedSprites, rules);
        rules.sort(Comparator.comparingInt(CtmRule::weight).reversed().thenComparing(CtmRule::path));
        return List.copyOf(rules);
    }

    private static void load(ResourceManager resources, String directory, TextureAtlas atlas,
            Map<String, TextureAtlasSprite> sourcedSprites, List<CtmRule> rules) {
        for (var location : resources.findResources("minecraft", directory,
                id -> id.identifier().endsWith(".properties")).keySet()) {
            if (!defaultEnabled(resources, location.identifier())) continue;
            List<Resource> stack = resources.getResourceStack(location);
            if (stack.isEmpty()) continue;
            Resource resource = stack.getLast();
            try {
                CtmRule rule = parse(new Props(resource), atlas, sourcedSprites);
                if (rule != null) rules.add(rule);
            } catch (Exception e) {
                Cera.LOGGER.warn("[CTM] Failed to load rule {} from {}", location, resource.sourceName(), e);
            }
        }
    }

    private static boolean defaultEnabled(ResourceManager resources, String path) {
        if (!path.startsWith(DEFAULT_PREFIX)) return true;
        String name = path.substring(DEFAULT_PREFIX.length());
        String texture = name.startsWith("glass") ? "glass"
                : name.startsWith("bookshelf") ? "bookshelf"
                : name.startsWith("sandstone") ? "sandstone_normal"
                : name.substring(name.indexOf('_') + 1, name.indexOf('/'));
        return resources.getResource(new Identifier("textures/blocks/" + texture + ".png"))
                .map(resource -> "Default".equals(resource.sourceName())).orElse(false);
    }

    private static CtmRule parse(Props properties, TextureAtlas atlas,
            Map<String, TextureAtlasSprite> sourcedSprites) {
        String path = properties.id().identifier();
        String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".properties".length());
        Method method = Method.parse(properties.get("method", "ctm"));
        if (method == null) return null;

        int metadata = BlockMatcher.parseIntMask(properties.get("metadata"));
        Map<Block, Integer> matchBlocks = BlockMatcher.parseBlocks(properties.get("matchBlocks"));
        Set<String> matchTiles = parseMatchTiles(
                properties.get("matchTiles"), properties, atlas, sourcedSprites);

        boolean inferredBlock = false;
        if (!properties.contains("matchBlocks")) {
            Matcher matcher = BLOCK_FILE.matcher(name);
            if (matcher.matches()) {
                inferredBlock = true;
                Block block = Block.byId(Integer.parseInt(matcher.group(1)));
                if (block != null) matchBlocks.put(block, -1);
            }
        }
        if (!properties.contains("matchBlocks") && !inferredBlock && !properties.contains("matchTiles")) {
            TextureAtlasSprite sprite = register(atlas, sourcedSprites, new Identifier("blocks/" + name));
            matchTiles.add(sprite.getName());
        }
        if (matchBlocks.isEmpty() && matchTiles.isEmpty()) {
            Cera.LOGGER.warn("[CTM] No matchBlocks or matchTiles in {}", path);
            return null;
        }

        String tileValue = properties.get("tiles",
                method == Method.CTM ? "0-11 16-27 32-43 48-58"
                        : method == Method.CTM_COMPACT ? "0-4"
                        : method == Method.OVERLAY ? "0-16"
                        : method == Method.OVERLAY_CTM ? "0-11 16-27 32-43 48-58"
                        : method == Method.HORIZONTAL ? "12-15" : "");
        List<String> tileNames = expand(tileValue);
        int requiredTiles = requiredTiles(method, properties);
        if (requiredTiles < 1 || (method == Method.CTM || method == Method.CTM_COMPACT
                || method == Method.OVERLAY || method == Method.OVERLAY_CTM
                ? tileNames.size() < requiredTiles : tileNames.size() != requiredTiles)) {
            Cera.LOGGER.warn("[CTM] Invalid tile count for rule {}: expected {}, found {}", path, requiredTiles, tileNames.size());
            return null;
        }
        Tile[] tiles = new Tile[tileNames.size()];
        for (int i = 0; i < tiles.length; i++) {
            String tile = tileNames.get(i);
            if (tile.equals("<skip>") || tile.equals("<skip>.png")) {
                tiles[i] = new Tile(null, TileAction.SKIP);
            } else if (tile.equals("<default>") || tile.equals("<default>.png")) {
                if (method.overlay()) {
                    Cera.LOGGER.warn("[CTM] <default> is not valid for overlay rule {}", path);
                    return null;
                }
                tiles[i] = new Tile(null, TileAction.DEFAULT);
            } else {
                tiles[i] = new Tile(
                        register(atlas, sourcedSprites, resolveSprite(tile, properties, true)),
                        TileAction.REPLACE
                );
            }
        }

        Connect connect = switch (properties.get("connect", matchBlocks.isEmpty() ? "tile" : "block").trim()) {
            case "block" -> Connect.BLOCK;
            case "tile" -> Connect.TILE;
            case "material" -> Connect.MATERIAL;
            case "state" -> Connect.STATE;
            default -> null;
        };
        if (connect == null) {
            Cera.LOGGER.warn("[CTM] Invalid connect in {}", path);
            return null;
        }

        int[] weights = method == Method.RANDOM || method == Method.OVERLAY_RANDOM
                ? parseWeights(properties.get("weights"), tiles.length) : null;
        int randomLoops = value(properties.getInt("randomLoops", 0), 0);
        if ((method == Method.RANDOM || method == Method.OVERLAY_RANDOM)
                && (randomLoops < 0 || randomLoops > 9)) {
            Cera.LOGGER.warn("[CTM] Invalid randomLoops in {}", path);
            return null;
        }
        int symmetry = parseSymmetry(properties.get("symmetry"));
        boolean linked = value(properties.getBoolean("linked", false), false);
        int width = value(properties.getInt("width", -1), -1);
        int height = value(properties.getInt("height", -1), -1);

        BlockLayer layer = parseLayer(properties.get("layer", "cutout_mipped"));
        if (method.overlay() && layer == null) {
            Cera.LOGGER.warn("[CTM] Invalid overlay layer in {}", path);
            return null;
        }
        int tintIndex = value(properties.getInt("tintIndex", -1), -1);
        BlockState tintState = parseTintState(properties.get("tintBlock"));
        CtmRule.Action action = switch (method) {
            case CTM -> CtmRule.ctm();
            case CTM_COMPACT -> CtmRule.compact(parseCtmOverrides(properties, tiles.length));
            case HORIZONTAL -> CtmRule.horizontal();
            case VERTICAL -> CtmRule.vertical();
            case TOP -> CtmRule.top();
            case RANDOM -> CtmRule.random(weights, randomLoops, symmetry, linked);
            case REPEAT -> CtmRule.repeat(width, height, symmetry);
            case FIXED -> CtmRule.fixed();
            case HORIZONTAL_VERTICAL -> CtmRule.combined(true);
            case VERTICAL_HORIZONTAL -> CtmRule.combined(false);
            case OVERLAY -> CtmRule.overlay(tintIndex, tintState, layer);
            case OVERLAY_FIXED -> CtmRule.overlay(CtmRule.fixed(), tintIndex, tintState, layer);
            case OVERLAY_RANDOM -> CtmRule.overlay(
                    CtmRule.random(weights, randomLoops, symmetry, linked),
                    tintIndex, tintState, layer);
            case OVERLAY_REPEAT -> CtmRule.overlay(
                    CtmRule.repeat(width, height, symmetry), tintIndex, tintState, layer);
            case OVERLAY_CTM -> CtmRule.overlay(CtmRule.ctm(), tintIndex, tintState, layer);
        };

        Object2IntMap<Block> connectBlocks = BlockMatcher.parseBlocks(properties.get("connectBlocks"));
        connectBlocks.defaultReturnValue(0);

        return new CtmRule(
                path,
                value(properties.getInt("weight", 0), 0),
                Map.copyOf(matchBlocks),
                Set.copyOf(matchTiles),
                metadata,
                parseFaces(properties.get("faces")),
                parseHeights(properties),
                parseBiomes(properties.get("biomes")),
                Patterns.matcher(properties.get("name")),
                value(properties.getBoolean("innerSeams", false), false),
                connect,
                tiles,
                action,
                maxMetadata(metadata, matchBlocks),
                Object2IntMaps.unmodifiable(connectBlocks),
                Set.copyOf(parseMatchTiles(properties.get("connectTiles"), properties, atlas, sourcedSprites))
        );
    }

    private static int requiredTiles(Method method, Props properties) {
        return switch (method) {
            case CTM, OVERLAY_CTM -> 47;
            case CTM_COMPACT -> 5;
            case OVERLAY -> 17;
			case HORIZONTAL, VERTICAL -> 4;
            case TOP, FIXED, OVERLAY_FIXED -> 1;
            case HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL -> 7;
            case RANDOM, OVERLAY_RANDOM -> expand(properties.get("tiles", "")).size();
            case REPEAT, OVERLAY_REPEAT -> {
                int width = value(properties.getInt("width", -1), -1);
                int height = value(properties.getInt("height", -1), -1);
                yield width > 0 && height > 0 ? width * height : -1;
            }
        };
    }

    private static int[] parseCtmOverrides(Props properties, int tileCount) {
        int[] overrides = new int[47];
        Arrays.fill(overrides, -1);
        for (String key : properties.properties().stringPropertyNames()) {
            if (!key.startsWith("ctm.")) continue;
            int index = parseInt(key.substring("ctm.".length()), -1);
            int tile = value(properties.getInt(key, -1), -1);
            if (index >= 0 && index < overrides.length && tile >= 0 && tile < tileCount) {
                overrides[index] = tile;
            }
        }
        return overrides;
    }

    private static BlockState parseTintState(String value) {
        if (value == null) return Blocks.AIR.defaultState();
        Map<Block, Integer> blocks = BlockMatcher.parseBlocks(value);
        if (blocks.isEmpty()) return Blocks.AIR.defaultState();
        var entry = blocks.entrySet().iterator().next();
        int metadata = entry.getValue() == -1 ? 0 : Integer.numberOfTrailingZeros(entry.getValue());
        return entry.getKey().getStateFromMetadata(metadata);
    }

    private static BlockLayer parseLayer(String value) {
        return switch (value.trim()) {
            case "cutout_mipped" -> BlockLayer.CUTOUT_MIPPED;
            case "cutout" -> BlockLayer.CUTOUT;
            case "translucent" -> BlockLayer.TRANSLUCENT;
            default -> null;
        };
    }

    private static Set<String> parseMatchTiles(String value, Props properties, TextureAtlas atlas,
            Map<String, TextureAtlasSprite> sourcedSprites) {
        if (value == null) return new ObjectOpenHashSet<>();
        Set<String> tiles = new ObjectOpenHashSet<>();
        for (String token : value.trim().split("[ ,]+")) {
            if (!token.isEmpty()) {
                tiles.add(register(atlas, sourcedSprites, resolveSprite(token, properties, false)).getName());
            }
        }
        return tiles;
    }

    private static TextureAtlasSprite register(TextureAtlas atlas, Map<String, TextureAtlasSprite> sourcedSprites, Identifier id) {
        TextureAtlasSprite sprite = sourcedSprites.get(id.toString());
        return sprite == null ? atlas.registerSprite(id) : sprite;
    }

    private static List<String> expand(String value) {
        List<String> result = new ObjectArrayList<>();
        for (String token : value.trim().split("[ ,]+")) {
            int separator = token.indexOf('-');
            if (separator > 0) {
                try {
                    int min = Integer.parseInt(token.substring(0, separator));
                    int max = Integer.parseInt(token.substring(separator + 1));
                    if (min <= max) {
                        for (int i = min; i <= max; i++) result.add(Integer.toString(i));
                        continue;
                    }
                } catch (NumberFormatException _) {
                }
            }
            if (!token.isEmpty()) result.add(token);
        }
        return result;
    }

    private static Identifier resolveSprite(String value, Props properties, boolean relative) {
        String path = value;
        int separator = value.indexOf(':');
        if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);
        if (path.startsWith("/")) path = "~/" + path.substring(1);
        if (!relative && separator < 0 && !path.contains("/")) path = "blocks/" + path;

        Identifier id = properties.parseId(path);
        path = id.getPath();
        if (path.startsWith("textures/")) path = path.substring("textures/".length());
        if (separator >= 0 && !path.contains("/")) path = "blocks/" + path;
        return new Identifier(id.getNamespace(), path);
    }

    private static int[] parseWeights(String value, int size) {
        if (value == null) return null;
        int[] parsed = Arrays.stream(value.trim().split("[ ,]+"))
                .filter(token -> !token.isEmpty())
                .mapToInt(token -> parseInt(token, 0))
                .toArray();
        int[] weights = Arrays.copyOf(parsed, size);
        if (parsed.length < size) {
            int average = parsed.length == 0 ? 0 : Arrays.stream(parsed).sum() / parsed.length;
            Arrays.fill(weights, parsed.length, size, average);
        }
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += weights[i];
            weights[i] = sum;
        }
        return weights;
    }

    private static int parseSymmetry(String value) {
        if (value == null || value.equals("none")) return 1;
        return switch (value.trim()) {
            case "opposite" -> 2;
            case "all" -> 6;
            default -> 1;
        };
    }


    private static int parseFaces(String value) {
        if (value == null) return 63;
        int faces = 0;
        for (String face : value.toLowerCase(Locale.ROOT).split("[ ,]+")) {
            faces |= switch (face) {
                case "bottom", "down" -> 1 << Direction.DOWN.ordinal();
                case "top", "up" -> 1 << Direction.UP.ordinal();
                case "north" -> 1 << Direction.NORTH.ordinal();
                case "south" -> 1 << Direction.SOUTH.ordinal();
                case "west" -> 1 << Direction.WEST.ordinal();
                case "east" -> 1 << Direction.EAST.ordinal();
                case "sides" -> (1 << Direction.NORTH.ordinal()) | (1 << Direction.SOUTH.ordinal())
                        | (1 << Direction.WEST.ordinal()) | (1 << Direction.EAST.ordinal());
                case "all" -> 63;
                default -> 0;
            };
        }
        return faces;
    }

    private static IntPredicate parseHeights(Props properties) {
        String heights = properties.get("heights");
        if (heights != null) {
            List<int[]> parsed = new ObjectArrayList<>();
            for (String token : heights.trim().split("[ ,]+")) {
                Matcher matcher = RANGE.matcher(token);
                if (matcher.matches()) {
                    int first = Integer.parseInt(matcher.group(1));
                    int second = matcher.group(2) == null ? first : Integer.parseInt(matcher.group(2));
                    parsed.add(new int[]{Math.min(first, second), Math.max(first, second)});
                }
            }
            List<int[]> ranges = List.copyOf(parsed);
            return y -> {
                for (int[] range : ranges) {
                    if (y >= range[0] && y <= range[1]) return true;
                }
                return false;
            };
        }
        int min = value(properties.getInt("minHeight", Integer.MIN_VALUE), Integer.MIN_VALUE);
        int max = value(properties.getInt("maxHeight", Integer.MAX_VALUE), Integer.MAX_VALUE);
        return y -> y >= min && y <= max;
    }

    private static <T> T value(Result<T> result, T fallback) {
        if (!result.isSuccess()) Cera.LOGGER.warn("[CTM] {}", result.error());
        return result.orElse(fallback);
    }

    private static Set<String> parseBiomes(String value) {
        if (value == null) return Set.of();
        Set<String> biomes = new ObjectOpenHashSet<>();
        for (String biome : value.split("[ ,]+")) {
            if (!biome.isEmpty()) biomes.add(normalizeBiome(biome));
        }
        return Set.copyOf(biomes);
    }

    static String normalizeBiome(String biome) {
        return biome.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException _) {
            return fallback;
        }
    }

    private static int maxMetadata(int metadata, Map<Block, Integer> blocks) {
        int max = metadata == -1 ? -1 : 31 - Integer.numberOfLeadingZeros(metadata);
        for (int mask : blocks.values()) {
            if (mask != -1) max = Math.max(max, 31 - Integer.numberOfLeadingZeros(mask));
        }
        return max;
    }
}
