package dev.rdh.cera.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rdh.cera.Cera;
import dev.rdh.cera.modules.cem.PartParser;
import dev.rdh.cera.props.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.entity.layer.EntityRenderLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.HttpImageProcessor;
import net.minecraft.client.render.texture.HttpTexture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class OptifineCosmetics {
    private static final String SERVER = "https://optifine.net";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]+");

    private final Map<String, Cape> capes = new ConcurrentHashMap<>();
    private final Map<String, List<Cosmetic>> cosmetics = new ConcurrentHashMap<>();
    private final Set<String> requested = ConcurrentHashMap.newKeySet();

    public Optional<Identifier> cape(ClientPlayerEntity player) {
        if (!Cera.CONFIG.optifineCosmetics) return Optional.empty();
        String name = player.getGameProfile().getName();
        if (name == null || !USERNAME.matcher(name).matches()) return Optional.empty();
        Cape cape = this.capes.computeIfAbsent(name, Cape::new);
        cape.register();
        return cape.loaded ? Optional.of(cape.location) : Optional.empty();
    }

    private List<Cosmetic> cosmetics(ClientPlayerEntity player) {
        if (!Cera.CONFIG.optifineCosmetics) return List.of();
        String name = player.getGameProfile().getName();
        if (name == null || !USERNAME.matcher(name).matches()) return List.of();
        if (this.requested.add(name)) CompletableFuture.runAsync(() -> this.cosmetics.put(name, this.loadCosmetics(name)));
        return this.cosmetics.getOrDefault(name, List.of());
    }

    private List<Cosmetic> loadCosmetics(String name) {
        try {
            JsonObject config = Json.object(json("users/" + name + ".cfg"));
            JsonArray items = config == null ? null : config.getAsJsonArray("items");
            if (items == null) return List.of();
            List<Cosmetic> cosmetics = new ArrayList<>();
            for (int index = 0; index < items.size(); index++) {
                JsonObject item = Json.object(items.get(index));
                if (item == null || !Json.bool(item, "active", true)) continue;
                Cosmetic cosmetic = this.parseCosmetic(item, name, index);
                if (cosmetic != null) cosmetics.add(cosmetic);
            }
            return cosmetics;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Cosmetic parseCosmetic(JsonObject item, String player, int index) throws IOException {
        String type = Json.string(item, "type");
        if (type == null) return null;
        String modelPath = Json.string(item, "model");
        if (modelPath == null) modelPath = "items/" + type + "/model.cfg";
        JsonObject model = Json.object(json(modelPath));
        if (model == null || !"PlayerItem".equals(Json.string(model, "type"))) return null;
        int[] textureSize = Json.ints(model.get("textureSize"), 2);
        if (textureSize == null || !model.has("models")) return null;

        boolean playerTexture = Json.bool(model, "usePlayerTexture", false);
        BufferedImage image = null;
        if (!playerTexture) {
            String texturePath = Json.string(item, "texture");
            if (texturePath == null) texturePath = "items/" + type + "/users/" + player + ".png";
            image = ImageIO.read(new ByteArrayInputStream(bytes(texturePath)));
            if (image == null) return null;
        }

        String basePath = directory(modelPath);
        Model owner = new Model() {
        };
        owner.textureWidth = textureSize[0];
        owner.textureHeight = textureSize[1];
        Map<String, JsonObject> definitions = new HashMap<>();
        List<Part> parts = new ArrayList<>();
        for (JsonObject part : Json.objects(model, "models")) {
            String baseId = Json.string(part, "baseId");
            if (baseId != null) {
                JsonObject parent = definitions.get(baseId);
                if (parent == null) continue;
                PartParser.inherit(parent, part);
            }
            String id = Json.string(part, "id");
            if (id != null) definitions.putIfAbsent(id, part);
            if (!"ModelBox".equals(Json.string(part, "type"))) continue;
            parts.add(new Part(PartParser.parse(part, owner, textureSize, basePath), Json.string(part, "attachTo")));
        }
        return parts.isEmpty() ? null : new Cosmetic(parts, playerTexture, image, index);
    }

    private static String directory(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static JsonElement json(String path) throws IOException {
        return JsonParser.parseString(new String(bytes(path), StandardCharsets.UTF_8));
    }

    private static byte[] bytes(String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(SERVER + "/" + path).toURL()
                .openConnection(Minecraft.getInstance().getNetworkProxy());
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try {
            if (connection.getResponseCode() / 100 != 2) throw new IOException("HTTP " + connection.getResponseCode());
            try (InputStream input = connection.getInputStream()) {
                return input.readAllBytes();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static final class Cape implements HttpImageProcessor {
        private final Identifier location;
        private final String url;
        private boolean registered;
        private volatile boolean loaded;

        private Cape(String name) {
            this.location = new Identifier("cera", "capes/" + name.toLowerCase(Locale.ROOT));
            this.url = SERVER + "/capes/" + name + ".png";
        }

        private void register() {
            if (this.registered) return;
            Minecraft.getInstance().getTextureManager().register(this.location, new HttpTexture(null, this.url, null, this));
            this.registered = true;
        }

        @Override
        public BufferedImage process(BufferedImage image) {
            int width = 64;
            int height = 32;
            while (width < image.getWidth() || height < image.getHeight()) {
                width *= 2;
                height *= 2;
            }
            BufferedImage cape = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics graphics = cape.getGraphics();
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();
            return cape;
        }

        @Override
        public void onTextureDownloaded() {
            this.loaded = true;
        }
    }

    private static final class Cosmetic {
        private final List<Part> parts;
        private final boolean playerTexture;
        private final BufferedImage image;
        private final int index;
        private Identifier texture;

        private Cosmetic(List<Part> parts, boolean playerTexture, BufferedImage image, int index) {
            this.parts = parts;
            this.playerTexture = playerTexture;
            this.image = image;
            this.index = index;
        }

        private Identifier bind(ClientPlayerEntity player, TextureManager textures) {
            if (this.playerTexture) return player.getSkinTextureLocation();
            if (this.texture == null) this.texture = textures.register("cera_cosmetic_" + this.index, new DynamicTexture(this.image));
            return this.texture;
        }
    }

    private record Part(ModelPart model, String attachTo) {
    }

    public final class Layer implements EntityRenderLayer<ClientPlayerEntity> {
        private final PlayerRenderer renderer;
        public Layer(PlayerRenderer renderer) { this.renderer = renderer; }

        @Override
        public void render(ClientPlayerEntity player, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta, float bob, float yaw, float pitch, float scale) {
            if (player.isInvisible()) return;
            TextureManager textures = Minecraft.getInstance().getTextureManager();
            PlayerModel model = this.renderer.getModel();
            for (Cosmetic cosmetic : OptifineCosmetics.this.cosmetics(player)) {
                Identifier texture = cosmetic.bind(player, textures);
                textures.bind(texture);
                for (Part part : cosmetic.parts) {
                    GlStateManager.pushMatrix();
                    if (player.isSneaking()) GlStateManager.translatef(0, 0.2F, 0);
                    ModelPart attachTo = attachment(model, part.attachTo());
                    if (attachTo != null) attachTo.transform(scale);
                    part.model().render(scale);
                    GlStateManager.popMatrix();
                }
            }
        }

        private static ModelPart attachment(PlayerModel model, String name) {
            if (name == null || "body".equals(name)) return model.body;
            return switch (name) {
                case "head" -> model.head;
                case "leftArm" -> model.leftArm;
                case "rightArm" -> model.rightArm;
                case "leftLeg" -> model.leftLeg;
                case "rightLeg" -> model.rightLeg;
                case "cape" -> null;
                default -> model.body;
            };
        }

        @Override
        public boolean colorsWhenDamaged() {
            return false;
        }
    }
}
