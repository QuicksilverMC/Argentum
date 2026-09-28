package dev.rdh.cera.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import dev.rdh.cera.props.Props;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;
import net.ornithemc.osl.resource.loader.api.resource.reload.ResourceReloadListener;
import org.embeddedt.embeddium.impl.model.quad.BakedQuadView;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;

import dev.rdh.cera.Cera;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.io.IOException;
import java.util.Map;

public final class NaturalTextures implements ResourceReloadListener {
    private static final Identifier CONFIG = new Identifier("optifine/natural.properties");
    private static final Identifier LEGACY_CONFIG = new Identifier("mcpatcher/natural.properties");
    private volatile Map<String, Rule> rules = Map.of();
    private volatile Map<TextureAtlasSprite, Rule> spriteRules = Map.of();

    @Override
    public void resourcesReloaded(ResourceManager resources) {
        try {
            Resource config = get(resources, CONFIG);
            boolean defaults = resources.getResourceStack(CONFIG).size() == 1;
            if (defaults) {
                Resource legacyConfig = get(resources, LEGACY_CONFIG);
                if (legacyConfig != null) {
                    config = legacyConfig;
                    defaults = false;
                }
            }
            if (config == null) {
                rules = Map.of();
                spriteRules = Map.of();
                return;
            }

            Props properties = new Props(config);

            Map<String, Rule> loaded = new Object2ObjectOpenHashMap<>();
            for (String texture : properties.properties().stringPropertyNames()) {
                Rule rule = Rule.parse(properties.get(texture));
                if (rule == null) {
                    Cera.LOGGER.warn("[NaturalTextures] Invalid rule: {}={}", texture, properties.get(texture));
                } else if (!defaults || isVanillaTexture(resources, texture)) {
                    loaded.put("minecraft:blocks/" + texture, rule);
                }
            }
            rules = Map.copyOf(loaded);
            bake(Minecraft.getInstance().getBlocksAtlas());
            Cera.LOGGER.info("[NaturalTextures] Loaded {} rules", rules.size());
        } catch (IOException e) {
            rules = Map.of();
            spriteRules = Map.of();
            Cera.LOGGER.warn("[NaturalTextures] Failed to load properties", e);
        }
    }

    public void bake(TextureAtlas atlas) {
        Map<TextureAtlasSprite, Rule> resolved = new Reference2ObjectOpenHashMap<>();
        for (Map.Entry<String, Rule> entry : this.rules.entrySet()) {
            TextureAtlasSprite sprite = atlas.getSprite(entry.getKey());
            if (sprite != atlas.getMissingSprite()) resolved.put(sprite, entry.getValue());
        }
        this.spriteRules = resolved;
    }

    public int getTransform(BakedQuadView quad, BlockPos pos) {
        if (!Cera.CONFIG.naturalTextures) return 0;

        TextureAtlasSprite sprite = (TextureAtlasSprite)quad.celeritas$getSprite();
        Rule rule = sprite == null ? null : spriteRules.get(sprite);
        if (rule == null) return 0;

        int random = Cera.random(pos, side(quad.getLightFace()));
        int rotation = switch (rule.rotations) {
            case 4 -> random & 3;
            case 2 -> random & 2;
            default -> 0;
        };
        boolean flip = rule.flip && (random & 4) != 0;
        if (rotation != 0 && !isFullSprite(quad, sprite)) rotation = 0;
        return rotation | (flip ? 4 : 0);
    }

    public static int transformVertex(int transform, int vertex) {
        int rotation = transform & 3;
        boolean flip = (transform & 4) != 0;
        return flip ? (3 - rotation - vertex) & 3 : (vertex + rotation) & 3;
    }

    private static Resource get(ResourceManager resources, Identifier id) {
        return resources.getResource(id).orElse(null);
    }

    private static boolean isVanillaTexture(ResourceManager resources, String texture) {
        Resource resource = get(resources, new Identifier("textures/blocks/" + texture + ".png"));
        if (resource == null) return false;
        try (resource) {
            return "Default".equals(resource.sourceName());
        } catch (IOException _) {
            return false;
        }
    }

    private static boolean isFullSprite(BakedQuadView quad, TextureAtlasSprite sprite) {
        float toleranceU = (sprite.getUMax() - sprite.getUMin()) / 256.0F;
        float toleranceV = (sprite.getVMax() - sprite.getVMin()) / 256.0F;
        for (int vertex = 0; vertex < 4; vertex++) {
            float u = quad.getTexU(vertex);
            float v = quad.getTexV(vertex);
            if (!nearEdge(u, sprite.getUMin(), sprite.getUMax(), toleranceU)
                    || !nearEdge(v, sprite.getVMin(), sprite.getVMax(), toleranceV)) {
                return false;
            }
        }
        return true;
    }

    private static boolean nearEdge(float value, float min, float max, float tolerance) {
        return Math.abs(value - min) < tolerance || Math.abs(value - max) < tolerance;
    }

    private static int side(ModelQuadFacing face) {
        return switch (face) {
            case NEG_Y -> 0;
            case POS_Y -> 1;
            case NEG_Z -> 2;
            case POS_Z -> 3;
            case NEG_X -> 4;
            case POS_X -> 5;
            default -> -1;
        };
    }

    private record Rule(int rotations, boolean flip) {
        private static Rule parse(String value) {
            return switch (value.trim()) {
                case "2" -> new Rule(2, false);
                case "4" -> new Rule(4, false);
                case "F" -> new Rule(1, true);
                case "2F" -> new Rule(2, true);
                case "4F" -> new Rule(4, true);
                default -> null;
            };
        }
    }
}
