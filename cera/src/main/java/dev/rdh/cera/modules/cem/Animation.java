package dev.rdh.cera.modules.cem;

import dev.rdh.argentum.impl.ext.ModelPartExtension;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.model.ModelPart;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

final class Animation implements Consumer<ModelPart> {
    enum Field {
        TX, TY, TZ, RX, RY, RZ, OX, OY, OZ, SX, SY, SZ, VISIBLE, VISIBLE_BOXES;

        static Field of(String name) {
            return switch (name) {
                case "tx" -> TX;
                case "ty" -> TY;
                case "tz" -> TZ;
                case "rx" -> RX;
                case "ry" -> RY;
                case "rz" -> RZ;
                case "ox" -> OX;
                case "oy" -> OY;
                case "oz" -> OZ;
                case "sx" -> SX;
                case "sy" -> SY;
                case "sz" -> SZ;
                case "visible" -> VISIBLE;
                case "visible_boxes" -> VISIBLE_BOXES;
                default -> null;
            };
        }

        boolean bool() {
            return this == VISIBLE || this == VISIBLE_BOXES;
        }

        float get(ModelPart part) {
            return switch (this) {
                case TX -> part.x;
                case TY -> part.y;
                case TZ -> part.z;
                case RX -> part.rotationX;
                case RY -> part.rotationY;
                case RZ -> part.rotationZ;
                case OX -> part.translateX;
                case OY -> part.translateY;
                case OZ -> part.translateZ;
                case SX -> ((ModelPartExtension)part).argentum$getScale().x;
                case SY -> ((ModelPartExtension)part).argentum$getScale().y;
                case SZ -> ((ModelPartExtension)part).argentum$getScale().z;
                case VISIBLE -> part.visible ? 1.0F : 0.0F;
                case VISIBLE_BOXES -> ((ModelPartExtension)part).argentum$areBoxesVisible() ? 1.0F : 0.0F;
            };
        }

        void set(ModelPart part, float value) {
            switch (this) {
                case TX -> part.x = value;
                case TY -> part.y = value;
                case TZ -> part.z = value;
                case RX -> part.rotationX = value;
                case RY -> part.rotationY = value;
                case RZ -> part.rotationZ = value;
                case OX -> part.translateX = value;
                case OY -> part.translateY = value;
                case OZ -> part.translateZ = value;
                case SX -> ((ModelPartExtension)part).argentum$getScale().x = value;
                case SY -> ((ModelPartExtension)part).argentum$getScale().y = value;
                case SZ -> ((ModelPartExtension)part).argentum$getScale().z = value;
                case VISIBLE -> part.visible = value != 0.0F;
                case VISIBLE_BOXES -> ((ModelPartExtension)part).argentum$setBoxesVisible(value != 0.0F);
            }
        }
    }

    interface Setter {
        void set(float value);
    }

    private record Target(Object key, ExpressionParser.FloatExpr get, Setter set) {
    }

    private record PartField(ModelPart part, Field field) {
    }

    private final AnimationContext context;
    private final Runnable[] assignments;
    private final Target[] targets;
    private final float[] rest;
    private final float[] values;
    private final boolean usesVars;
    boolean dirty;
    private long sequence = -1;
    private ModelPart first;

    private Animation(Builder builder) {
        this.context = builder.context;
        this.assignments = builder.assignments.toArray(new Runnable[0]);
        this.targets = builder.targets.toArray(new Target[0]);
        this.rest = new float[this.targets.length];
        this.values = new float[this.targets.length];
        for (int i = 0; i < this.targets.length; i++) this.rest[i] = this.targets[i].get().eval();
        this.usesVars = builder.usesVars;
    }

    // vanilla setupAnimation runs again before every model render pass (layers + the emissive pass), 
    // so evaluate once per render and put the results back each time the first part of a pass renders
    @Override
    public void accept(ModelPart part) {
        if (this.sequence != this.context.sequence) {
            this.sequence = this.context.sequence;
            this.first = part;
            if (this.usesVars) this.context.loadVars();
            for (Runnable assignment : this.assignments) assignment.run();
            for (int i = 0; i < this.targets.length; i++) this.values[i] = this.targets[i].get().eval();
        } else if (part == this.first) {
            for (int i = 0; i < this.targets.length; i++) this.targets[i].set().set(this.values[i]);
        } else {
            return;
        }
        this.context.markDirty(this);
    }

    void restore() {
        for (int i = 0; i < this.targets.length; i++) this.targets[i].set().set(this.rest[i]);
        this.dirty = false;
    }

