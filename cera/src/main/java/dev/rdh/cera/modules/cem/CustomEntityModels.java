package dev.rdh.cera.modules.cem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.rdh.argentum.impl.ext.ModelPartExtension;
import dev.rdh.cera.Cera;
import dev.rdh.cera.modules.random.RandomChoices;
import dev.rdh.cera.modules.random.RandomConditions;
import dev.rdh.cera.modules.random.Subject;
import dev.rdh.cera.props.Json;
import dev.rdh.cera.props.Props;
import dev.rdh.cera.props.RandomRules;
import dev.rdh.cera.props.Result;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class CustomEntityModels implements ResourceReloadListener {
    private static final String ROOT = "optifine/cem";

    private Map<String, Group> loaded = Map.of();
    private final List<Runnable> undo = new ArrayList<>();
    private final Map<Object, List<Slot>> dynamic = new IdentityHashMap<>();
    private final Map<Object, List<Slot>> slots = new IdentityHashMap<>();
    private final List<ModelPart> hidden = new ArrayList<>();
    private final List<Slot> selected = new ArrayList<>();
    private final IntArrayList renders = new IntArrayList();
    private final RandomChoices choices = new RandomChoices();
    private volatile Map<Object, List<Slot>> published = Map.of();
    private final AnimationContext animations = new AnimationContext();

    public AnimationContext animations() {
        return this.animations;
    }

    @Override
    public void resourcesReloaded(ResourceManager resources) {
        Map<String, Group> groups = new HashMap<>();
        if (Cera.CONFIG.customEntityModels) {
            Set<String> names = new LinkedHashSet<>(CemTargets.TARGETS.keySet());
            CemTargets.VARIANTS.values().forEach(variants -> variants.forEach(variant -> names.add(variant.name())));
            for (String name : names) {
                try {
                    Group group = group(resources, name);
                    if (group != null) groups.put(name, group);
                } catch (IOException | RuntimeException e) {
                    Cera.LOGGER.warn("[CustomEntityModels] Failed to load {}: {}", name, e.toString());
                }
            }
        }
        this.loaded = Map.copyOf(groups);
        this.choices.clear();
        Cera.LOGGER.info("[CustomEntityModels] Loaded {} models", groups.size());
        this.install();
    }

    public void install() {
        Minecraft minecraft = Minecraft.getInstance();
        // block entity renderers read game state when they are created, so wait until init has finished
        if (minecraft.worldRenderer == null) return;
        this.undo.reversed().forEach(Runnable::run);
        this.undo.clear();
        this.dynamic.clear();
        this.slots.clear();
        this.selected.clear();
        this.renders.clear();
        CemTargets.Dispatchers dispatchers = new CemTargets.Dispatchers(minecraft.getEntityRenderDispatcher(),
                BlockEntityRenderDispatcher.INSTANCE);
        CemTargets.TARGETS.forEach((name, target) -> {
            List<CemTargets.Variant> variants = new ArrayList<>(CemTargets.VARIANTS.getOrDefault(name, List.of()));
            variants.add(new CemTargets.Variant(name, target.when()));
            variants.removeIf(variant -> !this.loaded.containsKey(variant.name()));
            if (variants.isEmpty()) return;
            CemTargets.Binding binding;
            try {
                binding = target.bind(dispatchers);
            } catch (RuntimeException e) {
                Cera.LOGGER.warn("[CustomEntityModels] Failed to bind {}: {}", name, e.toString());
                return;
            }
            if (binding == null) return;

            State vanilla = State.capture(binding, null);
            List<Candidate> candidates = new ArrayList<>();
            for (CemTargets.Variant variant : variants) {
                Group group = this.loaded.get(variant.name());
                State[] states = new State[group.models().size()];
                for (int i = 0; i < states.length; i++) {
                    if (group.models().get(i) != null) states[i] = this.build(group.models().get(i), binding);
                }
                if (states[0] != null) candidates.add(new Candidate(variant.when(), states, group.rules()));
            }
            if (candidates.isEmpty()) return;

            Slot slot = new Slot(binding, vanilla, List.copyOf(candidates));
            slot.apply(slot.fallback());
            this.undo.add(() -> vanilla.apply(binding));
            this.slots.computeIfAbsent(binding.renderer(), _ -> new ArrayList<>()).add(slot);
            if (!slot.isStatic()) this.dynamic.computeIfAbsent(binding.renderer(), _ -> new ArrayList<>()).add(slot);
        });
        Map<Object, List<Slot>> published = new IdentityHashMap<>();
        this.slots.forEach((renderer, slots) -> published.put(renderer, List.copyOf(slots)));
        this.published = Collections.unmodifiableMap(published);
    }

    // called from chunk build threads, so it only reads what install published and the block from the chunk
    public boolean customizes(Object renderer, BlockEntity blockEntity, Block block) {
        List<Slot> slots = this.published.get(renderer);
        if (slots == null) return false;
        CemTargets.Placed placed = new CemTargets.Placed(blockEntity, block);
        for (Slot slot : slots) {
            for (Candidate candidate : slot.candidates()) {
                if (candidate.when().test(placed)) return true;
            }
        }
        return false;
    }

    public void beginEntity(Entity entity, float tickDelta) {
        this.animations.beginEntity(entity, tickDelta);
        this.renders.add(this.selected.size());
        if (!this.dynamic.isEmpty()) this.select(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity), entity);
    }

    public void beginBlockEntity(BlockEntity blockEntity, float tickDelta) {
        this.animations.beginBlockEntity(tickDelta);
        this.renders.add(this.selected.size());
        // block entities drawn as items have no world to choose a variant from, so they keep the fallback
        if (!this.dynamic.isEmpty() && blockEntity.hasWorld()) {
            this.select(BlockEntityRenderDispatcher.INSTANCE.getRenderer(blockEntity), new CemTargets.Placed(blockEntity, blockEntity.getBlock()));
        }
    }

    public void endRender() {
        this.animations.endRender();
        if (this.renders.isEmpty()) return;
        int start = this.renders.popInt();
        for (int i = this.selected.size() - 1; i >= start; i--) {
            Slot slot = this.selected.remove(i);
            slot.apply(slot.fallback());
        }
    }

    private void select(Object renderer, Object rendered) {
        List<Slot> slots = this.dynamic.get(renderer);
        if (slots == null) return;
        Subject subject = null;
        for (Slot slot : slots) {
            State state = null;
            for (Candidate candidate : slot.candidates()) {
                if (!candidate.when().test(rendered)) continue;
                int index = 0;
                if (candidate.states().length > 1 || candidate.rules() != null) {
                    if (subject == null) subject = Subject.current();
                    if (subject != null) {
                        RandomChoices.Choice choice = this.choices.choose(subject, candidate, candidate.rules(), candidate.states().length);
                        index = choice.variant() - 1;
                        this.animations.setRuleIndex(choice.rule());
                    }
                }
                state = index >= 0 && index < candidate.states().length && candidate.states()[index] != null
                        ? candidate.states()[index] : candidate.states()[0];
                break;
            }
            if (state == null) continue;
            slot.apply(state);
            this.selected.add(slot);
        }
    }

    // eyes layers draw the model again with their own texture, which a part with its own texture would ignore
    public void hideTexturedParts(Object renderer) {
        List<Slot> slots = this.slots.get(renderer);
        if (slots == null) return;
        for (Slot slot : slots) {
            for (ModelPart part : slot.current.textured()) {
                if (part.visible) {
                    part.visible = false;
                    this.hidden.add(part);
                }
            }
        }
    }

    public void showTexturedParts() {
        for (ModelPart part : this.hidden) part.visible = true;
        this.hidden.clear();
    }

    private State build(Loaded model, CemTargets.Binding binding) {
        List<Runnable> undo = new ArrayList<>();
        try {
            if (!apply(model, binding, undo)) return null;
            return State.capture(binding, this.animate(model, binding, undo));
        } catch (RuntimeException e) {
            Cera.LOGGER.warn("[CustomEntityModels] Failed to apply {}: {}", model.name(), e.toString());
            return null;
        } finally {
            undo.reversed().forEach(Runnable::run);
        }
    }

    private static boolean apply(Loaded model, CemTargets.Binding binding, List<Runnable> undo) {
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
                undo.add(() -> {
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
        if (binding.main() && binding.renderer() instanceof EntityRenderer<?> renderer) {
            Identifier texture = renderer.cera$getCustomTexture();
            float shadowSize = renderer.shadowSize;
            undo.add(() -> {
                renderer.cera$setCustomTexture(texture);
                renderer.shadowSize = shadowSize;
            });
            if (model.texture() != null) renderer.cera$setCustomTexture(model.texture());
            if (model.shadowSize() >= 0.0F) renderer.shadowSize = model.shadowSize();
        }
        return true;
    }

    private Animation animate(Loaded model, CemTargets.Binding binding, List<Runnable> undo) {
        Animation.Builder builder = new Animation.Builder(this.animations);
        List<ModelPart> roots = model.attachments().stream().map(Attachment::model).toList();
        for (Attachment attachment : model.attachments()) {
            ModelPart parent = binding.parts().get(attachment.part());
            Function<String, ModelPart> models = name -> find(name, attachment.model(), parent, binding.parts(), roots);
            for (Map.Entry<String, String> animation : attachment.animations()) {
                try {
                    builder.add(animation.getKey(), animation.getValue(), models);
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException(animation.getKey() + " = " + animation.getValue() + ": " + e.getMessage(), e);
                }
            }
        }
        if (builder.isEmpty()) return null;
        Animation animation = builder.build();
        undo.add(animation::restore);
        return animation;
    }

    private static ModelPart find(String name, ModelPart self, ModelPart parent, Map<String, ModelPart> parts, List<ModelPart> roots) {
        if (name.indexOf(':') >= 0) {
            String[] path = name.split(":");
            ModelPart part = find(path[0], self, parent, parts, roots);
            for (int i = 1; i < path.length && part != null; i++) part = child(part, path[i]);
            return part;
        }
        if (name.equals("this")) return self;
        if (name.equals("part")) return parent;
        ModelPart part = parts.get(name);
        if (part != null) return part;
        for (ModelPart root : roots) {
            if (name.equals(root.id)) return root;
            ModelPart child = child(root, name);
            if (child != null) return child;
        }
        return null;
    }

    private static ModelPart child(ModelPart part, String id) {
        if (part.children == null) return null;
        for (ModelPart child : part.children) {
            if (id.equals(child.id)) return child;
        }
        for (ModelPart child : part.children) {
            ModelPart found = child(child, id);
            if (found != null) return found;
        }
        return null;
    }

    private static Group group(ResourceManager resources, String name) throws IOException {
        Identifier location = new Identifier(ROOT + "/" + name + ".jem");
        if (resources.getResource(location).isEmpty()) location = new Identifier(ROOT + "/" + name + "/" + name + ".jem");
        Resource resource = resources.getResource(location).orElse(null);
        if (resource == null) return null;
        String path = location.getPath();
        String directory = path.substring(0, path.lastIndexOf('/') + 1);

        List<Loaded> models = new ArrayList<>();
        models.add(load(resources, name, location, resource));
        for (int index = 2; ; index++) {
            Identifier alternative = new Identifier(directory + name + index + ".jem");
            Resource alternativeResource = resources.getResource(alternative).orElse(null);
            if (alternativeResource == null) break;
            try {
                models.add(load(resources, name + index, alternative, alternativeResource));
            } catch (IOException | RuntimeException e) {
                Cera.LOGGER.warn("[CustomEntityModels] Failed to load {}{}: {}", name, index, e.toString());
                models.add(null);
            }
        }

        RandomRules<Subject> rules = null;
        Resource properties = resources.getResource(new Identifier(directory + name + ".properties")).orElse(null);
        if (properties != null) {
            Props props = new Props(properties);
            Result<RandomRules<Subject>> parsed = RandomRules.parse(props, "models", RandomConditions.reader(props));
            if (!parsed.isSuccess()) Cera.LOGGER.warn("[CustomEntityModels] {}: {}", props.id(), parsed.error());
            else if (!parsed.value().isEmpty()) rules = parsed.value();
        }
        return new Group(Collections.unmodifiableList(models), rules);
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
            List<Map.Entry<String, String>> animations = new ArrayList<>();
            for (JsonObject block : Json.objects(element, "animations")) {
                for (Map.Entry<String, JsonElement> entry : block.entrySet()) {
                    animations.add(Map.entry(entry.getKey(), entry.getValue().getAsString()));
                }
            }
            attachments.add(new Attachment(part, Json.bool(element, "attach", false),
                    PartParser.parse(element, owner, textureSize, basePath), List.copyOf(animations)));
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

    private record Group(List<Loaded> models, RandomRules<Subject> rules) {
    }

    private record Loaded(String name, Identifier texture, float shadowSize, List<Attachment> attachments) {
    }

    private record Attachment(String part, boolean attach, ModelPart model, List<Map.Entry<String, String>> animations) {
    }

    private record Candidate(Predicate<Object> when, State[] states, RandomRules<Subject> rules) {
    }

    private static final class Slot {
        private final CemTargets.Binding binding;
        private final State vanilla;
        private final List<Candidate> candidates;
        private State current;

        private Slot(CemTargets.Binding binding, State vanilla, List<Candidate> candidates) {
            this.binding = binding;
            this.vanilla = vanilla;
            this.candidates = candidates;
            this.current = vanilla;
        }

        CemTargets.Binding binding() {
            return this.binding;
        }

        State vanilla() {
            return this.vanilla;
        }

        List<Candidate> candidates() {
            return this.candidates;
        }

        void apply(State state) {
            state.apply(this.binding);
            this.current = state;
        }

        State fallback() {
            for (Candidate candidate : this.candidates) {
                if (candidate.when() == CemTargets.ALWAYS) return candidate.states()[0];
            }
            return this.vanilla;
        }

        boolean isStatic() {
            if (this.candidates.size() != 1) return false;
            Candidate candidate = this.candidates.getFirst();
            return candidate.when() == CemTargets.ALWAYS && candidate.states().length == 1 && candidate.rules() == null;
        }
    }

    private record State(ModelPart[] parts, List<ModelPart>[] children, boolean[] boxesVisible, Animation animation,
                         Identifier texture, float shadowSize, List<ModelPart> textured) {
        @SuppressWarnings("unchecked")
        static State capture(CemTargets.Binding binding, Animation animation) {
            ModelPart[] parts = binding.parts().values().toArray(new ModelPart[0]);
            List<ModelPart>[] children = new List[parts.length];
            boolean[] boxesVisible = new boolean[parts.length];
            for (int i = 0; i < parts.length; i++) {
                children[i] = parts[i].children == null ? null : new ArrayList<>(parts[i].children);
                boxesVisible[i] = ((ModelPartExtension)parts[i]).argentum$areBoxesVisible();
            }
            EntityRenderer<?> renderer = binding.main() && binding.renderer() instanceof EntityRenderer<?> entityRenderer ? entityRenderer : null;
            Set<ModelPart> named = Collections.newSetFromMap(new IdentityHashMap<>());
            named.addAll(binding.parts().values());
            List<ModelPart> textured = new ArrayList<>();
            for (ModelPart part : parts) textured(part, named, textured);
            return new State(parts, children, boxesVisible, animation,
                    renderer == null ? null : renderer.cera$getCustomTexture(), renderer == null ? 0.0F : renderer.shadowSize,
                    List.copyOf(textured));
        }

        private static void textured(ModelPart part, Set<ModelPart> named, List<ModelPart> textured) {
            if (part.children == null) return;
            for (ModelPart child : part.children) {
                if (named.contains(child)) continue;
                if (((ModelPartExtension)child).argentum$getTexture() != null) textured.add(child);
                else textured(child, named, textured);
            }
        }

        void apply(CemTargets.Binding binding) {
            for (int i = 0; i < this.parts.length; i++) {
                this.parts[i].children = this.children[i];
                ModelPartExtension extension = (ModelPartExtension)this.parts[i];
                extension.argentum$setBoxesVisible(this.boxesVisible[i]);
                extension.argentum$setRenderListener(this.animation);
            }
            if (binding.main() && binding.renderer() instanceof EntityRenderer<?> renderer) {
                renderer.cera$setCustomTexture(this.texture);
                renderer.shadowSize = this.shadowSize;
            }
        }
    }
}
