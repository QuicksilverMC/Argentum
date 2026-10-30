package dev.rdh.cera.modules.cem;

import dev.rdh.argentum.impl.ext.ModelPartExtension;
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

    private final AnimationContext context;
    private final Runnable[] assignments;
    private final ModelPart[] parts;
    private final Field[] fields;
    private final float[] rest;
    private final float[] values;
    private final boolean usesVars;
    boolean dirty;
    private long sequence = -1;
    private ModelPart first;

    private Animation(Builder builder) {
        this.context = builder.context;
        this.assignments = builder.assignments.toArray(new Runnable[0]);
        this.parts = builder.parts.toArray(new ModelPart[0]);
        this.fields = builder.fields.toArray(new Field[0]);
        this.rest = new float[this.parts.length];
        this.values = new float[this.parts.length];
        for (int i = 0; i < this.parts.length; i++) this.rest[i] = this.fields[i].get(this.parts[i]);
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
            for (int i = 0; i < this.parts.length; i++) this.values[i] = this.fields[i].get(this.parts[i]);
        } else if (part == this.first) {
            for (int i = 0; i < this.parts.length; i++) this.fields[i].set(this.parts[i], this.values[i]);
        } else {
            return;
        }
        this.context.markDirty(this);
    }

    void restore() {
        for (int i = 0; i < this.parts.length; i++) this.fields[i].set(this.parts[i], this.rest[i]);
        this.dirty = false;
    }

    static final class Builder {
        private final AnimationContext context;
        private final List<Runnable> assignments = new ArrayList<>();
        private final List<ModelPart> parts = new ArrayList<>();
        private final List<Field> fields = new ArrayList<>();
        private boolean usesVars;

        Builder(AnimationContext context) {
            this.context = context;
        }

        boolean isEmpty() {
            return this.assignments.isEmpty();
        }

        void add(String target, String expression, Function<String, ModelPart> models) {
            Object value = new ExpressionParser(name -> this.variable(name, models), this.context).parse(expression);
            AnimationContext context = this.context;
            if (target.startsWith("var.") || target.startsWith("varb.")) {
                int slot = context.varSlot(target);
                this.usesVars = true;
                if (target.startsWith("varb.")) {
                    ExpressionParser.BoolExpr bool = ExpressionParser.asBool(value, target);
                    this.assignments.add(() -> context.vars[slot] = bool.eval() ? 1.0F : 0.0F);
                } else {
                    ExpressionParser.FloatExpr number = ExpressionParser.asFloat(value, target);
                    this.assignments.add(() -> context.vars[slot] = number.eval());
                }
                return;
            }
            int dot = target.lastIndexOf('.');
            ModelPart part = dot < 0 ? null : models.apply(target.substring(0, dot));
            Field field = dot < 0 ? null : Field.of(target.substring(dot + 1));
            if (part == null || field == null) throw new IllegalArgumentException("Unknown model variable: " + target);
            if (!this.targets(part, field)) {
                this.parts.add(part);
                this.fields.add(field);
            }
            if (field.bool()) {
                ExpressionParser.BoolExpr bool = ExpressionParser.asBool(value, target);
                this.assignments.add(() -> field.set(part, bool.eval() ? 1.0F : 0.0F));
            } else {
                ExpressionParser.FloatExpr number = ExpressionParser.asFloat(value, target);
                this.assignments.add(() -> field.set(part, number.eval()));
            }
        }

        Animation build() {
            return new Animation(this);
        }

        private boolean targets(ModelPart part, Field field) {
            for (int i = 0; i < this.parts.size(); i++) {
                if (this.parts.get(i) == part && this.fields.get(i) == field) return true;
            }
            return false;
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
            int dot = name.lastIndexOf('.');
            if (dot < 0) return context.parameter(name);
            ModelPart part = models.apply(name.substring(0, dot));
            Field field = Field.of(name.substring(dot + 1));
            if (part == null || field == null) return null;
            return field.bool()
                    ? (ExpressionParser.BoolExpr)() -> field.get(part) != 0.0F
                    : (ExpressionParser.FloatExpr)() -> field.get(part);
        }
    }
}