    static final class Builder {
        private final AnimationContext context;
        private final EntityRenderer<?> renderer;
        private final List<Runnable> assignments = new ArrayList<>();
        private final List<Target> targets = new ArrayList<>();
        private boolean usesVars;

        Builder(AnimationContext context, EntityRenderer<?> renderer) {
            this.context = context;
            this.renderer = renderer;
        }

        boolean isEmpty() {
            return this.assignments.isEmpty();
        }

        void add(String name, String expression, Function<String, ModelPart> models) {
            Object value = new ExpressionParser(variable -> this.variable(variable, models), this.context).parse(expression);
            AnimationContext context = this.context;
            if (name.startsWith("var.") || name.startsWith("varb.")) {
                int slot = context.varSlot(name);
                this.usesVars = true;
                if (name.startsWith("varb.")) {
                    ExpressionParser.BoolExpr bool = ExpressionParser.asBool(value, name);
                    this.assignments.add(() -> context.vars[slot] = bool.eval() ? 1.0F : 0.0F);
                } else {
                    ExpressionParser.FloatExpr number = ExpressionParser.asFloat(value, name);
                    this.assignments.add(() -> context.vars[slot] = number.eval());
                }
                return;
            }
            Target target = this.target(name, models);
            if (target == null) throw new IllegalArgumentException("Unknown model variable: " + name);
            if (this.targets.stream().noneMatch(existing -> existing.key().equals(target.key()))) this.targets.add(target);
            if (isBool(name)) {
                ExpressionParser.BoolExpr bool = ExpressionParser.asBool(value, name);
                this.assignments.add(() -> target.set().set(bool.eval() ? 1.0F : 0.0F));
            } else {
                ExpressionParser.FloatExpr number = ExpressionParser.asFloat(value, name);
                this.assignments.add(() -> target.set().set(number.eval()));
            }
        }

        Animation build() {
            return new Animation(this);
        }

        private static boolean isBool(String name) {
            Field field = Field.of(name.substring(name.lastIndexOf('.') + 1));
            return !name.startsWith("render.") && field != null && field.bool();
        }

        private Target target(String name, Function<String, ModelPart> models) {
            AnimationContext context = this.context;
            EntityRenderer<?> renderer = this.renderer;
            if (name.startsWith("render.")) {
                return switch (name) {
                    case "render.shadow_size" -> renderer == null ? null
                            : new Target(name, () -> renderer.shadowSize, value -> renderer.shadowSize = value);
                    case "render.shadow_opacity" -> renderer == null ? null
                            : new Target(name, () -> renderer.shadowDarkness, value -> renderer.shadowDarkness = value);
                    case "render.shadow_offset_x" -> new Target(name, () -> context.shadowOffsetX, value -> context.shadowOffsetX = value);
                    case "render.shadow_offset_z" -> new Target(name, () -> context.shadowOffsetZ, value -> context.shadowOffsetZ = value);
                    case "render.leash_offset_x" -> new Target(name, () -> context.leashOffsetX, value -> context.leashOffsetX = value);
                    case "render.leash_offset_y" -> new Target(name, () -> context.leashOffsetY, value -> context.leashOffsetY = value);
                    case "render.leash_offset_z" -> new Target(name, () -> context.leashOffsetZ, value -> context.leashOffsetZ = value);
                    default -> null;
                };
            }
            int dot = name.lastIndexOf('.');
            ModelPart part = dot < 0 ? null : models.apply(name.substring(0, dot));
            Field field = dot < 0 ? null : Field.of(name.substring(dot + 1));
            if (part == null || field == null) return null;
            return new Target(new PartField(part, field), () -> field.get(part), value -> field.set(part, value));
        }

        private Object variable(String name, Function<String, ModelPart> models) {
            AnimationContext context = this.context;
            if (name.startsWith("var.") || name.startsWith("varb.")) {
                int slot = context.varSlot(name);
                this.usesVars = true;
                return name.startsWith("varb.")
                        ? (ExpressionParser.BoolExpr)() -> context.vars[slot] != 0.0F
                        : (ExpressionParser.FloatExpr)() -> context.vars[slot];
            }
            if (name.indexOf('.') < 0) return context.parameter(name);
            Target target = this.target(name, models);
            if (target == null) return null;
            ExpressionParser.FloatExpr get = target.get();
            return isBool(name) ? (ExpressionParser.BoolExpr)() -> get.eval() != 0.0F : get;
        }
    }
}
