package dev.rdh.cera.modules.cem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.rdh.argentum.impl.ext.ModelPartExtension;
import dev.rdh.cera.Cera;
import dev.rdh.cera.props.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.resource.Identifier;
import net.ornithemc.osl.resource.loader.api.resource.Resource;
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager;
import net.ornithemc.osl.resource.loader.api.resource.reload.ResourceReloadListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CustomEntityModels implements ResourceReloadListener {
    private static final String ROOT = "optifine/cem";

    private List<Loaded> loaded = List.of();
    private final List<Runnable> undo = new ArrayList<>();
    private volatile Set<Object> customized = Set.of();

    @Override
    public void resourcesReloaded(ResourceManager resources) {
        List<Loaded> models = new ArrayList<>();
        if (Cera.CONFIG.customEntityModels) {
            CemTargets.TARGETS.forEach((name, _) -> {
                Identifier location = new Identifier(ROOT + "/" + name + ".jem");
                if (resources.getResource(location).isEmpty()) location = new Identifier(ROOT + "/" + name + "/" + name + ".jem");
                Resource resource = resources.getResource(location).orElse(null);
                if (resource == null) return;
                try {
                    models.add(load(resources, name, location, resource));
                } catch (IOException | RuntimeException e) {
                    Cera.LOGGER.warn("[CustomEntityModels] Failed to load {}: {}", name, e.toString());
                }
            });
        }
        this.loaded = List.copyOf(models);
        Cera.LOGGER.info("[CustomEntityModels] Loaded {} models", models.size());
        this.install();
    }

    public void install() {
        Minecraft minecraft = Minecraft.getInstance();
        // block entity renderers read game state when they are created, so wait until init has finished
        if (minecraft.worldRenderer == null) return;
        this.undo.reversed().forEach(Runnable::run);
        this.undo.clear();
        Set<Object> renderers = Collections.newSetFromMap(new IdentityHashMap<>());
        CemTargets.Dispatchers dispatchers = new CemTargets.Dispatchers(minecraft.getEntityRenderDispatcher(),
                BlockEntityRenderDispatcher.INSTANCE);
        for (Loaded model : this.loaded) {
            try {
                CemTargets.Binding binding = CemTargets.TARGETS.get(model.name()).bind(dispatchers);
                if (binding != null && this.apply(model, binding)) renderers.add(binding.renderer());
            } catch (RuntimeException e) {
                Cera.LOGGER.warn("[CustomEntityModels] Failed to apply {}: {}", model.name(), e.toString());
            }
        }
        this.customized = Collections.unmodifiableSet(renderers);
    }

    public boolean customizes(Object renderer) {
        return this.customized.contains(renderer);
    }

    private boolean apply(Loaded model, CemTargets.Binding binding) {
        for (Attachment attachment : model.attachments()) {
            if (!binding.parts().containsKey(attachment.part())) {
                Cera.LOGGER.warn("[CustomEntityModels] {}: model part not found: {}", model.name(), attachment.part());
                return false;
            }
        }
        Set<ModelPart> named = Collections.newSetFromMap(new IdentityHashMap<>());
        named.addAll(binding.parts().values());
        Set<ModelPart> touched = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Attachment attachment : model.attachments()) {
            ModelPart parent = binding.parts().get(attachment.part());
            ModelPartExtension extension = (ModelPartExtension)parent;
            if (touched.add(parent)) {
                List<ModelPart> children = parent.children == null ? null : new ArrayList<>(parent.children);
                boolean boxesVisible = extension.argentum$areBoxesVisible();
                this.undo.add(() -> {
                    parent.children = children;
                    extension.argentum$setBoxesVisible(boxesVisible);
                });
            }
            if (!attachment.attach()) {
                // can't remove cause layers hold on to these parts and argentum caches their geometry
                extension.argentum$setBoxesVisible(false);
                if (parent.children != null) parent.children.removeIf(child -> !named.contains(child));
            }
            parent.addChild(attachment.model());
        }
        if (binding.renderer() instanceof EntityRenderer<?> renderer) {
            Identifier texture = renderer.cera$getCustomTexture();
            float shadowSize = renderer.shadowSize;
            this.undo.add(() -> {
                renderer.cera$setCustomTexture(texture);
                renderer.shadowSize = shadowSize;
            });
            if (model.texture() != null) renderer.cera$setCustomTexture(model.texture());
            if (model.shadowSize() >= 0.0F) renderer.shadowSize = model.shadowSize();
        }
        return true;
    }

    private static Loaded load(ResourceManager resources, String name, Identifier location, Resource resource) throws IOException {
        String path = location.getPath();
        String basePath = path.substring(0, path.lastIndexOf('/'));
        JsonObject json = json(resource);
        String texture = Json.string(json, "texture");
        int[] textureSize = Json.ints(json.get("textureSize"), 2);
        Model owner = new Model() {
        };
        if (textureSize != null) {
            owner.textureWidth = textureSize[0];
            owner.textureHeight = textureSize[1];
        }

        Map<String, JsonObject> byId = new HashMap<>();
        List<Attachment> attachments = new ArrayList<>();
        for (JsonObject element : Json.objects(json, "models")) {
            String baseId = Json.string(element, "baseId");
            if (baseId != null) {
                JsonObject base = byId.get(baseId);
                if (base == null) Cera.LOGGER.warn("[CustomEntityModels] {}: base id not found: {}", name, baseId);
                else PartParser.inherit(base, element);
            }
            String modelPath = Json.string(element, "model");
            if (modelPath != null) {
                Identifier modelLocation = PartParser.resource(basePath, modelPath, ".jpm");
                Resource external = resources.getResource(modelLocation).orElse(null);
                if (external == null) Cera.LOGGER.warn("[CustomEntityModels] {}: model not found: {}", name, modelLocation);
                else PartParser.inherit(json(external), element);
            }
            String id = Json.string(element, "id");
            if (id != null && !id.isEmpty()) byId.putIfAbsent(id, element);

            String part = Json.string(element, "part");
            if (part == null) throw new JsonParseException("Model part not specified");
            attachments.add(new Attachment(part, Json.bool(element, "attach", false),
                    PartParser.parse(element, owner, textureSize, basePath)));
        }
        if (!json.has("models")) throw new JsonParseException("Missing models");
        return new Loaded(name, texture == null ? null : PartParser.resource(basePath, texture, ".png"),
                Json.number(json, "shadowSize", -1.0F), List.copyOf(attachments));
    }

    private static JsonObject json(Resource resource) throws IOException {
        try (resource; InputStream stream = resource.open()) {
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (!json.isJsonObject()) throw new JsonParseException("Not a json object");
            return json.getAsJsonObject();
        }
    }

    private record Loaded(String name, Identifier texture, float shadowSize, List<Attachment> attachments) {
    }

    private record Attachment(String part, boolean attach, ModelPart model) {
    }
}
